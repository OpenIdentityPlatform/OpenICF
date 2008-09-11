/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.common.objects;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;


/**
 * Represents a named collection of values within a resource object, although
 * the simplest case is a name-value pair (e.g., email, employeeID). Values can
 * be empty, null, or set with various types. Empty and null are supported
 * because it makes a difference on some resources (in particular database
 * resources).
 * 
 * The developer of a Connector should use an {@link AttributeBuilder} to
 * construct an instance of Attribute.
 * 
 * TODO: define the set of allowed values
 * 
 * @author Will Droste
 * @version $Revision: 1.7 $
 * @since 1.0
 */
public class Attribute {
    /**
     * Name of the {@link Attribute}.
     */
    private final String name;

    /**
     * Values of the {@link Attribute}.
     */
    private final List<Object> value;

    /**
     * Create an attribute.
     */
    public Attribute(String name, List<Object> value) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("Name must not be blank!");
        }
        if (OperationalAttributes.PASSWORD_NAME.equals(name)
                || OperationalAttributes.CURRENT_PASSWORD_NAME.equals(name)
                || OperationalAttributes.RESET_PASSWORD_NAME.equals(name)) {
            // check the value..
            if (value == null || value.size() != 1) {
                final String MSG = "Must be a single value.";
                throw new IllegalArgumentException(MSG);
            }
            if (!(value.get(0) instanceof GuardedString)) {
                final String MSG = "Password value must be an instance of GuardedString";
                throw new IllegalArgumentException(MSG);
            }
        }
        // make this case insensitive
        this.name = name;
        // copy to prevent corruption..
        this.value = (value == null) ? null : CollectionUtil.newReadOnlyList(value);
    }

    public String getName() {
        return this.name;
    }

    public List<Object> getValue() {
        return (this.value == null) ? null : Collections
                .unmodifiableList(this.value);
    }

    /**
     * Determines if the 'name' matches this {@link Attribute}.
     * 
     * @param name
     *            case insensitive string representation of the attribute's
     *            name.
     * @return <code>true</code> iff the case insentitive name is equal to
     *         that of the one in {@link Attribute}.
     */
    public boolean is(String name) {
        return getName().equalsIgnoreCase(name);
    }

    // ===================================================================
    // Object Overrides
    // ===================================================================
    @Override
    public final int hashCode() {
        return getName().toUpperCase().hashCode();
    }

    @Override
    public String toString() {
        // poor man's consistent toString impl..
        StringBuilder bld = new StringBuilder();
        bld.append("Attribute: ");
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("Name", getName());
        map.put("Value", getValue());
        bld.append(map);
        return bld.toString();
    }

    @Override
    public final boolean equals(Object obj) {
        // test identity
        if (this == obj) {
            return true;
        }
        // test for null..
        if (obj == null) {
            return false;
        }
        // test that the exact class matches
        if (!(getClass().equals(obj.getClass()))) {
            return false;
        }
        // test name field..
        final Attribute other = (Attribute) obj;
        if (!name.equalsIgnoreCase(other.name)) {
            return false;
        }
        
        if (!CollectionUtil.equals(value, other.value)) {
            return false;
        }
        return true;
    }
}
