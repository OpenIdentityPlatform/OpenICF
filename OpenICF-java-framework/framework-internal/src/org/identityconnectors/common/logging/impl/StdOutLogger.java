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
package org.identityconnectors.common.logging.impl;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Date;

import org.identityconnectors.common.StringPrintWriter;
import org.identityconnectors.common.logging.LogSpi;
import org.identityconnectors.common.logging.Log.Level;


/**
 * Standard out logger. It logs all messages to STDOUT. The method
 * {@link LogSpi#isLoggable(Class, Level)} will always return true so currently
 * logging is not filtered.
 * 
 * @author Will Droste
 * @version $Revision: 1.5 $
 * @since 1.0
 */
public class StdOutLogger implements LogSpi {

    private static final String PATTERN = "Thread Id: {0}\tTime: {1}\tClass: {2}\tMethod: {3}\tLevel: {4}\tMessage: {5}";
    /**
     * Insures there is only one MessageFormat per thread since MessageFormat is
     * not thread safe.
     */
    private static final ThreadLocal<MessageFormat> _messageFormatHandler = new ThreadLocal<MessageFormat>() {
        @Override
        protected MessageFormat initialValue() {
            return new MessageFormat(PATTERN);
        }
    };

    /**
     * Logs the thread id, date, class, level, message, and optionally exception
     * stack trace to standard out.
     * 
     * @see LogSpi#log(Class, String, Level, String, Throwable)
     */
    public void log(Class<?> clazz, String methodName, Level level,
            String message, Throwable ex) {
        Object[] args = new Object[] { Thread.currentThread().getId(),
                new Date(), clazz.getName(), methodName, level, message };
        PrintStream out = Level.ERROR.equals(level) ? System.err : System.out;
        String msg = _messageFormatHandler.get().format(args);
        out.println(msg);
        if (ex != null) {
            StringPrintWriter wrt = new StringPrintWriter();
            ex.printStackTrace(wrt);
            out.print(wrt.getString());
        }
    }

    /**
     * Always returns true.
     */
    public boolean isLoggable(Class<?> clazz, Level level) {
        return true;
    }
}
