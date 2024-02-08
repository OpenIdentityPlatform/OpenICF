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
 * Portions Copyrighted 2010-2014 ForgeRock AS.
 */
package org.identityconnectors.framework.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.LogManager;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
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

    private static final String DEFAULT_LOG_SPI =
            "org.identityconnectors.common.logging.StdOutLogger";

    private static ConnectorServer connectorServer;

    private static Log log; // Initialized lazily to avoid early initialization.

    private static void usage() {
        System.out.println("Usage: Main -run -properties <connectorserver.properties>");
        System.out.println("       Main -service -properties <connectorserver.properties>");
        System.out.println("       Main -setKey -key <key> -properties <connectorserver.properties>");
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

                Scanner scanner = new Scanner(System.in);
                System.out.print("Please enter the new key: ");
                Thread maskThread = beforeReadPassword();
                char v1[] = scanner.nextLine().toCharArray();
                afterReadPassword(maskThread);                
                
                System.out.print("Please confirm the new key: ");
                maskThread = beforeReadPassword();
                char v2[] = scanner.nextLine().toCharArray();
                afterReadPassword(maskThread);
                
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

    private static Thread beforeReadPassword() {
        Thread maskThread = new Thread() {
            public void run() {
                while (!interrupted()) {
                    try {
                        System.out.print("\010*");
                        sleep(5);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };

        maskThread.setPriority(Thread.MAX_PRIORITY);
        maskThread.setDaemon(true);
        maskThread.start();
        return maskThread;
    }

    private static void afterReadPassword(Thread maskThread) throws InterruptedException {
        if (maskThread != null && maskThread.isAlive()) {
            maskThread.interrupt();
            Thread.sleep(5);
        }
    }
    
    private static void run(Properties properties) throws Exception {
        loadProperties(properties);
        connectorServer.start();
        getLog().info("Connector server listening on port " + connectorServer.getPort());
    }

    private static void loadProperties(Properties properties) throws Exception {
        if (connectorServer != null) {
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
        if (portStr == null) {
            throw new ConnectorException("connectorserver.properties is missing " + PROP_PORT);
        }
        if (bundleDirStr == null) {
            throw new ConnectorException("connectorserver.properties is missing " + PROP_BUNDLE_DIR);
        }
        if (keyHash == null) {
            throw new ConnectorException("connectorserver.properties is missing " + PROP_KEY);
        }

        if (loggerClass == null) {
            loggerClass = DEFAULT_LOG_SPI;
        }
        ensureLoggingNotInitialized();
        System.setProperty(Log.LOGSPI_PROP, loggerClass);

        int port = Integer.parseInt(portStr);

        // Work around issue 604. It seems that sometimes procrun will run
        // the start method in a thread with a null context class loader.
        if (Thread.currentThread().getContextClassLoader() == null) {
            getLog().warn("Context class loader is null, working around");
            Thread.currentThread().setContextClassLoader(Main.class.getClassLoader());
        }

        connectorServer = ConnectorServer.newInstance();
        connectorServer.setPort(port);
        connectorServer.setBundleURLs(buildBundleURLs(new File(bundleDirStr)));
        if (libDirStr != null) {
            connectorServer.setBundleParentClassLoader(buildLibClassLoader(new File(libDirStr)));
        }
        connectorServer.setKeyHash(keyHash);
        if (useSSLStr != null) {
            boolean useSSL = Boolean.parseBoolean(useSSLStr);
            connectorServer.setUseSSL(useSSL);
        }
        if (ifAddress != null) {
            connectorServer.setIfAddress(InetAddress.getByName(ifAddress));
        }
        if (facadeLifeTime != null) {
            connectorServer.setMaxFacadeLifeTime(Long.parseLong(facadeLifeTime));
        }
    }

    public static void stop(String[] args) {
        if (connectorServer == null) {
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

        connectorServer.stop();
        // Do not set connectorServer to null, because that way the check in run() fails
        // and we ensure that the server cannot be started twice in the same JVM.
        getLog().info("Connector server stopped");
        // LogManager installs a shutdown hook to reset the handlers (which includes
        // closing any files opened by FileHandler-s). Procrun does not call
        // JNI_DestroyJavaVM(), so shutdown hooks do not run. We reset the LM here.
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
            throw new ConnectorException(dir.getPath() + " does not exist");
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

    private static void handleThrowable(Throwable t)
    {
        if (t instanceof ThreadDeath)
            throw ((ThreadDeath)t);

        if (t instanceof VirtualMachineError)
            throw ((VirtualMachineError)t);
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
        connectorServer.start();
    }

    public void stop() throws Exception {
        connectorServer.stop();
    }

    public void destroy() {

    }
}
