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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.spi.operations.SyncOp;


/**
 * Represents a change to an object in a resource.
 * 
 * @see SyncApiOp
 * @see SyncOp
 */
public final class SyncDelta {
    private final Uid _uid;
    private final SyncToken _token;
    private final SyncDeltaType _deltaType;
    private final Set<Attribute> _attributes;

    /**
     * Creates a SyncDelata
     * 
     * @param uid
     *            The uid. Must not be null.
     * @param token
     *            The token. Must not be null.
     * @param deltaType
     *            The delta. Must not be null.
     * @param attributes
     *            May be null.
     */
    SyncDelta(Uid uid, SyncToken token, SyncDeltaType deltaType,
            Set<Attribute> attributes) {
        Assertions.nullCheck(uid, "uid");
        Assertions.nullCheck(token, "token");
        Assertions.nullCheck(deltaType, "deltaType");

        _uid = uid;
        _token = token;
        _deltaType = deltaType;
        _attributes = CollectionUtil.newReadOnlySet(attributes);

        // make sure attributes don't also contain uid
        if (AttributeUtil.getUidAttribute(attributes) != null) {
            throw new IllegalArgumentException(
                    "Attributes must not contain a UID");
        }
    }

    /**
     * Returns the <code>Uid</code> of the object that changed.
     * 
     * @return the <code>Uid</code> of the object that changed.
     */
    public Uid getUid() {
        return _uid;
    }

    /**
     * Returns the <code>SyncToken</code> of the object that changed.
     * 
     * @return the <code>SyncToken</code> of the object that changed.
     */
    public SyncToken getToken() {
        return _token;
    }

    /**
     * Returns the type of the change the occured.
     * 
     * @return The type of change that occured.
     */
    public SyncDeltaType getDeltaType() {
        return _deltaType;
    }

    /**
     * Returns the attributes associated with the change. TODO: Define whether
     * this is the whole object or just those that changed. The argument for
     * just the changes is that it will be faster. The argument against is that
     * the application will need to whole object anyway in most cases and so for
     * those cases it will actually be slower. Need some more emperical data
     * here...
     * 
     * @return The attributes
     */
    public Set<Attribute> getAttributes() {
        return _attributes;
    }
    
    @Override
    public String toString() {
        Map<String,Object> values = new HashMap<String, Object>();
        values.put("Uid", _uid);
        values.put("Token", _token);
        values.put("DeltaType", _deltaType);
        values.put("Attributes", _attributes);
        return values.toString();
    }
    
    @Override
    public int hashCode() {
        return _uid.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        if ( o instanceof SyncDelta ) {
            SyncDelta other = (SyncDelta)o;
            if (!_uid.equals(other._uid)) {
                return false;
            }
            if (!_token.equals(other._token)) {
                return false;
            }
            if (!_deltaType.equals(other._deltaType)) {
                return false;
            }
            if (!_attributes.equals(other._attributes)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
