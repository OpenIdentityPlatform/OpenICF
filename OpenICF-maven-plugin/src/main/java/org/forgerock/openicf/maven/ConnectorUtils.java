/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.maven;

import java.lang.reflect.Array;
import java.util.Locale;
import java.util.Stack;

import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

/**
 * A ConnectorUtils class provide helper methods to use in the Velocity
 * template.
 *
 * @author Laszlo Hordos
 */
public class ConnectorUtils {

    /**
     * Convert the encapsulated value of the {@code property}.
     *
     * @param property
     *            source ConfigurationProperty.
     * @return non null string representing the property value.
     */
    public static String defaultValue(ConfigurationProperty property) {
        String value = "";
        if (null == property.getValue()) {
            value = "null";
        } else if (property.getType().isArray()) {
            int length = Array.getLength(property.getValue());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < length; i++) {
                if (i > 0 && i < length) {
                    sb.append(",\n");
                }
                Object item = Array.get(property.getValue(), i);
                if (item instanceof GuardedString || item instanceof GuardedByteArray) {
                    item = "*****";
                }
                sb.append("'").append(String.valueOf(item)).append("'");
            }
            value = sb.append("]").toString();
        } else {
            value = property.getValue().toString();
        }
        return value;
    }


    public static String safeNCName(String value){
        return value.toLowerCase(Locale.US).replaceAll("\\s+","-");
    }

    /**
     * Convenience method to build the display name key for an object class.
     *
     * @return The display name key.
     */
    public static String getDisplayName(ConnectorInfo ci, ObjectClassInfo info, String dfl) {
        if (null != info && null != ci && null != ci.getMessages()) {
            return ci.getMessages().format(
                    "MESSAGE_OBJECT_CLASS_" + info.getType().toUpperCase(Locale.US), dfl);
        }
        return dfl;
    }

}
