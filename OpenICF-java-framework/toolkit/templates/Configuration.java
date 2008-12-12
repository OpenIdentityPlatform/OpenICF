/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
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
package <%= packageName %>;

import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.common.StringUtil;

/**
 * Implements the {@link Configuration} interface to provide all the necessary
 * parameters to initialize the $resourceName Connector.
 *
 * @author $userName
 * @version 1.0
 * @since 1.0
 */
public class <%= resourceName %>Configuration extends AbstractConfiguration {

    /*
     * Set up base configuration elements
     */
    private String exampleProperty = null;

    /**
     * Constructor
     */
    public <%= resourceName %>Configuration() {
        
    }
    
    /**
     * Accessor for the example property. Uses ConfigurationProperty annotation
     * to provide property metadata to the application.
     */
    @ConfigurationProperty(displayMessageKey="EXAMPLE_PROPERTY_DISPLAY", helpMessageKey="EXAMPLE_PROPERTY_HELP", confidential=false)
    public String getExampleProperty() {
        return exampleProperty;
    }
    
    /**
     * Setter for the example property.
     */
    public void setExampleProperty(String exampleProperty) {
        this.exampleProperty = exampleProperty;
    }
    
    /**
     * {@inheritDoc}
     */
    public void validate() {
        if(StringUtil.isBlank(exampleProperty)) {
            throw new IllegalArgumentException("ExampleProperty cannot be null or empty.");
        }
    }

}
