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
package org.identityconnectors.ldap.sync.sunds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.identityconnectors.ldap.sync.sunds.LdifParser.ChangeSeparator;
import org.identityconnectors.ldap.sync.sunds.LdifParser.Line;
import org.identityconnectors.ldap.sync.sunds.LdifParser.NameValue;
import org.identityconnectors.ldap.sync.sunds.LdifParser.Separator;
import org.junit.Test;

public class LdifParserTests {

    @Test
    public void testSimple() {
        String ldif =
                "changeType: mo\n" +
        		" dify\n" +
        		"rep\n" +
        		" lace: cn\n" +
        		"cn: Na\n" +
        		" me 1\n" +
        		"cn: Name 2\n" +
        		"-\n" +
        		"\n" +
        		"\n" +
        		"c\n" +
        		" hang\n" +
        		" eType: add\n" +
        		"add: \n" +
        		" uid\n" +
        		"uid: \n" +
        		" 1\n" +
        		"\n" +
        		"changeType: delete\n";

        LdifParser parser = new LdifParser(ldif);
        Iterator<Line> lines = parser.iterator();
        assertLineEquals(lines.next(), new NameValue("changeType", "modify"));
        assertLineEquals(lines.next(), new NameValue("replace", "cn"));
        assertLineEquals(lines.next(), new NameValue("cn", "Name 1"));
        assertLineEquals(lines.next(), new NameValue("cn", "Name 2"));
        assertTrue(lines.next() instanceof Separator);
        // LdifParser merges multiple change separators into one.
        assertTrue(lines.next() instanceof ChangeSeparator);
        assertLineEquals(lines.next(), new NameValue("changeType", "add"));
        assertLineEquals(lines.next(), new NameValue("add", "uid"));
        assertLineEquals(lines.next(), new NameValue("uid", "1"));
        assertTrue(lines.next() instanceof ChangeSeparator);
        assertLineEquals(lines.next(), new NameValue("changeType", "delete"));
        assertTrue(lines.next() instanceof ChangeSeparator);
    }

    @Test
    public void testChangeSeparatorAsLastLine() {
        String ldif =
            "changeType: modify";

        LdifParser parser = new LdifParser(ldif);
        Iterator<Line> lines = parser.iterator();
        assertLineEquals(lines.next(), new NameValue("changeType", "modify"));
        assertTrue(lines.next() instanceof ChangeSeparator);
    }

    private static void assertLineEquals(Line expected, NameValue value) {
        assertTrue(expected instanceof NameValue);
        NameValue expectedValue = (NameValue) expected;
        assertEquals(expectedValue.getName(), value.getName());
        assertEquals(expectedValue.getValue(), value.getValue());
    }
}
