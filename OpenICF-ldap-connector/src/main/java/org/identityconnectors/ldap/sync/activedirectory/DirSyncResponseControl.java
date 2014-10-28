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
/**
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */

package org.identityconnectors.ldap.sync.activedirectory;

import javax.naming.ldap.BasicControl;
import java.io.IOException;
import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerDecoder;

public class DirSyncResponseControl extends BasicControl {
    
    public static final String OID = "1.2.840.113556.1.4.841";

    private int flag;
    private byte[] cookie;
    private int maxLength;

    public DirSyncResponseControl(String id, boolean criticality, byte[] value) throws IOException {
        super(id, criticality, value);
        this.cookie = new byte[0];

        if ((value != null) && (value.length > 0)) {
            BerDecoder decoder = new BerDecoder(value, 0, value.length);
            decoder.parseSeq(null);
            flag = decoder.parseInt();
            maxLength = decoder.parseInt();
	    cookie = decoder.parseOctetString(Ber.ASN_OCTET_STR, null);
        }
    }

    public byte[] getResponseCookie() {
	if (cookie.length != 0) {
	    return cookie;
	} else {
            return null;
	}
    }
    
    public boolean hasMore() {
	return (0 != flag);
    }
}