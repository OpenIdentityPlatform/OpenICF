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
package org.identityconnectors.framework.impl.api;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.common.objects.ConnectorMessages;


/**
 * Implementation of ConnectorMessages
 */
public class ConnectorMessagesImpl implements ConnectorMessages {

    /**
     * NOTE: I am choosing not to use ResourceBundle here because,
     * for the remote case, we will not be able to use
     * ResourceBundle. Rather than have one implementation for
     * C# and one for Java, I have a single implementation
     * for both.
     */
    
    private Map<Locale,Map<String,String>> 
        _catalogs = new HashMap<Locale,Map<String,String>>();
    
    public ConnectorMessagesImpl() {
        
    }
    
    public String format(String key, String dflt, Object... args) {
        if ( key == null ) {
            return dflt;
        }
        
        Locale locale = CurrentLocale.get();
        if ( locale == null ) {
            locale = Locale.getDefault();
        }
        
        if ( dflt == null ) {
            dflt = key;
        }
        
        
        //first look for most-specific catalog
        Map<String,String> catalog =
            _catalogs.get(locale);
        //now look for language+country
        if ( catalog == null ) {
            locale = new Locale(locale.getLanguage(),
                    locale.getCountry());
            catalog =
                _catalogs.get(locale);
        }
        //now look for language
        if ( catalog == null ) {
            locale = new Locale(locale.getLanguage());
            catalog =
                _catalogs.get(locale);
        }
        //otherwise use the default catalog
        if ( catalog == null ) {
            locale = new Locale("");
            catalog =
                _catalogs.get(locale);
        }
        String message = null;
        if ( catalog != null ) {
            message = catalog.get(key);
        }
        if ( message == null ) {
            message = getFrameworkMessage(locale,key);
        }
        if ( message == null ) {
            return dflt;
        }
        else {
            MessageFormat formater =
                new MessageFormat(message,locale);
            return formater.format(args,new StringBuffer(),null).toString();
        }
    }
    
    private String getFrameworkMessage(Locale locale, String key) {
        final String baseName =
            ConnectorMessagesImpl.class.getPackage().getName()+".Messages";
        //this will throw if not there, but there should always be
        //at least a bundle
        final ResourceBundle bundle =
            ResourceBundle.getBundle(baseName,locale);
        try {
            return bundle.getString(key);
        }
        catch (MissingResourceException e) {
            return null;
        }
    }
        
    public Map<Locale,Map<String,String>> getCatalogs() {
        return _catalogs;
    }
    
    public void setCatalogs(Map<Locale,Map<String,String>> catalogs) {
        if ( catalogs == null ) {
            catalogs = new HashMap<Locale,Map<String,String>>();
        }
        _catalogs = catalogs;        
    }

}
