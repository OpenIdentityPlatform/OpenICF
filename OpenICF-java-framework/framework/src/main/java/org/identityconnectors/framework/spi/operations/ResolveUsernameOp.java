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
package org.identityconnectors.framework.spi.operations;

import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Resolve an object to its {@link Uid} based on its username.
 */
public interface ResolveUsernameOp extends SPIOperation {

    /**
     * This is a companion to the simple {@link AuthenticateOp authentication} with two parameters 
     * presumed to be user name and password. The difference is that this
     * method does not try to authenticate the credentials; instead, it 
     * return the {@link Uid} of the username that would be authenticated.
     * <p>  
     * If the authentication fails the
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
     * @param objectClass The object class to use for authenticate.
     *            Will typically be an account. Will not be null.
     * @param username
     *            the username that would be authenticated. Will not be null.
     * @param options
     *            additional options that impact the way this operation is run.
     *            If the caller passes null, the framework will convert this into
     *            an empty set of options, so SPI need not worry
     *            about this ever being null.
     * @return Uid The uid of the account that would be authenticated.
     * @throws RuntimeException
     *             iff native authentication fails. If a native exception is
     *             available attempt to throw it.
     * @since 1.1
     */
    Uid resolveUsername(ObjectClass objectClass, final String username, final OperationOptions options);
}
