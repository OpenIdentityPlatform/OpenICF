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

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

/**
 * Authenticate an object based on their unique identifier and password.
 */
public interface AuthenticateOp extends SPIOperation {

    /**
     * Simple authentication with two parameters presumed to be user name and
     * password. The {@link Connector} developer is expected to attempt to
     * authenticate these credentials natively. If the authentication fails the
     * developer should throw a type of {@link RuntimeException} either
     * {@link IllegalArgumentException} or if a native exception is available
     * and if its of type {@link RuntimeException} simple throw it. If the
     * native exception is not a {@link RuntimeException} wrap it in one and
     * throw it. This will provide the most detail for logging problem and
     * failed attempts.
     * <p>
     * The developer is of course encourage to try and throw the most
     * informative exception as possible. In that regards there are several
     * exceptions provided in the exceptions package. For instance one of the
     * most common is {@link InvalidPasswordException}.
     * 
     * @param username
     *            the name based credential for authentication.
     * @param password
     *            the password based credential for authentication.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     * @return Uid The uid of the account that was used to authenticate
     * @throws RuntimeException
     *             iff native authentication fails. If a native exception if
     *             available attempt to throw it.
     */
    Uid authenticate(final String username, final GuardedString password, final OperationOptions options);
}
