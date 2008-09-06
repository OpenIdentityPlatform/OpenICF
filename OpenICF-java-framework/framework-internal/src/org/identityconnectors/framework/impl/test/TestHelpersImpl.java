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
package org.identityconnectors.framework.impl.test;

import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.impl.api.APIConfigurationImpl;
import org.identityconnectors.framework.impl.api.ConfigurationPropertiesImpl;
import org.identityconnectors.framework.impl.api.ConnectorMessagesImpl;
import org.identityconnectors.framework.impl.api.local.JavaClassProperties;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.impl.api.local.operations.SearchImpl;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.test.TestHelpers;


public class TestHelpersImpl extends TestHelpers {
    
    /**
     * Method for convenient testing of local connectors. 
     */
    @Override
    protected APIConfiguration createTestConfigurationImpl(Class<? extends Connector> clazz,
            Configuration config) {
        LocalConnectorInfoImpl info = new LocalConnectorInfoImpl();
        info.setConnectorConfigurationClass(config.getClass());
        info.setConnectorClass(clazz);
        info.setConnectorDisplayNameKey("DUMMY_DISPLAY_NAME");
        info.setConnectorKey(
               new ConnectorKey(clazz.getName()+".bundle",
                "1.0",
                clazz.getName()));
        info.setMessages(new ConnectorMessagesImpl());
        try {
            APIConfigurationImpl rv = new APIConfigurationImpl();
            rv.setConnectorPoolingSupported(
                    PoolableConnector.class.isAssignableFrom(clazz));
            ConfigurationPropertiesImpl properties =
                JavaClassProperties.createConfigurationProperties(config);
            rv.setConfigurationProperties(properties);
            rv.setConnectorInfo(info);
            rv.setSupportedOperations(
                    FrameworkUtil.getDefaultSupportedOperations(clazz));
            info.setDefaultAPIConfiguration(
                    rv);
            return rv;
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }        
        
    /**
     * Performs a raw, unfiltered search at the SPI level,
     * eliminating duplicates from the result set.
     * @param search The search SPI
     * @param oclass The object class - passed through to
     * connector so it may be null if the connecor
     * allowing it to be null. (This is convenient for
     * unit tests, but will not be the case in general)
     * @param filter The filter to search on
     * @param handler The result handler
     * @param options The options - may be null - will
     *  be cast to an empty OperationOptions
     */
    @Override
    protected void searchImpl(SearchOp<?> search,
            final ObjectClass oclass, 
            final Filter filter, 
            ResultsHandler handler,
            OperationOptions options) {
        if ( options == null ) {
            options = new OperationOptionsBuilder().build();
        }
        SearchImpl.rawSearch(search, oclass, filter, handler, options);
    }

}
