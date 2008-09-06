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

import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.CollectionUtil;


/**
 * Builder for {@link SyncDelta}.
 */
public final class SyncDeltaBuilder {
    private Uid _uid;
    private SyncToken _token;
    private SyncDeltaType _deltaType;
    private Set<Attribute> _attributes = new HashSet<Attribute>();

    /**
     * Create a new <code>SyncDeltaBuilder</code>
     */
    public SyncDeltaBuilder() {

    }
    
    /**
     * Creates a new <code>SyncDeltaBuilder</code> whose
     * values are initialized to those of the delta.
     * @param delta The original delta.
     */
    public SyncDeltaBuilder(SyncDelta delta) {
        _uid = delta.getUid();
        _token = delta.getToken();
        _deltaType = delta.getDeltaType();
        _attributes = new HashSet<Attribute>(delta.getAttributes());
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
     * Sets the <code>Uid</code> of the object that changed.
     * 
     * @param uid
     *            the <code>Uid</code> of the object that changed.
     */
    public void setUid(Uid uid) {
        _uid = uid;
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
     * Sets the <code>SyncToken</code> of the object that changed.
     * 
     * @param token
     *            the <code>SyncToken</code> of the object that changed.
     */
    public void setToken(SyncToken token) {
        _token = token;
    }

    /**
     * Returns the type of the change that occurred.
     * 
     * @return The type of change that occurred.
     */
    public SyncDeltaType getDeltaType() {
        return _deltaType;
    }

    /**
     * Sets the type of the change that occurred.
     * 
     * @param type
     *            The type of change that occurred.
     */
    public void setDeltaType(SyncDeltaType type) {
        _deltaType = type;
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

    /**
     * Sets the attributes associated with the change. TODO: Define whether this
     * is the whole object or just those that changed. The argument for just the
     * changes is that it will be faster. The argument against is that the
     * application will need to whole object anyway in most cases and so for
     * those cases it will actually be slower. Need some more emperical data
     * here...
     * 
     * @param attributes
     *            The attributes
     */
    public void setAttributes(Set<Attribute> attributes) {
        attributes = CollectionUtil.nullAsEmpty(attributes);
        _attributes = attributes;
    }

    /**
     * Adds an attribute associated with the change. TODO: Define whether this
     * is the whole object or just those that changed. The argument for just the
     * changes is that it will be faster. The argument against is that the
     * application will need to whole object anyway in most cases and so for
     * those cases it will actually be slower. Need some more emperical data
     * here...
     * 
     * @param attribute
     *            The attribute
     */
    public void addAttribute(Attribute attribute) {
        Assertions.nullCheck(attribute, "attribute");
        _attributes.add(attribute);
    }

    /**
     * Creates a SyncDelata. Prior to calling the following must be specified:
     * <ol>
     * <li>{@link #setUid(Uid) Uid}</li>
     * <li>{@link #setToken(SyncToken) Token}</li>
     * <li>{@link #setDeltaType(SyncDeltaType) DeltaType}</li>
     * </ol>
     */
    public SyncDelta build() {
        return new SyncDelta(_uid, _token, _deltaType, _attributes);
    }
}
