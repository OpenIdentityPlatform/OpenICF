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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.spi.Connector;


/**
 * Simplifies the building of {@link Attribute}s. The builder creates an
 * {@link Attribute} that overrides the methods equals, hashcode, and toString
 * to provide a uniform and robust class. The {@link Connector} developer does
 * not have to implement their own {@link Attribute} class in order to create
 * {@link Attribute}. The implementation is backed by an {@link ArrayList} for
 * the values.
 * 
 * 
 * @author Will Droste
 * @version $Revision: 1.7 $
 * @since 1.0
 */
public final class AttributeBuilder {

    private final static String NAME_ERROR = "Name must not be blank!";

    String _name;

    List<Object> _value;

    /**
     * Creates a attribute with a {@code null} value.
     * 
     * @param name
     *            unique name of the attribute.
     * @return instance of an attribute with a {@code null} value.
     */
    public static Attribute build(final String name) {
        AttributeBuilder bld = new AttributeBuilder();
        bld.setName(name);
        return bld.build();
    }

    /**
     * Creates an attribute with name and the values provided.
     * 
     * @param name
     *            unique name of the attribute.
     * @param args
     *            variable number of arguments that are used as values for the
     *            attribute.
     * @return instance of an attribute with the name supplied and a value that
     *         includes the arguments provided.
     */
    public static Attribute build(final String name, final Object... args) {
        AttributeBuilder bld = new AttributeBuilder();
        bld.setName(name);
        bld.addValue(args);
        return bld.build();
    }

    /**
     * Creates an attribute the name and the values provided.
     */
    public static Attribute build(final String name, final Collection<?> obj) {
        // this method needs to be able to create the sub-classes
        // Name, Uid, ObjectClass
        AttributeBuilder bld = new AttributeBuilder();
        bld.setName(name);
        bld.addValue(obj);
        return bld.build();
    }

    /**
     * Get the name of the attribute.
     * 
     * @return The name of the attribute.
     */
    public String getName() {
        return _name;
    }

    /**
     * Set the name of the attribute.
     * 
     * @throws IllegalArgumentException
     *             iff the name parameter is blank.
     */
    public void setName(final String name) {
        if (StringUtil.isBlank(name)) {
            throw new IllegalArgumentException(NAME_ERROR);
        }
        _name = name;
    }

    /**
     * Returns the value of the attribute
     * 
     * @return The value of the attribute.
     */
    public List<Object> getValue() {
        return _value == null ? null : CollectionUtil.asReadOnlyList(_value);
    }

    /**
     * Adds values to the attribute.
     * 
     * @throws NullPointerException
     *             iff any of the values are null.
     */
    public void addValue(final Object... objs) {
        if (objs != null) {
            addValuesInternal(Arrays.asList(objs));
        }
    }

    /**
     * Adds each object in the collection.
     * 
     * @throws NullPointerException
     *             iff any of the values are null.
     */
    public void addValue(final Collection<?> obj) {
        addValuesInternal(obj);
    }

    /**
     * Creates the new attribute based on the name and values provided.
     */
    public Attribute build() {
        if (StringUtil.isBlank(_name)) {
            throw new IllegalArgumentException(NAME_ERROR);
        }
        // check for subclasses and some operational attributes..
        if (Uid.NAME.equals(_name)) {
            return new Uid(getSingleStringValue());
        } else if (Name.NAME.equals(_name)) {
            return new Name(getSingleStringValue());
        }
        return new Attribute(_name, _value);
    }

    /**
     * Determine if this is a single value attribute.
     */
    private void checkSingleValue() {
        if (_value == null || _value.size() != 1) {
            final String MSG = "Must be a single value.";
            throw new IllegalArgumentException(MSG);
        }        
    }
    
    /**
     * Determine if the value is suitable for a single value attribute.
     */
    private String getSingleStringValue() {
        checkSingleValue();
        if (!(_value.get(0) instanceof String)) {
            final String MSG = "Attribute value must be an instance of String.";
            throw new IllegalArgumentException(MSG);
        }
        return (String) _value.get(0);
    }

    private void addValuesInternal(final Iterable<?> values) {
        if (values != null) {
            // make sure the list is ready to receive values.
            if (_value == null) {
                _value = new ArrayList<Object>();
            }
            // add each value checking to make sure its correct
            for (Object v : values) {
                FrameworkUtil.checkAttributeValue(v);
                _value.add(v);
            }
        }
    }

    // =======================================================================
    // Operational Attributes
    // =======================================================================
    /**
     * Builds an password expiration date {@link Attribute}. This
     * {@link Attribute} represents the date/time a password will expire on a
     * resource.
     * 
     * @param dateTime
     *            UTC time in milliseconds.
     * @return an {@link Attribute} built with the pre-defined name for password
     *         expiration date.
     */
    public static Attribute buildPasswordExpirationDate(final Date dateTime) {
        return buildPasswordExpirationDate(dateTime.getTime());
    }

    /**
     * Builds an password expiration date {@link Attribute}. This
     * {@link Attribute} represents the date/time a password will expire on a
     * resource.
     * 
     * @param dateTime
     *            UTC time in milliseconds.
     * @return an {@link Attribute} built with the pre-defined name for password
     *         expiration date.
     */
    public static Attribute buildPasswordExpirationDate(final long dateTime) {
        return build(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,
                dateTime);
    }

    /**
     * Builds the operational attribute password.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a password.
     */
    public static Attribute buildPassword(final GuardedString password) {
        return build(OperationalAttributes.PASSWORD_NAME, password);
    }

    /**
     * Builds the operational attribute current password. The current password
     * indicates this a password change by the account owner and not an
     * administrator. The use case is that an administrator password change may
     * not keep history or validate against policy.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a password.
     */
    public static Attribute buildCurrentPassword(final GuardedString password) {
        return build(OperationalAttributes.CURRENT_PASSWORD_NAME, password);
    }

    /**
     * Builds the operational attribute reset password.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a reset password operation.
     */
    public static Attribute buildResetPassword(final GuardedString password) {
        return build(OperationalAttributes.RESET_PASSWORD_NAME, password);
    }
    
    /**
     * Builds the operational attribute password. The caller is responsible for
     * clearing out the array of characters.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a password.
     */
    public static Attribute buildPassword(final char[] password) {
        return buildPassword(new GuardedString(password));
    }

    /**
     * Builds the operational attribute current password. The current password
     * indicates this a password change by the account owner and not an
     * administrator. The use case is that an administrator password change may
     * not keep history or validate against policy.The caller is responsible for
     * clearing out the array of characters.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a password.
     */
    public static Attribute buildCurrentPassword(final char[] password) {
        return buildCurrentPassword(new GuardedString(password));
    }

    /**
     * Builds the operational attribute reset password. The caller is
     * responsible for clearing out the array of characters.
     * 
     * @param password
     *            the string that represents a password.
     * @return an attribute that represents a reset password operation.
     */
    public static Attribute buildResetPassword(final char[] password) {
        return buildResetPassword(new GuardedString(password));
    }

    /**
     * Builds ant operational attribute that either represents the object is
     * enabled or sets in disabled depending on where its used for instance on
     * {@link CreateApiOp} it could be used to create a disabled account. In
     * {@link SearchApiOp} it would show the object is enabled or disabled.
     * 
     * @param value
     *            true indicates the object is enabled otherwise false.
     * @return {@link Attribute} that determines the enable/disable state of an
     *         object.
     */
    public static Attribute buildEnabled(final boolean value) {
        return build(OperationalAttributes.ENABLE_NAME, value);
    }

    /**
     * Builds out an operational {@link Attribute} that determines the enable
     * date for an object.
     * 
     * @param date
     *            The date and time to enable a particular object, or the date
     *            time an object will be enabled.
     * @return {@link Attribute}
     */
    public static Attribute buildEnableDate(final Date date) {
        return buildEnableDate(date.getTime());
    }

    /**
     * Builds out an operational {@link Attribute} that determines the enable
     * date for an object. The time parameter is UTC in milliseconds.
     * 
     * @param date
     *            The date and time to enable a particular object, or the date
     *            time an object will be enabled.
     * @return {@link Attribute}
     */
    public static Attribute buildEnableDate(final long date) {
        return build(OperationalAttributes.ENABLE_DATE_NAME, date);
    }

    /**
     * Builds out an operational {@link Attribute} that determines the disable
     * date for an object.
     * 
     * @param date
     *            The date and time to enable a particular object, or the date
     *            time an object will be enabled.
     * @return {@link Attribute}
     */
    public static Attribute buildDisableDate(final Date date) {
        return buildDisableDate(date.getTime());
    }

    /**
     * Builds out an operational {@link Attribute} that determines the disable
     * date for an object. The time parameter is UTC in milliseconds.
     * 
     * @param date
     *            The date and time to enable a particular object, or the date
     *            time an object will be enabled.
     * @return {@link Attribute}
     */
    public static Attribute buildDisableDate(final long date) {
        return build(OperationalAttributes.DISABLE_DATE_NAME, date);
    }

    /**
     * Builds the lock attribute that determines if an object is locked out.
     * 
     * @param lock
     *            true if the object is locked otherwise false.
     * @return {@link Attribute} that represents the lock state of an object.
     */
    public static Attribute buildLockOut(final boolean lock) {
        return build(OperationalAttributes.LOCK_OUT_NAME, lock);
    }

    /**
     * Builds out an operational {@link Attribute} that determines if a password
     * is expired or expires a password.
     * 
     * @param value
     *            from the API true expires and from the SPI its shows its
     *            either expired or not.
     * @return {@link Attribute}
     */
    public static Attribute buildPasswordExpired(final boolean value) {
        return build(OperationalAttributes.PASSWORD_EXPIRED_NAME, value);
    }

    // =======================================================================
    // Pre-defined Attributes
    // =======================================================================

    /**
     * Builds out a pre-defined {@link Attribute} that determines the last login
     * date for an object.
     * 
     * @param date
     *            The date and time of the last login.
     * @return {@link Attribute}
     */
    public static Attribute buildLastLoginDate(final Date date) {
        return buildLastLoginDate(date.getTime());
    }

    /**
     * Builds out a pre-defined {@link Attribute} that determines the last login
     * date for an object. The time parameter is UTC in milliseconds.
     * 
     * @param date
     *            The date and time of the last login.
     * @return {@link Attribute}
     */
    public static Attribute buildLastLoginDate(final long date) {
        return build(PredefinedAttributes.LAST_LOGIN_DATE_NAME, date);
    }

    /**
     * Builds out a pre-defined {@link Attribute} that determines the last
     * password change date for an object.
     * 
     * @param date
     *            The date and time the password was changed.
     * @return {@link Attribute}
     */
    public static Attribute buildLastPasswordChangeDate(final Date date) {
        return buildLastPasswordChangeDate(date.getTime());
    }

    /**
     * Builds out a pre-defined {@link Attribute} that determines the last
     * password change date for an object.
     * 
     * @param date
     *            The date and time the password was changed.
     * @return {@link Attribute}
     */
    public static Attribute buildLastPasswordChangeDate(final long date) {
        return build(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME, date);
    } 

    /**
     * Common password policy attribute where the password must be changed every
     * so often. The value for this attribute is milliseconds since its the
     * lowest common denominator.
     */
    public static Attribute buildPasswordChangeInterval(final long value) {
        return build(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME, value);
    }
    
}
