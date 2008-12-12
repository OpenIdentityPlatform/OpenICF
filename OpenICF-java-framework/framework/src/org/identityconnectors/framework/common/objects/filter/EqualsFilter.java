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
package org.identityconnectors.framework.common.objects.filter;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;

public final class EqualsFilter extends AttributeFilter {

    /**
     * Determines if the attribute inside the {@link ConnectorObject} is equal
     * to the {@link Attribute} provided.
     */
    public EqualsFilter(Attribute attr) {
        super(attr);
    }

    /**
     * Determines if the attribute exists in the {@link ConnectorObject} and if
     * its equal to the one provided.
     * 
     * @see Filter#accept(ConnectorObject)
     */
    public boolean accept(ConnectorObject obj) {
        boolean ret = false;
        Attribute thisAttr = getAttribute();
        Attribute attr = obj.getAttributeByName(thisAttr.getName());
        if (attr != null) {
            ret = thisAttr.equals(attr);
        }
        return ret;
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        bld.append("EQUALS: ").append(getAttribute());
        return bld.toString();
    }
}
