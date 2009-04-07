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

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;


public class WorkingBundleInfo {
    /**
     * The original location for this bundle (for error reporting)
     */
    private String _originalLocation;
    /**
     * The manifest for this bundle
     */
    private ConnectorBundleManifest _manifest;
    
    /**
     * Immediate contents of the bundle
     */
    private Set<String> _immediateBundleContents = new HashSet<String>();
    
    /**
     * The URLs of the bundle itself and the libs in it
     */
    private List<URL> _immediateClassPath = new ArrayList<URL>();
    
    /**
     * List of included bundles
     */
    private List<WorkingBundleInfo> _embeddedBundles = new ArrayList<WorkingBundleInfo>();
    
    /**
     * Full classpath that includes the includes
     */
    private URL [] _resolvedClassPath;
    
    /**
     * Resolved contents of the bundle and all includes.
     */
    public Set<String> _resolvedContents;
    
    public WorkingBundleInfo(String originalLocation) {
        _originalLocation = originalLocation;
    }
    
    public String getOriginalLocation() {
        return _originalLocation;
    }
    
    public ConnectorBundleManifest getManifest() {
        return _manifest;
    }
    
    public void setManifest(ConnectorBundleManifest manifest) {
        _manifest = manifest;
    }
    
    public Set<String> getImmediateBundleContents() {
        return _immediateBundleContents;
    }
    
    public List<URL> getImmediateClassPath() {
        return _immediateClassPath;
    }
    
    public List<WorkingBundleInfo> getEmbeddedBundles() {
        return _embeddedBundles;
    }
    
    public URL [] getResolvedClassPath() {
        return _resolvedClassPath;
    }
    
    public Set<String> getResolvedContents() {
        return _resolvedContents;
    }
    
    /**
     * Resolves resolvedClassPath and resolvedContents
     */
    public static void resolve(List<? extends WorkingBundleInfo> infos)
        throws ConfigurationException
    {
        for ( WorkingBundleInfo info : infos ) {
            info._resolvedClassPath = null;
            info._resolvedContents = null;
        }
        //keep this around since it still verifies uniqueness
        //of bundles. just don't need the mapping anymore
        buildBundleMapping(infos);
        resolveClassPath(infos);
    }
    
    /**
     * Second pass - ensure bundle keys are unique
     */
    private static Map<BundleKey,? extends WorkingBundleInfo> 
    buildBundleMapping(List<? extends WorkingBundleInfo> working)
        throws ConfigurationException {
        Map<BundleKey,WorkingBundleInfo> parsed =
            new HashMap<BundleKey,WorkingBundleInfo>();
        for (WorkingBundleInfo info : working) {
            
            BundleKey key = new BundleKey(info._manifest.getBundleName(),
                    info._manifest.getBundleVersion());
            if ( parsed.containsKey(key) ) {
                String message = 
                    "There is more than one bundle with the same name+version"+
                    ": "+key;
                throw new ConfigurationException(message);
            }
            parsed.put(key, info);
        }
        return parsed;
    }
    
    /**
     * Third pass - populate resolvedClassPath and resolvedContents
     */
    private static void resolveClassPath(List<? extends WorkingBundleInfo> infos) 
    throws ConfigurationException {
        for (WorkingBundleInfo info : infos) {
            List<URL> urls = new ArrayList<URL>();
            Set<String> contents = new HashSet<String>();
            //this must go first, before the embedded bundles classpaths
            urls.addAll(info.getImmediateClassPath());
            contents.addAll(info.getImmediateBundleContents());
            resolveClassPath(info.getEmbeddedBundles());
            for (WorkingBundleInfo embedded : info.getEmbeddedBundles()) {
                urls.addAll(Arrays.asList(embedded.getResolvedClassPath()));
                contents.addAll(embedded.getResolvedContents());
            }
            info._resolvedClassPath = urls.toArray(new URL[urls.size()]);
            info._resolvedContents = contents;
        }
    }


}
