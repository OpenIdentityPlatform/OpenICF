/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.impl.api.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.ReflectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.ConnectorMessagesImpl;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;

public class LocalConnectorInfoManagerImpl implements ConnectorInfoManager {

    private static final Log LOG = 
        Log.getLog(LocalConnectorInfoManagerImpl.class);
    
    private List<ConnectorInfo> _connectorInfo;
    
    public LocalConnectorInfoManagerImpl(URL [] bundleURLs) 
        throws ConfigurationException {
        
        List<WorkingBundleInfo> workingInfo =
        expandBundles(bundleURLs);
        
        WorkingBundleInfo.resolve(workingInfo);
        
        _connectorInfo = createConnectorInfo(workingInfo);
    }
    
    /**
     * First pass - expand bundles as needed. populates
     * originalURL, parsedManifest, libContents, and topLevelContents
     */
    private List<WorkingBundleInfo> expandBundles (URL [] bundleURLs)
        throws ConfigurationException {
        List<WorkingBundleInfo> rv = new ArrayList<WorkingBundleInfo>();
        for (URL url : bundleURLs) {
            WorkingBundleInfo info;
            
            File file = new File(url.getFile());
            if ( "file".equals(url.getProtocol()) && file.isDirectory() ) {
                info = processDirectory(file);
            }
            else {
                info = processURL(url,true);                
            }
            
            rv.add(info);
        }
        return rv;
    }
    
    private WorkingBundleInfo processDirectory(File dir) 
        throws ConfigurationException {
        WorkingBundleInfo info
            = new WorkingBundleInfo(dir.getAbsolutePath());
        try {
            //easy case - nothing needs to be copied
            File manifest = new File(dir,"META-INF/MANIFEST.MF");
            InputStream in = null;
            try {
                in = new FileInputStream(manifest);
                Manifest rawManifest = new Manifest(in);
                ConnectorBundleManifestParser parser
                = new ConnectorBundleManifestParser(info.getOriginalLocation(),
                        rawManifest);
                info.setManifest(parser.parse());
                
            }
            finally {
                IOUtil.quietClose(in);
            }
            info.getImmediateClassPath().add(dir.toURL());
            List<String> bundleContents = listBundleContents(dir);
            info.getImmediateBundleContents().addAll(bundleContents);
            List<URL> libURLs = BundleLibSorter.getSortedURLs(dir);
            for (URL lib : libURLs) {
                WorkingBundleInfo embedded = processURL(lib,false);
                info.getEmbeddedBundles().add(embedded);
            }
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
        return info;
    }

    /**
     * Lists the contents of a directory and its contents.
     * Result will be given as a list of forward-slash separated
     * relative paths
     */
    private static List<String> listBundleContents(File dir) {
        List<String> rv = new ArrayList<String>();
        for (File file : dir.listFiles()) {
            listBundleContents2("",file,rv);
        }
        return rv;
    }
    
    private static void listBundleContents2(String prefix, File file,
            List<String> result) {
        result.add(prefix+file.getName());
        if ( file.isDirectory() ) {
            for (File sub : file.listFiles()) {
                listBundleContents2(prefix+"/",sub,result);
            }
        }
    }
    
    private WorkingBundleInfo processURL(URL url, boolean topLevel) 
        throws ConfigurationException {
        WorkingBundleInfo info
        = new WorkingBundleInfo(url.toString());


        try {
            JarInputStream stream = null;
            if ( url.getProtocol().equals("file") ) {
                info.getImmediateClassPath().add(url);
            }
            else {
                //if we're in a WAR, this might not be the kind of URL
                //that URLClassLoader can handle, so copy it as well
                InputStream stream2 = null;
                try {
                    stream2 = url.openStream();
                    info.getImmediateClassPath().add(copyStreamToTempFile(stream2));
                }
                finally {
                    IOUtil.quietClose(stream2);
                }            
            }
            TreeMap<String,URL> libURLs = new TreeMap<String,URL>();
            try {
                stream = new JarInputStream(url.openStream());
                //only parse the manifest for top-level bundles
                //other bundles may not be bundles - they might be
                //jars instead
                if ( topLevel ) {
                    Manifest rawManifest = stream.getManifest();
                    ConnectorBundleManifestParser parser
                        = new ConnectorBundleManifestParser(info.getOriginalLocation(),
                            rawManifest);
                    info.setManifest(parser.parse());
                }
                
                JarEntry entry = null;
                while ( ( entry = stream.getNextJarEntry()) != null ) {
                    String name = entry.getName();
                    info.getImmediateBundleContents().add(name);
                    if ( name.startsWith("lib/") ) {
                        String localName = name.substring("lib/".length());
                        URL tempurl = copyStreamToTempFile(stream);
                        libURLs.put(localName, tempurl);
                    }
                }
                
            }
            finally {
                IOUtil.quietClose(stream);
            }
            for (URL lib : libURLs.values()) {
                WorkingBundleInfo embedded = processURL(lib,false);
                info.getEmbeddedBundles().add(embedded);
            }
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
        return info;
    }
    
    private URL copyStreamToTempFile(InputStream in) 
        throws IOException {
        File tempFile = File.createTempFile("bundle-temp", "tmp");
        tempFile.deleteOnExit();
        FileOutputStream out = new FileOutputStream(tempFile);
        try {
            IOUtil.copyFile(in, out);
        }
        finally {
            out.close();
        }
        return tempFile.toURL();
    }
    

    
    /**
     * Final pass - create connector infos
     */
    private List<ConnectorInfo> createConnectorInfo(
            Collection<WorkingBundleInfo> parsed) 
        throws ConfigurationException {
        List<ConnectorInfo> rv = new ArrayList<ConnectorInfo>();
        for (WorkingBundleInfo bundleInfo : parsed ) {
            ClassLoader loader = 
                new BundleClassLoader(bundleInfo.getResolvedClassPath());
            for (String name : bundleInfo.getImmediateBundleContents()) {
                Class<?> connectorClass = null;
                ConnectorClass options = null;
                if ( name.endsWith(".class") ) {
                    String className = name.substring(0,
                            name.length()-".class".length());
                    className = className.replace('/', '.');
                    try {
                        connectorClass =
                            loader.loadClass(className);
                        options =
                            connectorClass.getAnnotation(ConnectorClass.class);
                    }
                    catch (Throwable e) {
                        //probe for the class. this might not
                        //be an error since it might be from a bundle
                        //fragment ( a bundle only included by other
                        //bundles ). However, we should definitely
                        //warn
                        LOG.warn(e, 
                                "Unable to load class {0} from bundle {1}. Class will be ignored and will not be listed in list of connectors.",
                                className,
                                bundleInfo.getOriginalLocation());
                    }
                }
                if ( connectorClass != null && options != null ) {
                    if (!Connector.class.isAssignableFrom(connectorClass)) {
                        String message =
                            "Class "+connectorClass+" does not implement "+
                            Connector.class.getName();
                        throw new ConfigurationException(message);
                    }
                    LocalConnectorInfoImpl info = new LocalConnectorInfoImpl();
                    info.setConnectorClass(connectorClass.asSubclass(Connector.class));
                    info.setConnectorConfigurationClass(
                            options.configurationClass());
                    info.setConnectorDisplayNameKey(
                            options.displayNameKey());
                    info.setConnectorKey(
                            new ConnectorKey(bundleInfo.getManifest().getBundleName(),
                                    bundleInfo.getManifest().getBundleVersion(),
                                    connectorClass.getName()));
                    ConnectorMessagesImpl messages = 
                        loadMessageCatalog(bundleInfo.getResolvedContents(),
                                loader,
                                info.getConnectorClass());
                    info.setMessages(messages);
                    info.setDefaultAPIConfiguration(createDefaultAPIConfiguration(info));
                    rv.add(info);
                }
            }
        }
        return rv;
    }
    
    

    /**
     * Create an instance of the {@link APIConfiguration} object to setup the
     * framework etc..
     */
    private APIConfigurationImpl 
    createDefaultAPIConfiguration(LocalConnectorInfoImpl localInfo) {
        //setup classloader since we are going to construct the
        //config bean
        ThreadClassLoaderManager.getInstance().pushClassLoader(localInfo.getConnectorClass().getClassLoader());
        try {
            Class<? extends Connector> connectorClass =
                localInfo.getConnectorClass();
            APIConfigurationImpl rv = new APIConfigurationImpl();
            Configuration config = 
                localInfo.getConnectorConfigurationClass().newInstance();
            boolean pooling = PoolableConnector.class.isAssignableFrom(connectorClass);
            rv.setConnectorPoolingSupported(pooling);
            rv.setConfigurationProperties(JavaClassProperties.createConfigurationProperties(config));
            rv.setConnectorInfo(localInfo);
            rv.setSupportedOperations(FrameworkUtil.getDefaultSupportedOperations(connectorClass));
            return rv;
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
        finally {
            ThreadClassLoaderManager.getInstance().popClassLoader();
        }
    }
        
    private ConnectorMessagesImpl loadMessageCatalog(Set<String> bundleContents,
            ClassLoader loader,
            Class<? extends Connector> connector) 
        throws ConfigurationException
    {
        try {
            final String prefix = getBundleNamePrefix(connector);
            final String suffix = ".properties";
            ConnectorMessagesImpl rv = new ConnectorMessagesImpl();
            for (String path : bundleContents) {
                if ( path.startsWith(prefix) ) {
                    String localeStr = path.substring(prefix.length());
                    if ( localeStr.endsWith(suffix) ) {
                        localeStr = localeStr.substring(0, localeStr.length()-suffix.length());
                        Locale locale = parseLocale(localeStr);
                        Properties properties = 
                            IOUtil.getResourceAsProperties(loader, path);
                        Map<String,String> map = 
                            CollectionUtil.newMap(properties);
                        rv.getCatalogs().put(locale, map);
                    }
                }
            }
            return rv;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }
        
    private Locale parseLocale(String str) {
        String lang = null;
        String country = null;
        String variant = null;
        StringTokenizer tok = new StringTokenizer(str,"_",false);
        if ( tok.hasMoreTokens() ) {
            lang = tok.nextToken();
        }
        if ( tok.hasMoreTokens() ) {
            country = tok.nextToken();
        }
        if ( tok.hasMoreTokens() ) {
            variant = tok.nextToken();
        }
        if ( variant != null ) {
            return new Locale(lang,country,variant);
        }
        else if ( country != null ) {
            return new Locale(lang,country);
        }
        else if ( lang != null ) {
            return new Locale(lang);
        }
        else {
            return new Locale("");
        }
    }
    
    private static String getBundleNamePrefix(Class<? extends Connector> connector) {
        // figure out the message catalog..
        ConnectorClass configOpts = connector.getAnnotation(
                ConnectorClass.class);
        String pkage = ReflectionUtil.getPackage(connector);
        String messageCatalog = pkage + ".Messages";
        if (configOpts != null
                && StringUtil.isNotBlank(configOpts.messageCatalogPath())) {
            messageCatalog = configOpts.messageCatalogPath();
        }
        return messageCatalog.replace('.', '/');
    }
    
    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        for (ConnectorInfo info : _connectorInfo) {
            if ( info.getConnectorKey().equals(key)) {
                return info;
            }
        }
        return null;
    }

    public List<ConnectorInfo> getConnectorInfos() {
        return Collections.unmodifiableList(_connectorInfo);
    }

}
