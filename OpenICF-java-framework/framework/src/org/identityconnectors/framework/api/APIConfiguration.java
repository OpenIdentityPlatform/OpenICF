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
package org.identityconnectors.framework.api;

import java.util.Set;

import org.identityconnectors.common.pooling.ObjectPoolConfiguration;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SearchOp;


/**
 * Interface to show the configuration properties from both the SPI/API based on
 * the {@link Connector} makeup. Before this is passed into the
 * {@link ConnectorFacadeFactory} one must call {@link #getConfigurationProperties()}
 * and configure accordingly.
 */
public interface APIConfiguration {

    /**
     * Gets instance of the configuration properties. These are initialized
     * to their default values based on meta information. Caller can then 
     * modify the properties as needed.
     */
    public ConfigurationProperties getConfigurationProperties();

    /**
     * Determines if this {@link Connector} uses the framework's connector
     * pooling.
     * 
     * @return true iff the {@link Connector} uses the framework's connector
     *         pooling feature.
     */
    boolean isConnectorPoolingSupported();

    /**
     * Gets the connector pooling configuration. This is initialized to 
     * the default values. Caller can then 
     * modify the properties as needed.
     */
    ObjectPoolConfiguration getConnectorPoolConfiguration();

        
    // =======================================================================
    // Operational Support Set
    // =======================================================================
    /**
     * Get the set of operations that this {@link ConnectorFacade} will support.
     */
    Set<Class<? extends APIOperation>> getSupportedOperations();


    // =======================================================================
    // Framework Configuration..
    // =======================================================================
    /**
     * Sets the timeout value for the operation provided. 
     * 
     * @param operation
     *            particular operation that requires a timeout.
     * @param timeout
     *            milliseconds that the operation will wait in order to
     *            complete. Values less than or equal to zero are considered to
     *            disable the timeout property.
     */
    void setTimeout(Class<? extends APIOperation> operation, int timeout);

    /**
     * Gets the timeout in milliseconds based on the operation provided.
     * 
     * @param operation
     *            particular operation to get a timeout for.
     * @return milliseconds to wait for an operation to complete before throwing
     *         an error.
     */
    int getTimeout(Class<? extends APIOperation> operation);

    /**
     * Sets the size of the buffer for {@link Connector} the support
     * {@link SearchOp} and what the results of the producer buffered.
     * 
     * @param size
     *            default is 100, if size is set to zero or less will disable
     *            buffering.
     */
    void setProducerBufferSize(int size);

    /**
     * Get the size of the buffer.
     */
    int getProducerBufferSize();

}
