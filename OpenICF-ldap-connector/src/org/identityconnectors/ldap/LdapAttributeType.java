/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

public class LdapAttributeType {

    private final Class<?> type;
    private final Set<Flags> flags;

    public LdapAttributeType(Class<?> type, Set<Flags> flags) {
        this.type = type;
        this.flags = Collections.unmodifiableSet(flags);
    }

    public AttributeInfo createAttributeInfo(String realName, Set<Flags> add, Set<Flags> remove) {
        EnumSet<Flags> realFlags = flags.isEmpty() ? EnumSet.noneOf(Flags.class) : EnumSet.copyOf(flags);
        if (add != null) {
            realFlags.addAll(add);
        }
        if (remove != null) {
            realFlags.removeAll(remove);
        }
        return AttributeInfoBuilder.build(realName, type, realFlags);
    }
}
