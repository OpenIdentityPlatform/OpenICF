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

package org.forgerock.openicf.connectors.xml.util;

import java.util.Arrays;
import org.identityconnectors.common.security.GuardedByteArray;

public class GuardedByteArrayAccessor implements GuardedByteArray.Accessor {

    public static final String code_id = "$Id$";
    private byte[] array;
    
    public void access(byte[] clearBytes) {
        array = new byte[clearBytes.length];
        System.arraycopy(clearBytes, 0, array, 0, array.length);
    }

    public byte[] getArray() {
        return array;
    }

    public void clear() {
        Arrays.fill(array, Byte.MIN_VALUE);
    }
}