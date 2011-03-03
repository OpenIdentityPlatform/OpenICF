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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;


public class QueryImpl implements Query {

    
    private static final QueryPart AND = new SimplePart(" and ");
    private static final QueryPart OR = new SimplePart(" or ");

    private QueryPart mainPart;
    private LinkedList<QueryPart> parts;
    
    public QueryImpl() {
        this.parts = new LinkedList<QueryPart>();
    }

    public void set(QueryPart part) {
        if (mainPart != null && parts.contains(mainPart)) {
            int index = parts.indexOf(mainPart);

            parts.remove(mainPart);
            parts.add(index, part);
        }
        else {
            parts.add(part);
        }

        mainPart = part;
    }

    public Iterator<QueryPart> iterator() {
        return parts.iterator();
    }

    public void and(Query part) {
        parts.addLast(AND);

        for (QueryPart p : part.getParts()) {
            parts.addLast(p);
        }
    }

    public Collection<QueryPart> getParts() {
        return parts;
    }

    public void or(Query part) {
        parts.addLast(OR);

        for (QueryPart p : part.getParts()) {
            parts.addLast(p);
        }
    }

    static class SimplePart implements QueryPart {

        private String value;

        public SimplePart(String value) {
            this.value = value;
        }

        public String getExpression() {
            return this.value;
        }
    }
}