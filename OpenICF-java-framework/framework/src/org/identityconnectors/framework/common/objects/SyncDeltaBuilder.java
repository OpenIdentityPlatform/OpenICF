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
 * Builder for {@link SyncDelta}.
 */
public final class SyncDeltaBuilder {
    private SyncToken _token;
    private SyncDeltaType _deltaType;
    private Uid _uid;
    private ConnectorObject _object;

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
        _token = delta.getToken();
        _deltaType = delta.getDeltaType();
        _object = delta.getObject();
        _uid = delta.getUid();
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
     * Gets the Uid of the object that changed
     * @return The Uid of the object that changed.
     */
    public Uid getUid() {
        return _uid;
    }
    
    /**
     * Sets the Uid of the object that changed.
     * Note that this is implicitly set when you call
     * {@link #setObject(ConnectorObject)}.
     * @param uid The Uid of the object that changed.
     */
    public void setUid(Uid uid) {
        _uid = uid;
    }

    /**
     * Returns the object that changed.
     * @return The object that changed. May be null for
     * deletes.
     */
    public ConnectorObject getObject() {
        return _object;
    }

    /**
     * Sets the object that changed and implicitly
     * sets Uid if object is not null.
     * @param object The object that changed. May be
     * null for deletes.
     */
    public void setObject(ConnectorObject object) {
        _object = object;
        if ( object != null ) {
            _uid = object.getUid();
        }
    }


    /**
     * Creates a SyncDelta. Prior to calling the following must be specified:
     * <ol>
     * <li>{@link #setObject(ConnectorObject) Object} (for anything other than delete)</li>
     * <li>{@link #setUid(Uid) Uid} (this is implictly set when calling {@link #setObject(ConnectorObject)})</li>
     * <li>{@link #setToken(SyncToken) Token}</li>
     * <li>{@link #setDeltaType(SyncDeltaType) DeltaType}</li>
     * </ol>
     */
    public SyncDelta build() {
        return new SyncDelta(_token, _deltaType, _uid, _object);
    }
}
