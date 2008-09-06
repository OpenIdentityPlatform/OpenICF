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

/**
 * An instance of <code>ObjectClass</code> 
 * specifies a <i>category or type</i> of {@link ConnectorObject}.
 * This class predefines some common object-classes,
 * such as <code>ACCOUNT</code> and <code>GROUP</code>.
 * 
 * @author Will Droste
 * @version $Revision: 1.3 $
 * @since 1.0
 */
public final class ObjectClass {
    


    // =======================================================================
    // Basic Types--i.e., common values of the ObjectClass attribute.
    // =======================================================================    

    /**
     * This constant defines a specific 
     * {@linkplain #getObjectClassValue value of ObjectClass} 
     * that is reserved for {@link ObjectClass#ACCOUNT}.
     */
    public static final String ACCOUNT_NAME = "account";

    /**
     * This constant defines a specific 
     * {@linkplain #getObjectClassValue value of ObjectClass} 
     * that is reserved for {@link ObjectClass#PERSON}.
     */
    public static final String PERSON_NAME = "person";

    /**
     * This constant defines a specific 
     * {@linkplain #getObjectClassValue value of ObjectClass} 
     * that is reserved for {@link ObjectClass#GROUP}.
     */
    public static final String GROUP_NAME = "group";

    /**
     * This constant defines a specific 
     * {@linkplain #getObjectClassValue value of ObjectClass} 
     * that is reserved for {@link ObjectClass#ORGANIZATION}.
     */
    public static final String ORGANIZATION_NAME = "organization";
    

    
    // =======================================================================
    // Create only after all other static initializers
    // =======================================================================
    
    /**
     * Represents a human being <i>in the context of a specific system or application</i>.
     * <p>
     * When an attribute matching this constant is found within a <code>ConnectorObject</code>,
     * this indicates that the <code>ConnectorObject</code> represents a human being
     * (actual or fictional) within the context of a specific system or application.
     * <p>
     * Generally, an Account object records characteristics of a human user
     * (such as loginName, password, user preferences or access privileges)
     * that are relevant only to (or primarily to) a specific system or application.
     *
     * @see #PERSON
     */
    public static final ObjectClass ACCOUNT = new ObjectClass(ACCOUNT_NAME);

    /**
     * Represents a human being <i>independent of any specific system or application</i>.
     * <p>
     * When an attribute matching this constant is found within a <code>ConnectorObject</code>,
     * this indicates that the <code>ConnectorObject</code> represents a human being
     * (actual or fictional) independent of any specific system or application.
     * <p>
     * Generally, a Person object describes "real-world" characteristics of a human being
     * (such as email-address, home address, telephone numbers, gender, and so forth)
     * that are <em>not</em> specific to any system or application.
     * 
     * NOTE: Few applications (other than provisioning systems) need a separate ObjectClass 
     * for Person; most applications simply treat every user as an Account.  
     * At a practical level, the most obvious distinction between Person and Account
     * is that a <i>Person owns Accounts</i>.  That is, a human being is responsible for 
     * properly using and securing his or her access to various systems and applications.
     * @see #ACCOUNT
     */
    public static final ObjectClass PERSON = new ObjectClass(PERSON_NAME);

    /**
     * Represents a collection that contains an object (such as a person or an account).
     * <p>
     * When an attribute matching this constant is found within a <code>ConnectorObject</code>,
     * this indicates that the <code>ConnectorObject</code> represents a group.
     */
    public static final ObjectClass GROUP = new ObjectClass(GROUP_NAME);

    /**
     * Represents (a portion of) the management hierarchy of a corporation that contains a person.
     * <p>
     * When an attribute matching this constant is found within a <code>ConnectorObject</code>,
     * this indicates that the <code>ConnectorObject</code> represents an organization.
     * An organization usually contains people, but may also contain other types of objects.
     *
     * @see #GROUP
     */
    public static final ObjectClass ORGANIZATION = new ObjectClass(ORGANIZATION_NAME);

    private final String _type;
    
    /**
     * Create a custom object class.
     * 
     * @param type
     *            string representation for the name of the object class.
     */
    public ObjectClass(String type) {
        if ( type == null ) {
            throw new IllegalArgumentException("Type cannot be null.");
        }
        _type = type;
    }

    /**
     * Get the name of the object class.
     * (For example, the name of {@link ObjectClass#ACCOUNT}
     * is the value defined by {@link ObjectClass#ACCOUNT_NAME},
     * which is <code>"@@ACCOUNT@@"</code>.)
     */
    public String getObjectClassValue() {
        return _type;
    }
    
    @Override
    public int hashCode() {
        return _type.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if ( o instanceof ObjectClass ) {
            ObjectClass other = (ObjectClass)o;
            return _type.equals(other._type);
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "ObjectClass: "+_type;
    }

}
