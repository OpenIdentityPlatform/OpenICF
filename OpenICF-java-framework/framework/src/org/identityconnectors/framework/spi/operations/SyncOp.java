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

import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;


/**
 * Receive synchronization events from the resource.
 * 
 * @see SyncApiOp
 */
public interface SyncOp extends SPIOperation {
    /**
     * Perform a synchronization.
     * 
     * @param objClass
     *            The object class to synchronize. Must not be null.
     * @param token
     *            The token representing the last token from the previous sync.
     *            Should be null if this is the first sync for the given
     *            resource. 
     * @param handler
     *            The result handler Must not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     */
    public void sync(ObjectClass objClass, SyncToken token,
            SyncResultsHandler handler,
            OperationOptions options);
    
    /**
     * Returns the token corresponding to the latest sync delta.
     * This is to support applications that may wish to sync starting
     * "now".
     * @return The latest token or null if there is no sync data.
     */
    public SyncToken getLatestSyncToken();
}
