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
import org.forgerock.openicf.framework.api.osgi.ConnectorManager;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.ReflectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.AbstractConnectorInfo;
import org.identityconnectors.framework.impl.api.ConnectorMessagesImpl;
import org.identityconnectors.framework.impl.api.local.ConnectorPoolManager;
import org.identityconnectors.framework.impl.api.local.JavaClassProperties;
import org.identityconnectors.framework.impl.api.local.LocalConnectorFacadeImpl;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.local.ThreadClassLoaderManager;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.ops4j.lang.NullArgumentException;
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
    /**
     * Connector Cache
     */
    private final HashMap<String, Pair<Bundle, List<ConnectorInfo>>> connectorInfoCache = new HashMap<String, Pair<Bundle, List<ConnectorInfo>>>();

    public ConnectorInfo findConnectorInfo(ConnectorKey key) {
        for (Pair<Bundle, List<ConnectorInfo>> bundle : connectorInfoCache.values()) {
            for (ConnectorInfo info : bundle.second) {
                if (info.getConnectorKey().equals(key)) {
                    return info;
                }
            }
        }
        return null;
    }

    public List<ConnectorInfo> getConnectorInfos() {
        List<ConnectorInfo> result = new ArrayList<ConnectorInfo>();
        for (Pair<Bundle, List<ConnectorInfo>> info : connectorInfoCache.values()) {
            result.addAll(info.second);
        }
        return CollectionUtil.newReadOnlyList(result);
    }

    public void dispose() {
        ConnectorPoolManager.dispose();
    }

    public ConnectorFacade newInstance(APIConfiguration config) {
        ConnectorFacade ret = null;
        APIConfigurationImpl impl = (APIConfigurationImpl) config;
        AbstractConnectorInfo connectorInfo = impl.getConnectorInfo();
        if (connectorInfo instanceof LocalConnectorInfoImpl) {
            LocalConnectorInfoImpl localInfo =
                    (LocalConnectorInfoImpl) connectorInfo;
            try {
                // create a new Provisioner..
                ret = new LocalConnectorFacadeImpl(localInfo, impl);
            }
            catch (Exception ex) {
                String connector = impl.getConnectorInfo().getConnectorKey().toString();
                logger.error("Failed to create new connector facade: {}, {}",
                        new Object[]{connector, config}, ex);
                throw ConnectorException.wrap(ex);
            }
        } else {
            throw new ConnectorException("RemoteConnector not supported!");
        }
        return ret;
    }

    public void addingEntries(Bundle bundle, List<ManifestEntry> list) {
        NullArgumentException.validateNotNull(bundle, "Bundle");
        NullArgumentException.validateNotNull(list, "ManifestEntry");
        synchronized (connectorInfoCache) {
            if (!connectorInfoCache.containsKey(bundle.getSymbolicName())) {
                Pair<Bundle, List<ConnectorInfo>> info = processBundle(bundle, list);
                if (null != info) {
                    connectorInfoCache.put(bundle.getSymbolicName(), info);
                }
                logger.info("Add Connector {}, list: {}", bundle.getSymbolicName(), list);
            }
        }
    }

    public void removingEntries(Bundle bundle, List<ManifestEntry> list) {
        NullArgumentException.validateNotNull(bundle, "Bundle");
        synchronized (connectorInfoCache) {
            connectorInfoCache.remove(bundle.getSymbolicName());
        }
        logger.info("Remove Connector {}, list: {}", bundle.getSymbolicName(), list);
    }

    private Pair<Bundle, List<ConnectorInfo>> processBundle(Bundle bundle, List<ManifestEntry> list) {
        Pair<Bundle, List<ConnectorInfo>> result = null;
        try {
            List<ConnectorInfo> info = createConnectorInfo(bundle, list);
            if (!info.isEmpty()) {
                result = new Pair<Bundle, List<ConnectorInfo>>(bundle, info);
            }
        }
        catch (Throwable t) {
            logger.error("ConnectorBundel {} loading failed.", bundle.getSymbolicName(), t);
        }
        return result;
    }

    /**
     * Final pass - create connector infos
     */
    private List<ConnectorInfo> createConnectorInfo(Bundle parsed, List<ManifestEntry> manifestEnties) {
        List<ConnectorInfo> rv = new ArrayList<ConnectorInfo>();
        Enumeration<URL> classFiles = parsed.findEntries("/", "*.class", true);
        Enumeration<URL> propertyFiles = parsed.findEntries("/", "*.properties", true);

        String frameworkVersion = null;
        String bundleName = null;
        String bundleVersion = null;

        for (ManifestEntry entry : manifestEnties) {
            if (ConnectorManifestScanner.ATT_FRAMEWORK_VERSION.equals(entry.getKey())) {
                frameworkVersion = entry.getValue();
            } else if (ConnectorManifestScanner.ATT_BUNDLE_NAME.equals(entry.getKey())) {
                bundleName = entry.getValue();
            } else if (ConnectorManifestScanner.ATT_BUNDLE_VERSION.equals(entry.getKey())) {
                bundleVersion = entry.getValue();
            }
        }

        if (StringUtil.isBlank(bundleName) || StringUtil.isBlank(bundleVersion)) {
            return rv;
        }

        while (classFiles.hasMoreElements()) {

            Class<?> connectorClass = null;
            ConnectorClass options = null;
            String name = classFiles.nextElement().getFile();

            String className = name.substring(1, name.length() - ".class".length());
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
                        bundleName,
                        bundleVersion,
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

    private ConnectorMessagesImpl loadMessageCatalog(Enumeration<URL> propertyFiles, Bundle loader, Class<? extends Connector> connector)
            throws ConfigurationException {
        try {
            final String[] prefixes = getBundleNamePrefixes(connector);
            final String suffix = ".properties";
            ConnectorMessagesImpl rv = new ConnectorMessagesImpl();
            //iterate last to first so that first one wins
            for (int i = prefixes.length - 1; i >= 0; i--) {
                String prefix = prefixes[i];
                while (propertyFiles.hasMoreElements()) {
                    String path = propertyFiles.nextElement().getFile();
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
            paths[i] = "/" + paths[i].replace('.', '/');
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
        }
        finally {
            in.close();
        }
    }
}
