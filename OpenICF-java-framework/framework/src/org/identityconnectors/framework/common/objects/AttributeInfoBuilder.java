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

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.FrameworkUtil;

/**
 * Simplifies the process of building 'AttributeInfo' objects. This class is
 * responsible for providing a default implementation of {@link AttributeInfo}.
 * 
 * <code>
 * AttributeInfoBuilder bld = new AttributeBuilder();
 * bld.setName("email");
 * bld.setRequired(true);
 * AttributeInfo info = bld.build();
 * AttributeInfo info2 = AttributeInfoBuilder.build("someAttrInfo");
 * </code>
 * 
 * @author Will Droste
 * @version $Revision: 1.9 $
 * @since 1.0
 */
public final class AttributeInfoBuilder {

    private String name;
    private Class<?> type;
    private boolean readable;
    private boolean writeable;
    private boolean required;
    private boolean multivalue;
    private boolean returnedByDefault;

    /**
     * Creates an builder with all the defaults set. The name must be set before
     * the 'build' method is called otherwise an {@link IllegalStateException}
     * is thrown.
     * 
     * <pre>
     * Name: &lt;not set&gt;
     * Readable: true
     * Writeable: true
     * Required: false
     * Type: string
     * MultiValue: false
     * </pre>
     */
    public AttributeInfoBuilder() {
        this.name = null;
        this.readable = true;
        this.writeable = true;
        this.required = false;
        this.multivalue = false;
        this.type = String.class;
        this.returnedByDefault = true;
    }

    /**
     * Builds an {@link AttributeInfo} object based on the properties set.
     * 
     * @return {@link AttributeInfo} based on the properties set.
     */
    public AttributeInfo build() {
        if (StringUtil.isBlank(name)) {
            throw new IllegalStateException("Name must not be blank!");
        }
        if ((OperationalAttributes.PASSWORD_NAME.equals(name) ||
                OperationalAttributes.RESET_PASSWORD_NAME.equals(name) ||
                OperationalAttributes.CURRENT_PASSWORD_NAME.equals(name)) &&
                !GuardedString.class.equals(type)) {
            final String MSG = "Password based attributes must be of type GuardedString.";
            throw new IllegalArgumentException(MSG);
        }
        return new AttributeInfo(name, type, readable, writeable, required,
                multivalue, returnedByDefault);
    }

    /**
     * Sets the unique name of the {@link AttributeInfo} object.
     * 
     * @param name
     *            unique name of the {@link AttributeInfo} object.
     */
    public void setName(final String name) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException("Argument must not be blank.");
        }
        this.name = name;
    }

    /**
     * Please see {@link FrameworkUtil#checkAttributeType(Class)} for the
     * definitive list of supported types.
     * 
     * @param value
     *            type for an {@link Attribute}'s value.
     * @throws IllegalArgumentException
     *             if the Class is not a supported type.
     */
    public void setType(final Class<?> value) {
        FrameworkUtil.checkAttributeType(value);
        this.type = value;
    }

    /**
     * Determines if the attribute is readable.
     */
    public void setReadable(final boolean value) {
        this.readable = value;
    }

    /**
     * Determines if the attribute is writable.
     */
    public void setWriteable(final boolean value) {
        this.writeable = value;
    }

    /**
     * Determines if this attribute is required.
     * 
     * @param value
     */
    public void setRequired(final boolean value) {
        this.required = value;
    }

    public void setMultiValue(final boolean value) {
        this.multivalue = value;
    }

    public void setReturnedByDefault(final boolean value) {
        this.returnedByDefault = value;
    }

    // =======================================================================
    // Static Helper methods..
    // =======================================================================
    public static AttributeInfo build(final String name) {
        AttributeInfoBuilder bld = new AttributeInfoBuilder();
        bld.setName(name);
        return bld.build();
    }

    public static AttributeInfo build(final String name, final Class<?> type) {
        AttributeInfoBuilder bld = new AttributeInfoBuilder();
        bld.setName(name);
        bld.setType(type);
        return bld.build();
    }

    public static AttributeInfo build(final String name, final Class<?> type,
            final boolean required) {
        AttributeInfoBuilder bld = new AttributeInfoBuilder();
        bld.setName(name);
        bld.setType(type);
        bld.setRequired(required);
        return bld.build();
    }

    public static AttributeInfo build(final String name,
            final boolean required, final boolean readable,
            final boolean writeable) {
        AttributeInfoBuilder bld = new AttributeInfoBuilder();
        bld.setName(name);
        bld.setRequired(required);
        bld.setReadable(readable);
        bld.setWriteable(writeable);
        return bld.build();
    }

    public static AttributeInfo build(final String name, final Class<?> type,
            final boolean required, final boolean readable,
            final boolean writeable) {
        AttributeInfoBuilder bld = new AttributeInfoBuilder();
        bld.setName(name);
        bld.setType(type);
        bld.setRequired(required);
        bld.setReadable(readable);
        bld.setWriteable(writeable);
        return bld.build();
    }
}
