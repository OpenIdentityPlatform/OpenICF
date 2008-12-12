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

import java.util.List;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;

public class ContainsAllValuesFilter extends AttributeFilter {

    private final String _name;
    private final List<Object> _values;

    public ContainsAllValuesFilter(Attribute attr) {
        super(attr);
        _name = attr.getName();
        _values = attr.getValue();
    }

    /**
     * Determine if the {@link ConnectorObject} contains an {@link Attribute}
     * which contains all the values provided in the {@link Attribute} passed
     * into the filter.
     * 
     * {@inheritDoc}
     */
    public boolean accept(ConnectorObject obj) {
        Attribute found = obj.getAttributeByName(_name);
        if (found != null) {
            // TODO: possible optimization using 'Set'
            return found.getValue().containsAll(_values);
        }
        return false;
    }
}
