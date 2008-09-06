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
package org.identityconnectors.framework.spi.operations;

import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;

/**
 * Implement this interface to allow the Connector to search for resource
 * objects.
 * @param T The result type of the translator. 
 * @see AbstractFilterTranslator For more information
 */
public interface SearchOp<T> extends SPIOperation {
    
    /**
     * Creates a filter translator that will translate
     * a specified filter to the native filter. The
     * translated filters will be subsequently passed to
     * {@link #search(ObjectClass, Object, ResultsHandler)}
     * @param oclass The object class for the search. Will never be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     * @return A filter translator.
     */
    public FilterTranslator<T> createFilterTranslator(ObjectClass oclass, OperationOptions options);
    /**
     * This will be called by ConnectorFacade, once for each native query produced
     * by the FilterTranslator. If there is more than one query the results will
     * automatically be merged together and duplicates eliminated. NOTE
     * that this implies an in-memory data structure that holds a set of
     * Uids, so memory usage in the event of multiple queries will be O(N)
     * where N is the number of results. That is why it is important that
     * the FilterTranslator implement OR if possible.
     * @param oclass The object class for the search. Will never be null.
     * @param query The native query to run. A value of null means 'return everything for the given object class'.
     * @param handler
     *            Results should be returned to this handler
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     */
    public void executeQuery(ObjectClass oclass, T query, ResultsHandler handler, OperationOptions options);

}
