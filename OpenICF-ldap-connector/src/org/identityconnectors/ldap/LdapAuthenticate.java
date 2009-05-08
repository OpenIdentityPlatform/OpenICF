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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection.AuthenticationResult;
import org.identityconnectors.ldap.LdapConnection.AuthenticationResultType;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapAuthenticate {

    private final LdapConnection conn;
    private final ObjectClass oclass;
    private final String username;
    private final GuardedString password;
    private final OperationOptions options;

    public LdapAuthenticate(LdapConnection conn, ObjectClass oclass, String username, GuardedString password, OperationOptions options) {
        this.conn = conn;
        this.oclass = oclass;
        this.username = username;
        this.password = password;
        this.options = options;
    }

    public Uid execute() {
        List<String> userNameAttrs = getUserNameAttributes();
        Map<ConnectorObject, AuthenticationResult> object2SuccessAuthn = new HashMap<ConnectorObject, AuthenticationResult>();
        int matchedObjectCount = 0;

        ctxLoop: for (String baseContext : conn.getConfiguration().getBaseContexts()) {
            /* attrLoop: */ for (String userNameAttr : userNameAttrs) {
                Attribute attr = AttributeBuilder.build(userNameAttr, username);
                List<ConnectorObject> objects = LdapSearches.findObjects(conn, oclass, baseContext, attr, "entryDN");
                matchedObjectCount += objects.size();

                for (ConnectorObject object : objects) {
                    String entryDN = object.getAttributeByName("entryDN").getValue().get(0).toString();
                    AuthenticationResult authnResult = conn.authenticate(entryDN, password);

                    if (isSuccess(authnResult)) {
                        object2SuccessAuthn.put(object, authnResult);
                        if (object2SuccessAuthn.size() > 1) {
                            // We will throw an exception below for more than one authenticated objects,
                            // so it is useless to try for more.
                            break ctxLoop;
                        }
                        // Does not make a lot of sense to stop when having authenticated
                        // the first user for the current attribute, but that's what the adapter does.
                        // break attrLoop;
                    }
                }
            }
        }

        if (object2SuccessAuthn.isEmpty()) {
            switch (matchedObjectCount) {
                case 0:
                    throw new ConnectorSecurityException(conn.format("noUserMatched", null, username));
                case 1:
                    throw new InvalidCredentialException(conn.format("authenticationFailed", null, username));
                default:
                    throw new ConnectorSecurityException(conn.format("moreThanOneUserMatched", null, username));
            }
        } else if (object2SuccessAuthn.size() > 1) {
            throw new ConnectorSecurityException(conn.format("moreThanOneUserMatchedWithPassword", null, username));
        }

        Entry<ConnectorObject, AuthenticationResult> entry = object2SuccessAuthn.entrySet().iterator().next();
        ConnectorObject object = entry.getKey();
        AuthenticationResult authnResult = entry.getValue();
        try {
            authnResult.propagate();
        } catch (PasswordExpiredException e) {
            e.initUid(object.getUid());
            throw e;
        }
        // AuthenticationResult did not throw an exception, so this authentication was successful.
        return object.getUid();
    }

    private List<String> getUserNameAttributes() {
        String[] result = LdapConstants.getLdapUidAttributes(options);
        if (result != null && result.length > 0) {
            return Arrays.asList(result);
        }
        return conn.getSchemaMapping().getUserNameLdapAttributes(oclass);
    }

    private static boolean isSuccess(AuthenticationResult authResult) {
        // We consider PASSWORD_EXPIRED to be a success, because it means the credentials were right.
        AuthenticationResultType type = authResult.getType();
        return type.equals(AuthenticationResultType.SUCCESS) || type.equals(AuthenticationResultType.PASSWORD_EXPIRED);
    }
}
