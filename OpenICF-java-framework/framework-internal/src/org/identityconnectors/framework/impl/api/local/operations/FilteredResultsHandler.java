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
package org.identityconnectors.framework.impl.api.local.operations;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

public final class FilteredResultsHandler implements ResultsHandler {

    // =======================================================================
    // Fields
    // =======================================================================
    final ResultsHandler handler;
    final Filter filter;

    // =======================================================================
    // Constructors
    // =======================================================================
    /**
     * Filter chain for producers.
     * 
     * @param producer
     *            Producer to filter.
     * @param filter
     *            Filter to use to accept objects.
     */
    public FilteredResultsHandler(ResultsHandler handler, Filter filter) {
        // there must be a producer..
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null!");
        }
        this.handler = handler;
        // use a default pass through filter..
        this.filter = filter == null ? new PassThruFilter() : filter;
    }

    public boolean handle(ConnectorObject object) {
        if ( filter.accept(object) ) {
            return handler.handle(object);
        }
        else {
            return true;
        }
    }

    /**
     * Use a pass thru filter to use if a null filter is provided.
     */
    public static class PassThruFilter implements Filter {
        public boolean accept(ConnectorObject obj) {
            return true;
        }
    }

}
