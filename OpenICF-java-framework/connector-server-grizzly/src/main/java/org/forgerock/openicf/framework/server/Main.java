/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2015 ForgeRock AS.
 */
package org.forgerock.openicf.framework.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.LogManager;

import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.client.RemoteWSFrameworkConnectionInfo;
import org.forgerock.openicf.framework.local.AsyncLocalConnectorInfoManager;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public final class Main {

    private static final String PROP_PORT = "connectorserver.port";
    private static final String PROP_BUNDLE_DIR = "connectorserver.bundleDir";
    private static final String PROP_LIB_DIR = "connectorserver.libDir";
    private static final String PROP_SSL = "connectorserver.usessl";
    private static final String PROP_IFADDRESS = "connectorserver.ifaddress";
    private static final String PROP_KEY = "connectorserver.key";
    private static final String PROP_FACADE_LIFETIME = "connectorserver.maxFacadeLifeTime";
    private static final String PROP_LOGGER_CLASS = "connectorserver.loggerClass";
    private static final String PROP_REMOTE_URL = "connectorserver.url";
    private static final String PROP_REMOTE_PRINCIPAL = "connectorserver.principal";
    private static final String PROP_REMOTE_PASSWORD = "connectorserver.password";

    private static final String PROP_PROXY_HOST = "connectorserver.proxyHost";
    private static final String PROP_PROXY_PORT = "connectorserver.proxyPort";
    private static final String PROP_PROXY_PRINCIPAL = "connectorserver.proxyPrincipal";
    private static final String PROP_PROXY_PASSWORD = "connectorserver.proxyPassword";

    private static final String DEFAULT_LOG_SPI =
            "org.identityconnectors.common.logging.StdOutLogger";

    private static ConnectorServer connectorServer;
    private static ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework;

    private static Log log; // Initialized lazily to avoid early initialization.

    private static void usage() {
        System.out.println("Usage: Main -run -properties <connectorserver.properties>");
        System.out.println("       Main -service -properties <connectorserver.properties>");
        System.out
                .println("       Main -setKey -key <key> -properties <connectorserver.properties>");
        System.out.println("       Main -setDefaults -properties <connectorserver.properties>");
        System.out.println("NOTE: If using SSL, you must specify the system config");
        System.out.println("    properties: ");
        System.out.println("        -Djavax.net.ssl.keyStore");
        System.out.println("        -Djavax.net.ssl.keyStoreType (optional)");
        System.out.println("        -Djavax.net.ssl.keyStorePassword");
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0 || arguments.length % 2 != 1) {
            usage();
            return;
        }

        String cmd = arguments[0];
        if (cmd.equalsIgnoreCase("-run")) {
            String propertiesFileName = getArgumentValue(arguments, "-properties");
            if (StringUtil.isNotBlank(propertiesFileName)) {
                Properties properties = IOUtil.loadPropertiesFile(propertiesFileName);
                run(properties);
                final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Press q to shutdown.");
                char c;
                // read characters
                do {
                    c = (char) br.read();
                } while (c != 'q');
                connectorServer.stop();
                connectorServer.destroy();
            } else {
                usage();
            }
        } else if (cmd.equalsIgnoreCase("-service")) {
            /*
             * Using Procrun in jvm mode: Note that the method handling service
             * start should create and start a separate thread to carry out the
             * processing, and then return. The start and stop methods are
             * called from different threads.
             */
            String propertiesFileName = getArgumentValue(arguments, "-properties");
            if (StringUtil.isNotBlank(propertiesFileName)) {
                Properties properties = IOUtil.loadPropertiesFile(propertiesFileName);
                run(properties);
            } else {
                usage();
            }
        } else if (cmd.equalsIgnoreCase("-setkey")) {
            String propertiesFileName = getArgumentValue(arguments, "-properties");
            if (StringUtil.isBlank(propertiesFileName)) {
                usage();
                return;
            }
            Properties properties = IOUtil.loadPropertiesFile(propertiesFileName);
            String key = getArgumentValue(arguments, "-key");
            if (StringUtil.isBlank(key)) {

                System.out.print("Please enter the new key: ");
                char v1[] = System.console().readPassword();

                System.out.print("Please confirm the new key: ");
                char v2[] = System.console().readPassword();

                if (!Arrays.equals(v1, v2)) {
                    System.out.println("Error: Key mismatch.");
                    return;
                }
                properties.put(PROP_KEY, SecurityUtil.computeBase64SHA1Hash(v2));
            } else {
                properties.put(PROP_KEY, SecurityUtil.computeBase64SHA1Hash(key.toCharArray()));
            }

            IOUtil.storePropertiesFile(new File(propertiesFileName), properties);
            System.out.println("Key has been successfully updated.");
        } else if (cmd.equalsIgnoreCase("-setDefaults")) {
            String propertiesFileName = getArgumentValue(arguments, "-properties");
            if (StringUtil.isNotBlank(propertiesFileName)) {
                IOUtil.extractResourceToFile(Main.class, "connectorserver.properties", new File(
                        propertiesFileName));
                System.out.println("Default configuration successfully restored.");
            } else {
                usage();
            }
        } else {
            usage();
        }
    }

    private static void run(Properties properties) throws Exception {
        loadProperties(properties);
        if (connectorServer != null) {
            connectorServer.start();
            getLog().info("ConnectorServer listening on: " + connectorServer.getListeners());
        }
    }

    private static void loadProperties(Properties properties) throws Exception {
        if (connectorServer != null || connectorFramework != null) {
            // Procrun called main() without calling stop().
            // Do not use a logging statement here to avoid initializing logging
            // too early just because a bug in procrun.
            System.err.println("Server has already been started");
        }

        String portStr = properties.getProperty(PROP_PORT);
        String bundleDirStr = properties.getProperty(PROP_BUNDLE_DIR);
        String libDirStr = properties.getProperty(PROP_LIB_DIR);
        String useSSLStr = properties.getProperty(PROP_SSL);
        String ifAddress = properties.getProperty(PROP_IFADDRESS);
        String keyHash = properties.getProperty(PROP_KEY);
        String facadeLifeTime = properties.getProperty(PROP_FACADE_LIFETIME);
        String loggerClass = properties.getProperty(PROP_LOGGER_CLASS);
        String url  = properties.getProperty(PROP_REMOTE_URL);

        if (loggerClass == null) {
            loggerClass = DEFAULT_LOG_SPI;
        }
        ensureLoggingNotInitialized();
        System.setProperty(Log.LOGSPI_PROP, loggerClass);

        int port = -1;
        List<RemoteWSFrameworkConnectionInfo> connectionInfos = null;

        if (bundleDirStr == null) {
            throw new ConnectorException("connectorserver.properties is missing " + PROP_BUNDLE_DIR);
        }

        // Work around issue 604. It seems that sometimes procrun will run
        // the start method in a thread with a null context class loader.
        if (Thread.currentThread().getContextClassLoader() == null) {
            getLog().warn("Context class loader is null, working around");
            Thread.currentThread().setContextClassLoader(Main.class.getClassLoader());
        }

        if (StringUtil.isNotBlank(url)){
            String principal = properties.getProperty(PROP_REMOTE_PRINCIPAL);
            if (principal == null){
                throw new ConnectorException("connectorserver.properties is missing " + PROP_REMOTE_PRINCIPAL);
            }
            String password = properties.getProperty(PROP_REMOTE_PASSWORD);
            if (password == null){
                throw new ConnectorException("connectorserver.properties is missing " + PROP_REMOTE_PASSWORD);
            }

            String proxyHost = properties.getProperty(PROP_PROXY_HOST);
            String proxyPort = properties.getProperty(PROP_PROXY_PORT);
            String proxyPrincipal = properties.getProperty(PROP_PROXY_PRINCIPAL);
            String proxyPassword = properties.getProperty(PROP_PROXY_PASSWORD);

            RemoteWSFrameworkConnectionInfo.Builder builder = RemoteWSFrameworkConnectionInfo.newBuilder()
                    .setRemoteURI(URI.create(url)).setPrincipal(principal)
                    .setPassword(new GuardedString(password.toCharArray()));

            if (proxyHost != null){
                builder.setProxyHost(proxyHost);
                if (proxyPort != null){
                    builder.setProxyPort(Integer.getInteger(proxyPort));
                }
                if (proxyPrincipal != null) {
                    builder.setProxyPrincipal(proxyPrincipal);
                }
                if (proxyPassword != null) {
                    builder.setProxyPassword(
                            new GuardedString(proxyPassword.toCharArray()));
                }
            }

            connectionInfos = CollectionUtil.newList(builder.build());

        } else if (StringUtil.isBlank(portStr)) {
            throw new ConnectorException("connectorserver.properties is missing " + PROP_PORT);
        } else {
            port = Integer.parseInt(portStr);
            if (keyHash == null) {
                throw new ConnectorException("connectorserver.properties is missing " + PROP_KEY);
            }
        }


        final ConnectorFrameworkFactory connectorFrameworkFactory = new ConnectorFrameworkFactory();
        connectorFrameworkFactory.initialize(properties);

        ClassLoader bundleParentClassLoader = null;
        if (libDirStr != null) {
            bundleParentClassLoader = buildLibClassLoader(new File(libDirStr));
            connectorFrameworkFactory
                    .setDefaultConnectorBundleParentClassLoader(bundleParentClassLoader);
        }
        List<URL> bundleUrls = buildBundleURLs(new File(bundleDirStr));

        if (port > 0) {
            connectorServer = new ConnectorServer();
            connectorServer.setConnectorFrameworkFactory(connectorFrameworkFactory);

            if (bundleParentClassLoader != null) {
                connectorServer.setBundleParentClassLoader(bundleParentClassLoader);
            }
            connectorServer.setConnectorBundleURLs(bundleUrls);
            connectorServer.setKeyHash(keyHash);

            connectorServer.init();
            if (useSSLStr != null) {
                boolean useSSL = Boolean.parseBoolean(useSSLStr);
                connectorServer.addListener(null, ifAddress, port, useSSL ? new SSLContextConfigurator(
                        true) : null);
            } else {
                connectorServer.addListener(null, ifAddress, port);
            }
        } else if (connectionInfos != null) {
            connectorFramework = connectorFrameworkFactory.acquire();

            connectorFramework.get().getLocalManager().addConnectorBundle(bundleUrls);

            for (RemoteWSFrameworkConnectionInfo connectionInfo : connectionInfos) {
                connectorFramework.get().getRemoteManager(connectionInfo);
                getLog().info("ConnectorAgent connecting to : " + connectionInfo.getRemoteURI());
            }
        }
    }

    public static void stop(String[] args) throws Exception {
        if (connectorServer == null && connectorFramework == null) {
            // Procrun called stop() without calling main().
            // Do not use a logging statement here to avoid initializing logging
            // too early just because a bug in procrun.
            System.err.println("Server has not been started yet");
            return;
        }

        // Work around issue 604. It seems that sometimes procrun will run
        // the start method in a thread with a null context class loader.
        if (Thread.currentThread().getContextClassLoader() == null) {
            getLog().warn("Context class loader is null, working around");
            Thread.currentThread().setContextClassLoader(Main.class.getClassLoader());
        }

        if (connectorServer != null) {
            connectorServer.stop();
            // Do not set connectorServer to null, because that way the check in
            // run() fails
            // and we ensure that the server cannot be started twice in the same
            // JVM.
            getLog().info("Connector server stopped");
            connectorServer.destroy();
            connectorServer = null;
        } else if (connectorFramework != null) {
            connectorFramework.release();
            connectorFramework = null;
        }
        // LogManager installs a shutdown hook to reset the handlers (which
        // includes
        // closing any files opened by FileHandler-s). Procrun does not call
        // JNI_DestroyJavaVM(), so shutdown hooks do not run. We reset the LM
        // here.
        LogManager.getLogManager().reset();
    }

    private static void ensureLoggingNotInitialized() throws Exception {
        Field field = Log.class.getDeclaredField("cacheSPI");
        field.setAccessible(true);
        if (field.get(null) != null) {
            throw new IllegalStateException("Logging has already been initialized");
        }
    }

    private static List<URL> buildBundleURLs(File dir) throws MalformedURLException {
        List<URL> rv = getJarFiles(dir);
        if (rv.isEmpty()) {
            getLog().warn("No bundles found in the bundles directory");
        }
        return rv;
    }

    private static ClassLoader buildLibClassLoader(File dir) throws MalformedURLException {
        List<URL> jars = getJarFiles(dir);
        if (!jars.isEmpty()) {
            return new URLClassLoader(jars.toArray(new URL[jars.size()]),
                    ConnectorInfoManagerFactory.class.getClassLoader());
        }
        return null;

    }

    private static List<URL> getJarFiles(File dir) throws MalformedURLException {
        if (!dir.isDirectory()) {
            throw new ConnectorException("The 'connectorserver.bundleDir' at: '"
                    + dir.getAbsolutePath() + "' does not exist");
        }
        List<URL> rv = new ArrayList<URL>();
        for (File bundle : dir.listFiles()) {
            if (bundle.getName().endsWith(".jar")) {
                rv.add(bundle.toURI().toURL());
            }
        }
        return rv;
    }

    private static String getArgumentValue(String[] arguments, String keyName) {
        if (arguments.length >= 2) {
            for (int i = 0; i < arguments.length - 1; i++) {
                String name = arguments[i];
                String value = arguments[i + 1];
                if (name.equalsIgnoreCase(keyName)) {
                    return value;
                }
            }
        }
        return null;
    }

    private synchronized static Log getLog() {
        if (log == null) {
            log = Log.getLog(Main.class);
        }
        return log;
    }

    public void init(String[] arguments) throws Exception {
        String propertiesFileName = getArgumentValue(arguments, "-properties");
        if (StringUtil.isNotBlank(propertiesFileName)) {
            Properties properties = IOUtil.loadPropertiesFile(propertiesFileName);
            loadProperties(properties);
        } else {
            System.exit(1);
        }
    }

    public void start() throws Exception {
        if (null == connectorServer) {
            loadProperties(IOUtil.loadPropertiesFile("conf/ConnectorServer.properties"));
        }
        if (null != connectorServer) {
            connectorServer.start();
        }
    }

    public void stop() throws Exception {
        if (null != connectorServer) {
            connectorServer.stop();
        }
    }

    public void destroy() {
        try {
            if (null != connectorServer) {
                connectorServer.destroy();
            }
            if (null != connectorFramework) {
                connectorFramework.release();
            }
        } catch (Exception e) {
            getLog().warn(e, "Failed to destroy ConnectorServer");
        }
    }
}
