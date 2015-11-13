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

import java.util.Map;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.forgerock.json.JsonValue;

import groovy.json.JsonOutput;

/**
 * A self contained, repeatable entity that obtains its content from a {@link String}.
 *
 * @author Laszlo Hordos
 */
@NotThreadSafe
class JsonEntity extends StringEntity {

    /**
     * Creates a JsonEntity with the specified content.
     *
     * @param content
     *         content to be used. Not {@code null}.
     * @throws IllegalArgumentException
     *         if the source parameter is null
     */
    public JsonEntity(final Object content) {
        super(getJsonContent(content), ContentType.APPLICATION_JSON);
    }

    private static String getJsonContent(Object source) {

        Object value = null;
        if (source instanceof JsonValue) {
            value = ((JsonValue) source).getObject();
        } else {
            value = source;
        }
        if (value instanceof Map) {
            return JsonOutput.toJson((Map) value);
        } else if (value instanceof Number) {
            return JsonOutput.toJson((Number) value);
        } else if (value instanceof String) {
            return JsonOutput.toJson((String) value);
        } else if (value instanceof Boolean) {
            return JsonOutput.toJson((Boolean) value);
        }
        return JsonOutput.toJson(value);

    }
}
