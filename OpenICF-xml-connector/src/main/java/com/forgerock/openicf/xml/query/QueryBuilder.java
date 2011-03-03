/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package com.forgerock.openicf.xml.query;

import com.forgerock.openicf.xml.query.abstracts.QueryPart;
import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.XMLHandlerImpl;
import com.forgerock.openicf.xml.util.NamespaceLookupUtil;
import java.util.Iterator;
import org.identityconnectors.framework.common.objects.ObjectClass;

public class QueryBuilder {

    private Query query;
    private String selectPart;
    private String wherePart;
    private String returnPart;

    public QueryBuilder(Query query, ObjectClass objClass) {
        this.query = query;
        createSelectPart(objClass);
        wherePart = "where ";
        createReturnPart();

        if (query != null) {
            processQuery();
        }
    }

    private void createSelectPart(ObjectClass objClass) {
        StringBuilder sb = new StringBuilder();
        appendIcfNamespace(sb);
        appendRINamespace(sb);
        appendFLWORExpression(sb, objClass);
        this.selectPart = sb.toString();
    }

    private void createReturnPart() {
        this.returnPart = "return $x";
    }

    private void processQuery() {
        Iterator<QueryPart> it = query.iterator();

        while (it.hasNext()) {
            QueryPart part = it.next();
            wherePart += part.getExpression();
        }
    }

    @Override
    public String toString() {
        if (query == null || query.getParts().isEmpty()) {
            return String.format("%s %s", selectPart, returnPart);
        } else {
            return String.format("%s %s %s", selectPart, wherePart, returnPart);
        }
    }

    private void appendIcfNamespace(StringBuilder sb) {
        sb.append("declare namespace ");
        sb.append(XMLHandlerImpl.ICF_NAMESPACE_PREFIX);
        sb.append(" = \"").append(NamespaceLookupUtil.INSTANCE.getIcfNamespace()).append("\"; ");
    }

    private void appendRINamespace(StringBuilder sb) {
        sb.append("declare namespace ");
        sb.append(XMLHandlerImpl.RI_NAMESPACE_PREFIX);
        sb.append(" = \"").append(NamespaceLookupUtil.INSTANCE.getRINamespace()).append("\"; ");
    }

    private void appendFLWORExpression(StringBuilder sb, ObjectClass objClass) {
        sb.append("for $x in /").append(XMLHandlerImpl.ICF_NAMESPACE_PREFIX).append(":").append(
                XMLHandlerImpl.ICF_CONTAINER_TAG).append("//").append(XMLHandlerImpl.RI_NAMESPACE_PREFIX).append(":").append(objClass.getObjectClassValue());
    }
}
