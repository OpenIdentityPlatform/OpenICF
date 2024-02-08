/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.misc.crest;

import org.identityconnectors.framework.common.objects.Attribute;

/**
 * A VisitorParameter used to get the CREST field name and the String value.
 *
 * @author Laszlo Hordos
 */
public interface VisitorParameter {

    /**
     * Translates the attribute name from ICF to JSON.
     *
     * @param name
     * @return the new name.
     */
    String translateName(String name);

    /**
     * Converts the attribute value from ICF to JSON.
     *
     * The ICF Attribute value is a {@code List < Object >} which has to be
     * converts to JSON Type. Number, Boolean, String, Null, Map or Array of
     * these.
     *
     * @param attribute
     * @return the new value. It could be Null, String, Boolean or Number.
     */
    Object convertValue(Attribute attribute);
}
