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

package com.forgerock.openicf.xml.util;

import com.forgerock.openicf.xml.XMLHandlerImpl;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSSchema;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum NamespaceLookupUtil {

    INSTANCE;

    private Set<String> icfMap = new HashSet<String>();
    private String icfNamespace;
    private String riNamespace;

    public void initialize(XSSchema icfSchema, XSSchema riSchema) {

        icfNamespace = icfSchema.getTargetNamespace();
        riNamespace = riSchema.getTargetNamespace();

        Map<String, XSElementDecl> icfElementMap = icfSchema.getElementDecls();

        for (String elementName : icfElementMap.keySet()) {
            icfMap.add(elementName);
        }
    }

    public String getAttributeNamespace(String attrName) {
        if (icfMap.contains(attrName))
            return icfNamespace;
        else
            return riNamespace;
    }

    public String getAttributePrefix(String attrName) {
        if (icfMap.contains(attrName))
            return XMLHandlerImpl.ICF_NAMESPACE_PREFIX;
        else
            return XMLHandlerImpl.RI_NAMESPACE_PREFIX;
    }

    public String getIcfNamespace() {
        return icfNamespace;
    }

    public String getRINamespace() {
        return riNamespace;
    }
}
