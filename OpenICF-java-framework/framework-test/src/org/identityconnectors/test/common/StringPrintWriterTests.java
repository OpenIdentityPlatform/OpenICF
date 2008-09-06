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
package org.identityconnectors.test.common;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringPrintWriter;
import org.junit.Test;


import static org.junit.Assert.*;

/**
 * Simple tests to check that its functioning.
 */
public class StringPrintWriterTests {

    String[] DATA = new String[] { "Some random text to use!",
            "Some more text to use wee!", "Even more text to use woo hoo!" };

    @Test
    public void getString() {
        StringBuilder bld = new StringBuilder();
        StringPrintWriter wrt = new StringPrintWriter();
        for (String data : DATA) {
            bld.append(data);
            wrt.print(data);
        }
        // check that it works..
        assertEquals(bld.toString(), wrt.getString());
        wrt.clear();
        assertEquals(wrt.getString(), "");
    }

    @Test
    public void getReader() {
        StringWriter wrt = new StringWriter();
        StringPrintWriter pwrt = new StringPrintWriter();
        for (String data : DATA) {
            wrt.append(data);
            pwrt.append(data);
        }
        // get the string of the reader..
        String actual = IOUtil.readerToString(pwrt.getReader());
        assertEquals(wrt.toString(), actual);
    }

    @Test
    public void println() {
        StringWriter swrt = new StringWriter();
        PrintWriter wrt = new PrintWriter(swrt);
        StringPrintWriter pwrt = new StringPrintWriter();
        for (String data : DATA) {
            wrt.println(data);
            pwrt.println(data);
        }
        assertEquals(swrt.toString(), pwrt.getString());
    }
    
    public void printlnArray() {
        StringWriter swrt = new StringWriter();
        PrintWriter wrt = new PrintWriter(swrt);
        StringPrintWriter pwrt = new StringPrintWriter();
        for (String data : DATA) {
            wrt.println(data);
        }
        pwrt.println(DATA);
        assertEquals(swrt.toString(), pwrt.getString());
    }

    @Test
    public void printArray() {
        StringWriter swrt = new StringWriter();
        PrintWriter wrt = new PrintWriter(swrt);
        StringPrintWriter pwrt = new StringPrintWriter();
        for (String data : DATA) {
            wrt.print(data);
        }
        pwrt.print(DATA);
        assertEquals(swrt.toString(), pwrt.getString());
    }

    
}
