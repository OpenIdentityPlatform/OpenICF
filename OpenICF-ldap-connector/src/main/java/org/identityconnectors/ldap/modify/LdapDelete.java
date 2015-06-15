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
 * "Portions Copyrighted 2014 ForgeRock AS"
 */
package org.identityconnectors.ldap.modify;

import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;

import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapModifyOperation;
import org.identityconnectors.ldap.GroupHelper.GroupMembership;
import org.identityconnectors.ldap.LdapAuthenticate;
import org.identityconnectors.ldap.search.LdapSearches;

public class LdapDelete extends LdapModifyOperation {

    private final ObjectClass oclass;
    private final OperationOptions options;
    private final Uid uid;
    
    private static final Log log = Log.getLog(LdapDelete.class);

    public LdapDelete(LdapConnection conn, ObjectClass oclass, Uid uid, OperationOptions options) {
        super(conn);
        this.oclass = oclass;
        this.options = options;
        this.uid = uid;
    }

    public void execute() {
        String entryDN = escapeDNValueOfJNDIReservedChars(LdapSearches.getEntryDN(conn, oclass, uid));
        LdapContext runAsContext = null;
        
        if (StringUtil.isNotBlank(options.getRunAsUser())) {
            String dn = new LdapAuthenticate(conn, oclass, options.getRunAsUser(), options).getDn();
            runAsContext = conn.getRunAsContext(dn, options.getRunWithPassword());
        }
        
        if (conn.getConfiguration().isMaintainLdapGroupMembership()) {
            List<String> ldapGroups = groupHelper.getLdapGroups(entryDN);
            groupHelper.removeLdapGroupMemberships(entryDN, ldapGroups, runAsContext);
        }

        if (conn.getConfiguration().isMaintainPosixGroupMembership()) {
            PosixGroupMember posixMember = new PosixGroupMember(entryDN);
            Set<GroupMembership> memberships = posixMember.getPosixGroupMemberships();
            groupHelper.removePosixGroupMemberships(memberships, runAsContext);
        }

        log.ok("Deleting LDAP entry {0}", entryDN);
        try {
            if (runAsContext == null) {
                conn.getInitialContext().destroySubcontext(entryDN);
            }
            else {
                runAsContext.destroySubcontext(entryDN);
                }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
        finally {
            if (runAsContext != null) {
                try {
                    runAsContext.close();
                } catch (NamingException ex) {
                }
            }
        }
    }
}