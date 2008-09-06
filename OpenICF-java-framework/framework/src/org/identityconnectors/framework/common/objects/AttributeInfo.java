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

import java.util.LinkedHashMap;
import java.util.Map;

import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;


/**
 * <i>AttributeInfo</i> is meta data responsible for describing an
 * {@link Attribute}. It can be programmatically determined at runtime or
 * statically constructed. The class determines if an {@link Attribute} is
 * required, readable, writable, or nullable. In also includes the native type
 * and name. It is recommended that date fields be represented as a long with
 * time zone UTC. It should be up to the display or separate attributes if the
 * time zone is necessary.
 * 
 * @author Will Droste
 * @version $Revision: 1.7 $
 * @since 1.0
 */
public final class AttributeInfo {

    private final String name;
    private final Class<?> type;
    private final boolean required;
    private final boolean readable;
    private final boolean writeable;
    private final boolean multivalue;
    private final boolean returnedByDefault;

    public AttributeInfo(final String name, final Class<?> type,
            final boolean readable, final boolean writeable,
            final boolean required, final boolean multivalue,
            final boolean returnedByDefault) {
        this.name = name;
        this.type = type;
        this.readable = readable;
        this.writeable = writeable;
        this.required = required;
        this.multivalue = multivalue;
        this.returnedByDefault = returnedByDefault;
    }

    /**
     * The native name of the attribute.
     * 
     * @return the native name of the attribute its describing.
     */
    public String getName() {
        return this.name;
    }

    /**
     * The basic type associated with this attribute. All primitives are
     * supported.
     * 
     * @return the native type if uses.
     */
    public Class<?> getType() {
        return this.type;
    }

    /**
     * Determines if the attribute is readable.
     * 
     * @return true if the attribute is readable else false.
     */
    public boolean isReadable() {
        return this.readable;
    }

    /**
     * Determines if the attribute is writable.
     * 
     * @return true if the attribute is writable else false.
     */
    public boolean isWritable() {
        return this.writeable;
    }

    /**
     * Determines whether this attribute is required for creates.
     * 
     * @return true if the attribute is required for an object else false.
     */
    public boolean isRequired() {
        return this.required;
    }

    /**
     * Determines if this attribute can handle multiple values. There is a
     * special case with byte[] since in most instances this denotes a single
     * object.
     * 
     * @return true if the attribute is multi-value otherwise false.
     */
    public boolean isMultiValue() {
        return this.multivalue;
    }

    /**
     * Determines if the attribute is returned by default. Indicates if an
     * {@link Attribute} will be returned during {@link SearchApiOp} or
     * {@link GetApiOp} inside a {@link ConnectorObject} by default. The default
     * value is <code>true</code>.
     * 
     * @return false iff the attribute should not be returned by default.
     */
    public boolean isReturnedByDefault() {
        return returnedByDefault;
    }

    /**
     * Determines if the name parameter matches this {@link AttributeInfo}.
     */
    public boolean is(String name) {
        return getName().equalsIgnoreCase(name);
    }

    // =======================================================================
    // Object Overrides
    // =======================================================================

    @Override
    public boolean equals(Object obj) {
        boolean ret = false;
        if (obj instanceof AttributeInfo) {
            AttributeInfo other = (AttributeInfo)obj;
            if (!getName().toUpperCase().equals(other.getName().toUpperCase())) {
                return false;
            }
            if (!getType().equals(other.getType())) {
                return false;
            }
            if (isReadable() != other.isReadable()) {
                return false;
            }
            if (isWritable() != other.isWritable()) {
                return false;
            }
            if (isRequired() != other.isRequired()) {
                return false;
            }
            if (isMultiValue() != other.isMultiValue()) {
                return false;
            }
            if (isReturnedByDefault() != other.isReturnedByDefault()) {
                return false;
            }
            return true;
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return getName().toUpperCase().hashCode();
    }

    @Override
    public String toString() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("Name", getName());
        map.put("Type", getType());
        map.put("Required", isRequired());
        map.put("Readable", isReadable());
        map.put("Writeable", isWritable());
        map.put("MultiValue", isMultiValue());
        map.put("ReturnedByDefault", isReturnedByDefault());
        return map.toString();
    }

}
