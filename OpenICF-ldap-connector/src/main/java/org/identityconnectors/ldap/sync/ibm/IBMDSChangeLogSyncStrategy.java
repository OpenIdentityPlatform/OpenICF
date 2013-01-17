/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.identityconnectors.ldap.sync.ibm;

import static org.identityconnectors.common.CollectionUtil.isEmpty;
import static org.identityconnectors.ldap.LdapUtil.quietCreateLdapName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ldap.LdapName;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.sync.sunds.SunDSChangeLogSyncStrategy;

/**
 * A NAME does ...
 * 
 * @author Laszlo Hordos
 */
public class IBMDSChangeLogSyncStrategy extends SunDSChangeLogSyncStrategy {

    private static final Log logger = Log.getLog(IBMDSChangeLogSyncStrategy.class);

    public IBMDSChangeLogSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        super(conn, oclass);
    }

    protected boolean filterOutByModifiersNames(Map<String, List<Object>> changes) {
        Set<LdapName> filter = conn.getConfiguration().getModifiersNamesToFilterOutAsLdapNames();
        if (filter.isEmpty()) {
            logger.ok("Filtering by modifiersName disabled");
            return false;
        }
        List<?> modifiersNameValues = changes.get("ibm-changeInitiatorsName");
        if (isEmpty(modifiersNameValues)) {
            logger.ok("Not filtering by modifiersName because not set for this entry");
            return false;
        }
        LdapName modifiersName = quietCreateLdapName(modifiersNameValues.get(0).toString());
        return filter.contains(modifiersName);
    }

}
