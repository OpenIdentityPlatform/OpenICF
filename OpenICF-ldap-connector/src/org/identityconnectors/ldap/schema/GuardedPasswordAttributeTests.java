/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.ldap.schema;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.ldap.schema.GuardedPasswordAttribute.Accessor;
import org.junit.Test;

public class GuardedPasswordAttributeTests {

    @Test
    public void testAccess() throws NamingException {
        final String PASSWORD = "\u011b\u0161\u010d\u0159\u017e\u00fd\u00e1\u00ed\u00e9"; // Czech characters ;-)

        GuardedPasswordAttribute pwdAttr = GuardedPasswordAttribute.create("userPassword", new GuardedString(PASSWORD.toCharArray()));
        final Attribute[] attribute = { null };

        pwdAttr.access(new Accessor() {
            public void access(Attribute passwordAttribute) {
                assertEquals("userPassword", passwordAttribute.getID());
                try {
                    assertEquals(PASSWORD, new String((byte[]) passwordAttribute.get(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                } catch (NamingException e) {
                    throw new RuntimeException(e);
                }
                attribute[0] = passwordAttribute;
            }
        });
        assertEquals(1, attribute[0].size());
        byte[] value = (byte[]) attribute[0].get();
        for (int i = 0; i < value.length; i++) {
            assertEquals(0, value[i]);
        }
    }
    
    @Test
    public void testEmpty() {
        GuardedPasswordAttribute pwdAttr = GuardedPasswordAttribute.create("userPassword");
        pwdAttr.access(new Accessor() {
            public void access(Attribute passwordAttribute) {
                assertEquals(0, passwordAttribute.size());
            }
        });
    }
}
