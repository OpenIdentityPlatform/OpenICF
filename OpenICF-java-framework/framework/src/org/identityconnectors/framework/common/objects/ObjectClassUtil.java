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
package org.identityconnectors.framework.common.objects;

/**
 * Utility methods to for {@link ObjectClass}.
 */
public class ObjectClassUtil {

    private ObjectClassUtil() {
    }

    /**
     * Determines whether the specified object class is a special object class.
     * Special object classes include {@link ObjectClass#ACCOUNT} and
     * {@link ObjectClass#GROUP}.
     *
     * @param oclass
     *            {@link ObjectClass} to test for against.
     * @return true iff the object class is a special one.
     * @throws NullPointerException
     *             iff the object class parameter is null.
     */
    public static boolean isSpecial(ObjectClass oclass) {
        String name = oclass.getObjectClassValue();
        return isSpecialName(name);
    }

    private static boolean isSpecialName(String name) {
        return NameUtil.isSpecialName(name);
    }

    /**
     * Create a special name from the specified name. Add the <code>__</code>
     * string as both prefix and suffix. This indicates that a name
     * identifies a special object class such as a predefined one.
     */
    public static String createSpecialName(String name) {
        return NameUtil.createSpecialName(name);
    }
}
