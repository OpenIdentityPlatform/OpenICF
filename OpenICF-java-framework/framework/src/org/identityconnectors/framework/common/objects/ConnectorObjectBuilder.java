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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;


/**
 * Builder class to create a {@link ConnectorObject}.
 * 
 * The developer of a Connector will construct a ConnectorObjectBuilder, and
 * then call the ConnectorObjectBuilder to set a {@link Uid}, add attributes,
 * and then finally to {@link #build()} the actual {@link ConnectorObject}.
 */
public final class ConnectorObjectBuilder {

    private ObjectClass _objectClass;
    private Map<String, Attribute> _attrs;

    // =======================================================================
    // Constructors
    // =======================================================================
    public ConnectorObjectBuilder() {
        _attrs = new HashMap<String, Attribute>();
        // default always add the account object class..
        setObjectClass(ObjectClass.ACCOUNT);
    }

    // =======================================================================
    // Uid Setters
    // =======================================================================
    public void setUid(final String uid) {
        addAttribute(new Uid(uid));
    }

    public void setUid(final Uid uid) {
        addAttribute(uid);
    }

    // =======================================================================
    // Name Setter
    // =======================================================================
    public void setName(final String name) {
        addAttribute(new Name(name));
    }
    public void setName(final Name name) {
        addAttribute(name);
    }
    // =======================================================================
    // ObjectClass Setter
    // =======================================================================
    public void setObjectClass(ObjectClass oclass) {
        _objectClass = oclass;
    }

    // =======================================================================
    // Clone basically..
    // =======================================================================
    /**
     * Takes all the attribute from a {@link ConnectorObject} and add/overwrite
     * the current attributes.
     */
    public void add(ConnectorObject obj) {
        // simply add all the attributes
        for (Attribute attr : obj.getAttributes()) {
            addAttribute(attr);
        }
        setObjectClass(obj.getObjectClass());
    }

    // =======================================================================
    // Attribute based methods..
    // =======================================================================
    /**
     * Adds one or many attributes to the {@link ConnectorObject}.
     */
    public void addAttribute(Attribute... attrs) {
        validateParameter(attrs, "attrs");
        for (Attribute a : attrs) {
            _attrs.put(a.getName(), a);
        }
    }

    /**
     * Add all the {@link Attribute}s of a {@link Collection}.
     */
    public void addAttributes(Collection<Attribute> attrs) {
        validateParameter(attrs, "attrs");
        for (Attribute a : attrs) {
            _attrs.put(a.getName(), a);
        }
    }
    /**
     * Adds values to the attribute.
     */
    public void addAttribute(String name, Object... objs) {
        addAttribute(AttributeBuilder.build(name, objs));
    }

    /**
     * Adds each object in the collection.
     */
    public void addAttribute(String name, Collection<?> obj) {
        addAttribute(AttributeBuilder.build(name, obj));
    }

    // =======================================================================
    // Build Out..
    // =======================================================================

    /**
     * Builds a 'ConnectorObject' based on the attributes and Uid provided.
     */
    public ConnectorObject build() {
        // check that there are attributes to return..
        if (_attrs.size() == 0) {
            throw new IllegalStateException("No attributes set!");
        }
        Set<Attribute> attrs = CollectionUtil.newReadOnlySet(_attrs.values());
        return new ConnectorObject(_objectClass,attrs);
    }
    
    private static void validateParameter(Object param, String paramName) {
        if (param == null) {
            final String FORMAT = "Parameter '%s' must not be null!";
            throw new NullPointerException(String.format(FORMAT, paramName));
        }
    }
}
