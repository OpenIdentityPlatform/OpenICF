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
package org.identityconnectors.framework.common.exceptions;

/**
 * Base exception for the Connector framework.  
 * The Connector framework code throws only exceptions 
 * that extend <code>ConnectorException</code>.
 */
public class ConnectorException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public ConnectorException() {
        super();
    }

    /**
     * Sets a message for the {@link Exception}.
     *  
     * @param message
     *            passed to the {@link RuntimeException} message.
     */
    public ConnectorException(String message) {
        super(message);
    }

    /**
     * Sets the stack trace to the original exception, so this exception can
     * masquerade as the original only be a {@link RuntimeException}.
     * 
     * @param originalException
     *            the original exception adapted to {@link RuntimeException}.
     */
    public ConnectorException(Throwable originalException) {
        super(originalException);
    }

    /**
     * Sets the stack trace to the original exception, so this exception can
     * masquerade as the original only be a {@link RuntimeException}.
     * 
     * @param message
     * @param originalException
     *            the original exception adapted to {@link RuntimeException}.
     */
    public ConnectorException(String message, Throwable originalException) {
        super(message, originalException);
    }

    /**
     * Re-throw the original exception.
     * 
     * @throws Exception
     *             throws the original passed in the constructor.
     */
    public void rethrow() throws Throwable {
        throw (getCause() == null) ? this : getCause();
    }

    /**
     * If {@link Exception} parameter passed in is a {@link RuntimeException} it
     * is simply returned. Otherwise the {@link Exception} is wrapped in a
     * <code>ConnectorException</code> and returned.
     * 
     * @param ex
     *            Exception to wrap or cast and return.
     * @return a <code>RuntimeException</code> that either 
     *           <i>is</i> the specified exception
     *            or <i>contains</i> the specified exception. 
     */
    public static RuntimeException wrap(Throwable ex) {
        // make sure to just throw Errors don't return them..
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        // don't bother to wrap a exception that is already a runtime..
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new ConnectorException(ex);
    }
}
