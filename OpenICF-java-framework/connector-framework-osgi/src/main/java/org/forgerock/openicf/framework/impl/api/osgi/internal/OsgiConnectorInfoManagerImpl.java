/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright © 2011 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.forgerock.openicf.framework.impl.api.osgi.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Manifest;
import org.forgerock.openicf.framework.api.osgi.ConnectorManager;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.ReflectionUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.ConnectorMessagesImpl;
import org.identityconnectors.framework.impl.api.local.ConnectorBundleManifest;
import org.identityconnectors.framework.impl.api.local.ConnectorBundleManifestParser;
import org.identityconnectors.framework.impl.api.local.ConnectorPoolManager;
import org.identityconnectors.framework.impl.api.local.JavaClassProperties;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.local.ThreadClassLoaderManager;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.ops4j.pax.swissbox.extender.BundleObserver;
import org.ops4j.pax.swissbox.extender.ManifestEntry;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample Class Doc
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0.0
 */
public class OsgiConnectorInfoManagerImpl implements ConnectorManager, BundleObserver<ManifestEntry> {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(OsgiConnectorInfoManagerImpl.class);
    private final HashMap<String, Pair<Bundle, ConnectorInfo>> connectorInfoCache = new HashMap<String, Pair<Bundle, ConnectorInfo>>();

    public OsgiConnectorInfoManagerImpl() throws RuntimeException {
    }

    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        for (Pair<Bundle, ConnectorInfo> info : connectorInfoCache.values()) {
            if (info.second.getConnectorKey().equals(key)) {
                return info.second;
            }
        }
        return null;
    }

    public List<ConnectorInfo> getConnectorInfos() {
        List<ConnectorInfo> result = new ArrayList<ConnectorInfo>(connectorInfoCache.size());
        for (Pair<Bundle, ConnectorInfo> info : connectorInfoCache.values()) {
            result.add(info.second);
        }
        return CollectionUtil.newReadOnlyList(result);
    }

    public void dispose() {
        ConnectorPoolManager.dispose();
    }

    public ConnectorFacade newInstance(APIConfiguration config) {



        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addingEntries(Bundle bundle, List<ManifestEntry> list) {
        synchronized (connectorInfoCache) {
            if (!connectorInfoCache.containsKey(bundle.getSymbolicName())) {
                connectorInfoCache.put(bundle.getSymbolicName(), processBundle(bundle));
                logger.info("Add Connector {}, list: {}", bundle.getSymbolicName(), list);
            }
        }
    }

    public void removingEntries(Bundle bundle, List<ManifestEntry> list) {
        synchronized (connectorInfoCache) {
            connectorInfoCache.remove(bundle.getSymbolicName());
        }
        logger.info("Remove Connector {}, list: {}", bundle.getSymbolicName(), list);
    }

    //TODO Implement this method
    private Pair<Bundle, ConnectorInfo> processBundle(Bundle bundle) {
        return new Pair(bundle, new LocalConnectorInfoImpl());
    }

    /**
     * Final pass - create connector infos
     */
    private List<ConnectorInfo> createConnectorInfo(Bundle parsed) throws ConfigurationException, IOException {
        List<ConnectorInfo> rv = new ArrayList<ConnectorInfo>();
        ClassLoader loader = parsed.getClass().getClassLoader();

        URL metaUrl = parsed.getEntry("META-INF/MANIFEST.MF");
        //This may throw IOException
        ConnectorBundleManifest manifest = ( new ConnectorBundleManifestParser(parsed.getLocation(), new Manifest(metaUrl.openStream())) ).parse();

        Enumeration<String> classFiles = parsed.findEntries("/", "*.class", true);
        Enumeration<String> propertyFiles = parsed.findEntries("/", "*.properties", true);

        while (classFiles.hasMoreElements()) {

            Class<?> connectorClass = null;
            ConnectorClass options = null;
            String name = classFiles.nextElement();
            String className = name.substring(0, name.length() - ".class".length());
            className = className.replace('/', '.');
            try {
                connectorClass = parsed.loadClass(className);
                options = connectorClass.getAnnotation(ConnectorClass.class);
            }
            catch (Throwable e) {
                //probe for the class. this might not be an error since it might be from a bundle
                //fragment ( a bundle only included by other bundles ). However, we should definitely warn
                logger.warn("Unable to load class {} from bundle {}. Class will be ignored and will not be listed in list of connectors.",
                        new Object[]{className, parsed.getLocation()}, e);
            }

            if (connectorClass != null && options != null) {
                if (!Connector.class.isAssignableFrom(connectorClass)) {
                    String message = "Class " + connectorClass + " does not implement " + Connector.class.getName();
                    throw new ConfigurationException(message);
                }
                LocalConnectorInfoImpl info = new LocalConnectorInfoImpl();
                info.setConnectorClass(connectorClass.asSubclass(Connector.class));
                info.setConnectorConfigurationClass(options.configurationClass());
                info.setConnectorDisplayNameKey(options.displayNameKey());
                info.setConnectorKey(new ConnectorKey(
                        manifest.getBundleName(),
                        manifest.getBundleVersion(),
                        connectorClass.getName()));
                ConnectorMessagesImpl messages = loadMessageCatalog(
                        propertyFiles,
                        parsed,
                        info.getConnectorClass());
                info.setMessages(messages);
                info.setDefaultAPIConfiguration(createDefaultAPIConfiguration(info));
                rv.add(info);

            }
        }
        return rv;
    }

    /**
     * Create an instance of the {@link APIConfiguration} object to setup the
     * framework etc..
     */
    private APIConfigurationImpl createDefaultAPIConfiguration(LocalConnectorInfoImpl localInfo) {
        //setup classloader since we are going to construct the config bean
        ThreadClassLoaderManager.getInstance().pushClassLoader(localInfo.getConnectorClass().getClassLoader());
        try {
            Class<? extends Connector> connectorClass = localInfo.getConnectorClass();
            APIConfigurationImpl rv = new APIConfigurationImpl();
            Configuration config = localInfo.getConnectorConfigurationClass().newInstance();
            boolean pooling = PoolableConnector.class.isAssignableFrom(connectorClass);
            rv.setConnectorPoolingSupported(pooling);
            rv.setConfigurationProperties(JavaClassProperties.createConfigurationProperties(config));
            rv.setConnectorInfo(localInfo);
            rv.setSupportedOperations(FrameworkUtil.getDefaultSupportedOperations(connectorClass));
            return rv;
        }
        catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
        finally {
            ThreadClassLoaderManager.getInstance().popClassLoader();
        }
    }

    private ConnectorMessagesImpl loadMessageCatalog(Enumeration<String> propertyFiles, Bundle loader, Class<? extends Connector> connector)
            throws ConfigurationException {
        try {
            final String[] prefixes = getBundleNamePrefixes(connector);
            final String suffix = ".properties";
            ConnectorMessagesImpl rv = new ConnectorMessagesImpl();
            //iterate last to first so that first one wins
            for (int i = prefixes.length - 1; i >= 0; i--) {
                String prefix = prefixes[i];
                while (propertyFiles.hasMoreElements()) {
                    String path = propertyFiles.nextElement();
                    if (path.startsWith(prefix)) {
                        String localeStr = path.substring(prefix.length());
                        if (localeStr.endsWith(suffix)) {
                            localeStr = localeStr.substring(0, localeStr.length() - suffix.length());
                            Locale locale = parseLocale(localeStr);
                            Properties properties = getResourceAsProperties(loader, path);
                            //get or create map
                            Map<String, String> map = rv.getCatalogs().get(locale);
                            if (map == null) {
                                map = new HashMap<String, String>();
                                rv.getCatalogs().put(locale, map);
                            }
                            //merge properties into map, overwriting
                            //any that already exist
                            map.putAll(CollectionUtil.newMap(properties));
                        }
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
        StringTokenizer tok = new StringTokenizer(str, "_", false);
        if (tok.hasMoreTokens()) {
            lang = tok.nextToken();
        }
        if (tok.hasMoreTokens()) {
            country = tok.nextToken();
        }
        if (tok.hasMoreTokens()) {
            variant = tok.nextToken();
        }
        if (variant != null) {
            return new Locale(lang, country, variant);
        } else if (country != null) {
            return new Locale(lang, country);
        } else if (lang != null) {
            return new Locale(lang);
        } else {
            return new Locale("");
        }
    }

    private String[] getBundleNamePrefixes(Class<? extends Connector> connector) {
        // figure out the message catalog..
        ConnectorClass configOpts = connector.getAnnotation(ConnectorClass.class);
        String[] paths = null;
        if (configOpts != null) {
            paths = configOpts.messageCatalogPaths();
        }
        if (paths == null || paths.length == 0) {
            String pkage = ReflectionUtil.getPackage(connector);
            String messageCatalog = pkage + ".Messages";
            paths = new String[]{messageCatalog};
        }
        for (int i = 0; i < paths.length; i++) {
            paths[i] = paths[i].replace('.', '/');
        }
        return paths;
    }

    public Properties getResourceAsProperties(Bundle loader, String path) throws IOException {
        //TODO Potential NPE Exception.
        InputStream in = loader.getResource(path).openStream();
        if (in == null) {
            return null;
        }
        try {
            Properties rv = new Properties();
            rv.load(in);
            return rv;
        } finally {
            in.close();
        }
    }
}
