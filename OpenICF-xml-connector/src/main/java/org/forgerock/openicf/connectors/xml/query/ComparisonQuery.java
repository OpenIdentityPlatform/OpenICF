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
 * Portions Copyrighted 2026 3A Systems, LLC
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml.query;

import org.forgerock.openicf.connectors.xml.query.abstracts.QueryPart;


public class ComparisonQuery implements QueryPart {

    private final String name;
    private final String operator;
    private final String value;
    private final String literal;

    public ComparisonQuery(String name, String operator, String literal) {
        this(name, operator, null, literal);
    }

    public ComparisonQuery(String name, String operator, String value, String literal) {
        this.name = name;
        this.operator = operator;
        this.value = value;
        this.literal = literal;
    }

    @Override
    public String getExpression() {
        return String.format("%s %s %s", this.name, this.operator, this.literal);
    }
}
