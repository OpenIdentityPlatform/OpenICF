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
package org.identityconnectors.framework.server;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;


public final class Main {
    private static final String PROP_PORT = "connectorserver.port";
    private static final String PROP_BUNDLE_DIR = "connectorserver.bundleDir";
    private static final String PROP_SSL  = "connectorserver.usessl";
    private static final String PROP_IFADDRESS = "connectorserver.ifaddress";
    private static final String PROP_KEY = "connectorserver.key";

    private static void usage() {
        System.out.println("Usage: Main -run -properties <connectorserver.properties>");
        System.out.println("       Main -setKey -key <key> -properties <connectorserver.properties>");
        System.out.println("       Main -setDefaults -properties <connectorserver.properties>");
        System.out.println("NOTE: If using SSL, you must specify the system config");
        System.out.println("    properties: ");
        System.out.println("        -Djavax.net.ssl.keyStore");
        System.out.println("        -Djavax.net.ssl.keyStoreType (optional)");
        System.out.println("        -Djavax.net.ssl.keyStorePassword");
    }
    
    public static void main(String [] args) 
        throws Exception {
        if ( args.length == 0 || args.length % 2 != 1) {
            usage();
            return;
        }
        String propertiesFileName = null;
        String key = null;
        for ( int i = 1; i < args.length; i+=2 ) {
            String name = args[i];
            String value = args[i+1];
            if (name.equalsIgnoreCase("-properties")) {
                propertiesFileName = value;
            }
            else if (name.equalsIgnoreCase("-key")) {
                key = value;
            }
            else {
                usage();
                return;
            }
        }
        String cmd = args[0];
        if ( cmd.equalsIgnoreCase("-run")) {
            if ( propertiesFileName == null || key != null ) {
                usage();
                return;
            }
            Properties properties = 
                IOUtil.loadPropertiesFile(propertiesFileName);
            run(properties);
        }
        else if (cmd.equalsIgnoreCase("-setkey")) {
            if ( propertiesFileName == null || key == null ) {
                usage();
                return;
            }
            Properties properties = IOUtil.loadPropertiesFile(propertiesFileName);
            properties.put(PROP_KEY, SecurityUtil.computeBase64SHA1Hash(key.toCharArray()));
            IOUtil.storePropertiesFile(new File(propertiesFileName),properties);
        }
        else if (cmd.equalsIgnoreCase("-setDefaults")) {
            if ( propertiesFileName == null || key != null ) {
                usage();
                return;
            }
            IOUtil.extractResourceToFile(Main.class, 
                    "connectorserver.properties", 
                    new File(propertiesFileName));
        }
        else {
            usage();
            return;
        }
    }
                    
    private static void run(Properties properties)
        throws Exception
    { 
        String portStr = properties.getProperty(PROP_PORT);
        String bundleDirStr = properties.getProperty(PROP_BUNDLE_DIR);
        String useSSLStr = properties.getProperty(PROP_SSL);
        String ifAddress = properties.getProperty(PROP_IFADDRESS);
        String keyHash = properties.getProperty(PROP_KEY);
        if ( portStr == null ) {
            throw new ConnectorException("connectorserver.properties is missing "+PROP_PORT);
        }
        if ( bundleDirStr == null ) {
            throw new ConnectorException("connectorserver.properties is missing "+PROP_BUNDLE_DIR);
        }
        if ( keyHash == null ) {
            throw new ConnectorException("connectorserver.properties is missing "+PROP_KEY);
        }
        
        
        int port = Integer.parseInt(portStr);
        
        ConnectorServer server = ConnectorServer.newInstance();
        server.setPort(port);
        server.setBundleURLs(buildURLs(new File(bundleDirStr)));
        server.setKeyHash(keyHash);
        if (useSSLStr != null) {
            boolean useSSL = Boolean.parseBoolean(useSSLStr);
            server.setUseSSL(useSSL);
        }
        if (ifAddress != null) {
            server.setIfAddress(InetAddress.getByName(ifAddress));
        }
        server.start();
        System.out.println("Connector server listening on port "+port);           
    }
    
    private static List<URL> buildURLs(File dir) throws Exception {
        if (!dir.isDirectory()) {
            throw new ConnectorException(dir.getPath()+" does not exist");
        }
        List<URL> rv = new ArrayList<URL>();
        for (File bundle : dir.listFiles()) {
            if ( bundle.getName().endsWith(".jar")) {
                rv.add(bundle.toURL());
            }
        }
        return rv;
    }
}
