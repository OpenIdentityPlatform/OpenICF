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
package org.identityconnectors.ldap;

import org.identityconnectors.framework.common.objects.ObjectClass;

public class LdapObjectClass {

    public static final String GROUP_NAME = "Group";

    public static final String DOMAIN_NAME = "Domain";

    public static final String ORGANIZATION_NAME = "Organization";

    public static final String ORGANIZATIONAL_UNIT_NAME = "Organizational Unit";

    public static final ObjectClass GROUP = new ObjectClass(GROUP_NAME);

    public static final ObjectClass DOMAIN = new ObjectClass(DOMAIN_NAME);

    public static final ObjectClass ORGANIZATION = new ObjectClass(ORGANIZATION_NAME);

    public static final ObjectClass ORGANIZATIONAL_UNIT = new ObjectClass(ORGANIZATIONAL_UNIT_NAME);

    private LdapObjectClass() {
    }
}
