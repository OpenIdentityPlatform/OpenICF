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

import java.util.EnumSet;
import java.util.Set;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;

/**
 * Simplifies the process of building 'AttributeInfo' objects. This class is
 * responsible for providing a default implementation of {@link AttributeInfo}.
 * 
 * <code>
 * AttributeInfoBuilder bld = new AttributeInfoBuilder("email");
 * bld.setRequired(true);
 * AttributeInfo info = bld.build();
 * </code>
 * 
 * @author Will Droste
 * @version $Revision: 1.9 $
 * @since 1.0
 */
public final class AttributeInfoBuilder {

	private String _name;
	private Class<?> _type;
	private final EnumSet<Flags> _flags;

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
        setType(String.class);
        _flags = EnumSet.noneOf(Flags.class);
    }
    
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
    public AttributeInfoBuilder(String name) {
        this(name,String.class);
    }
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
	public AttributeInfoBuilder(String name, Class<?> type) {
		setName(name);
		setType(type);
		//noneOf means the defaults
		_flags = EnumSet.noneOf(Flags.class);
	}

	/**
	 * Builds an {@link AttributeInfo} object based on the properties set.
	 * 
	 * @return {@link AttributeInfo} based on the properties set.
	 */
	public AttributeInfo build() {
		return new AttributeInfo(_name, _type, _flags);
	}

	/**
	 * Sets the unique name of the {@link AttributeInfo} object.
	 * 
	 * @param name
	 *            unique name of the {@link AttributeInfo} object.
	 */
	public AttributeInfoBuilder setName(final String name) {
		if (StringUtil.isBlank(name)) {
			throw new IllegalArgumentException("Argument must not be blank.");
		}
		_name = name;
		return this;
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
	public AttributeInfoBuilder setType(final Class<?> value) {
		FrameworkUtil.checkAttributeType(value);
		_type = value;
        return this;
	}

	/**
	 * Determines if the attribute is readable.
	 */
	public AttributeInfoBuilder setReadable(final boolean value) {
	    if ( !value ) {
	        _flags.add(Flags.NOT_READABLE);
	    }
	    else {
	        _flags.remove(Flags.NOT_READABLE);
	    }
        return this;
	}

	/**
	 * Determines if the attribute is writable.
	 */
	public AttributeInfoBuilder setCreateable(final boolean value) {
        if ( !value ) {
            _flags.add(Flags.NOT_CREATABLE);
        }
        else {
            _flags.remove(Flags.NOT_CREATABLE);
        }
        return this;
	}

	/**
	 * Determines if this attribute is required.
	 */
	public AttributeInfoBuilder setRequired(final boolean value) {
        if ( value ) {
            _flags.add(Flags.REQUIRED);
        }
        else {
            _flags.remove(Flags.REQUIRED);
        }
        return this;
	}

	/**
	 * Determines if this attribute supports multivalue.
	 */
	public AttributeInfoBuilder setMultiValued(final boolean value) {
        if ( value ) {
            _flags.add(Flags.MULTIVALUED);
        }
        else {
            _flags.remove(Flags.MULTIVALUED);
        }
        return this;
	}

	/**
	 * Determines if this attribute writable during update.
	 */
	public AttributeInfoBuilder setUpdateable(final boolean value) {
        if ( !value ) {
            _flags.add(Flags.NOT_UPDATEABLE);
        }
        else {
            _flags.remove(Flags.NOT_UPDATEABLE);
        }
        return this;
	}
	
	public AttributeInfoBuilder setReturnedByDefault(final boolean value) {
	    if ( !value ) {
	        _flags.add(Flags.NOT_RETURNED_BY_DEFAULT);
	    }
	    else {
	        _flags.remove(Flags.NOT_RETURNED_BY_DEFAULT);
	    }
        return this;
	}
		
	/**
	 * Sets all of the flags for this builder.
	 * @param flags The set of attribute info flags. Null means clear all flags.
	 * <p>
	 * NOTE: EnumSet.noneOf(AttributeInfo.Flags.class) results in
	 * an attribute with the default behavior:
	 * <ul>
	 *     <li>updateable</li>
	 *     <li>creatable</li>
     *     <li>returned by default</li>
     *     <li>readable</li>
     *     <li>single-valued</li>
     *     <li>optional</li>
	 * </ul>
	 */
	public AttributeInfoBuilder setFlags(Set<Flags> flags) {
	    _flags.clear();
	    if ( flags != null ) {
    	    _flags.addAll(flags);
	    }
	    return this;
	}
	
	/**
	 * Convenience method to create an AttributeInfo. Equivalent to
	 * <code>
	 * new AttributeInfoBuilder(name,type).setFlags(flags).build()
	 * </code>
	 * @param name The name of the attribute
	 * @param type The type of the attribute
	 * @param flags The flags for the attribute. Null means clear all flags
	 * @return The attribute info 
	 */
	public static AttributeInfo build(String name, Class<?> type,
	        Set<Flags> flags) {
	    return new AttributeInfoBuilder(name,type).setFlags(flags).build();
	}
    /**
     * Convenience method to create an AttributeInfo. Equivalent to
     * <code>
     * AttributeInfoBuilder.build(name,type,null)
     * </code>
     * @param name The name of the attribute
     * @param type The type of the attribute
     * @param flags The flags for the attribute
     * @return The attribute info 
     */
    public static AttributeInfo build(String name, Class<?> type) {
        return build(name,type,null);
    }

    /**
     * Convenience method to create an AttributeInfo. Equivalent to
     * <code>
     * AttributeInfoBuilder.build(name,type)
     * </code>
     * @param name The name of the attribute
     * @return The attribute info 
     */
    public static AttributeInfo build(String name) {
        return build(name,String.class);
    }
}
