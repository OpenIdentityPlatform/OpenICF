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
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.framework.impl.api.local;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;


public final class ConnectorBundleManifestParser {
    

    private static final String BUNDLE_PREFIX =
        "ConnectorBundle-";
    
    private static final String ATT_FRAMEWORK_VERSION =
        BUNDLE_PREFIX+"FrameworkVersion";
    
    private static final String ATT_BUNDLE_NAME =
        BUNDLE_PREFIX+"Name";
    
    private static final String ATT_BUNDLE_VERSION =
        BUNDLE_PREFIX+"Version";

    

    private static final Set<String> ALL_ATTRIBUTES =
        new HashSet<String>();
    static {
        String [] all = {
            ATT_FRAMEWORK_VERSION,
            ATT_BUNDLE_NAME,
            ATT_BUNDLE_VERSION
        };
        ALL_ATTRIBUTES.addAll(Arrays.asList(all));
    }
    
    private String _fileName;
    private Map<?,?> _properties;
    private boolean _requireBundleVersion;
    
    public ConnectorBundleManifestParser(String fileName,
            Manifest manifest) {
        _fileName = fileName;
        Map<String,String> properties = new HashMap<String,String>();
        for (Map.Entry<Object,Object> entry : manifest.getMainAttributes().entrySet()) {
           properties.put(String.valueOf(entry.getKey()), 
                   String.valueOf(entry.getValue()));
        }
        _properties = properties;
        //require bundle version if parsing a Manifest
        _requireBundleVersion = true;
    }

    public ConnectorBundleManifestParser(String fileName,
            Properties properties) {
        _fileName = fileName;
        _properties = properties;
        //don't require bundle version if parsing the bundle.properties
        _requireBundleVersion = false;
    }

    /**
     * Parses the manifest.
     * @return The manifest. Note that the classes/classloaders will
     * not be populated yet. That is to be done at a higher-level.
     * @throws ConfigurationException If there were any structural problems.
     */
    public ConnectorBundleManifest parse() 
        throws ConfigurationException {
        
        String frameworkVersion = 
            getRequiredAttribute(ATT_FRAMEWORK_VERSION);
        String bundleName = 
            getRequiredAttribute(ATT_BUNDLE_NAME);
        String bundleVersion;
        if (_requireBundleVersion) {
            bundleVersion = getRequiredAttribute(ATT_BUNDLE_VERSION);
        }
        else {
            bundleVersion = getAttribute(ATT_BUNDLE_VERSION);
        }
        
        if (!"1.0".equals(frameworkVersion) ) {
            String message = 
                "Bundle "+_fileName+" contains an unrecognized "+
                "framework version: "+frameworkVersion;
            throw new ConfigurationException(message);
        }

        ConnectorBundleManifest rv = new ConnectorBundleManifest();

        rv.setFrameworkVersion(frameworkVersion);
        rv.setBundleName(bundleName);
        rv.setBundleVersion(bundleVersion);
        
        return rv;
    }
    
    public static Map<String,String> toMap(ConnectorBundleManifest manifest) {
        Map<String,String> rv = new LinkedHashMap<String,String>();
        rv.put(ATT_FRAMEWORK_VERSION, manifest.getFrameworkVersion());
        rv.put(ATT_BUNDLE_NAME, manifest.getBundleName());
        rv.put(ATT_BUNDLE_VERSION, manifest.getBundleVersion());
        return rv;
    }
            
    private String getRequiredAttribute(String name) 
        throws ConfigurationException {
        String rv = getAttribute(name);
        if ( rv == null ) {
            throw missingRequiredAttribute(name);
        }
        return rv;
    }
    
    private String getAttribute(String name) {
        String rv = (String)_properties.get(name);
        return rv;
    }
    
    
    private ConfigurationException missingRequiredAttribute(String name)
    {
        String msg =
            "Bundle "+_fileName+" is missing required attribute '"+name+"'.";
        return new ConfigurationException(msg);
    }
    
}
