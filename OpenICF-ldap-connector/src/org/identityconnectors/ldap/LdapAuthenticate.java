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
package org.identityconnectors.ldap;

import java.util.List;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapAuthenticate {

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final String username;
    private final GuardedString password;

    public LdapAuthenticate(LdapConnection conn, ObjectClass oclass, String username, GuardedString password) {
        this.conn = conn;
        this.oclass = oclass;
        this.username = username;
        this.password = password;
    }

    public Uid execute() {
        List<ConnectorObject> objects = LdapSearches.findObjects(conn, oclass, username);
        if (objects.isEmpty()) {
            throw new InvalidCredentialException("No user with this name was found");
        }

        for (ConnectorObject object : objects) {
            String bindDN = AttributeUtil.getAsStringValue(object.getAttributeByName("entryDN"));
            conn.authenticate(bindDN, password);
            return object.getUid();
        }

        assert false : "Should never get here";
        return null;
    }
}
