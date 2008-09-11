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

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
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
 */
public final class AttributeInfo {

	private final String _name;
	private final Class<?> _type;
	private final boolean _required;
	private final boolean _readable;
	private final boolean _createable;
	private final boolean _multivalue;
	private final boolean _updateable;
	private final boolean _returnedByDefault;

	public AttributeInfo(final String name, final Class<?> type,
			final boolean readable, final boolean createable,
			final boolean required, final boolean multivalue,
			final boolean updateable, final boolean returnedByDefault) {
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
		_name = name;
		_type = type;
		_readable = readable;
		_createable = createable;
		_required = required;
		_multivalue = multivalue;
		_updateable = updateable;
		_returnedByDefault = returnedByDefault;
	}

	/**
	 * The native name of the attribute.
	 * 
	 * @return the native name of the attribute its describing.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * The basic type associated with this attribute. All primitives are
	 * supported.
	 * 
	 * @return the native type if uses.
	 */
	public Class<?> getType() {
		return _type;
	}

	/**
	 * Determines if the attribute is readable.
	 * 
	 * @return true if the attribute is readable else false.
	 */
	public boolean isReadable() {
		return _readable;
	}

	/**
	 * Determines if the attribute is writable on create.
	 * 
	 * @return true if the attribute is writable on create else false.
	 */
	public boolean isCreateable() {
		return _createable;
	}

	/**
	 * Determines if the attribute is writable on update.
	 * 
	 * @return true if the attribute is writable on update else false.
	 */
	public boolean isUpdateable() {
		return _updateable;
	}

	/**
	 * Determines whether this attribute is required for creates.
	 * 
	 * @return true if the attribute is required for an object else false.
	 */
	public boolean isRequired() {
		return _required;
	}

	/**
	 * Determines if this attribute can handle multiple values. There is a
	 * special case with byte[] since in most instances this denotes a single
	 * object.
	 * 
	 * @return true if the attribute is multi-value otherwise false.
	 */
	public boolean isMultiValue() {
		return _multivalue;
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
		return _returnedByDefault;
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
			AttributeInfo other = (AttributeInfo) obj;
			if (!getName().toUpperCase().equals(other.getName().toUpperCase())) {
				return false;
			}
			if (!getType().equals(other.getType())) {
				return false;
			}
			if (isReadable() != other.isReadable()) {
				return false;
			}
			if (isCreateable() != other.isCreateable()) {
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
			if (isUpdateable() != other.isUpdateable()) {
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
		map.put("Createable", isCreateable());
		map.put("MultiValue", isMultiValue());
		map.put("Updateable", isUpdateable());
		map.put("ReturnedByDefault", isReturnedByDefault());
		return map.toString();
	}

}
