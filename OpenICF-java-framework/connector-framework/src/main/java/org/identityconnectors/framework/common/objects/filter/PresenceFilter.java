/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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

package org.identityconnectors.framework.common.objects.filter;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;

/**
 * A PresenceFilter determines if the attribute provided is present in the
 * {@link ConnectorObject}
 *
 * @since 1.5
 */
public class PresenceFilter implements Filter {

    private final String name;

    public PresenceFilter(String attributeName) {
        this.name = attributeName;
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("Attribute name not be null!");
        }
    }

    /**
     * Name of the attribute to find in the {@link ConnectorObject}.
     */
    public String getName() {
        return name;
    }

    /**
     * Determines if the attribute provided is present in the
     * {@link ConnectorObject}.
     */
    public boolean accept(ConnectorObject obj) {
        return obj.getAttributeByName(name) != null;
    }

    public <R, P> R accept(FilterVisitor<R, P> v, P p) {
        return v.visitExtendedFilter(p, this);
    }
}
