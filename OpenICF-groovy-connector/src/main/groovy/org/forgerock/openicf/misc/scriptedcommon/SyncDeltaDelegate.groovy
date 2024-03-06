/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.misc.scriptedcommon

import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder
import org.identityconnectors.framework.common.objects.SyncDeltaType
import org.identityconnectors.framework.common.objects.SyncToken
import org.identityconnectors.framework.common.objects.Uid

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
class SyncDeltaDelegate extends AbstractICFBuilder<SyncDeltaBuilder> {

    SyncDeltaDelegate(SyncDeltaBuilder builder) {
        super(builder)
    }

    void syncToken(SyncToken token) {
        ((SyncDeltaBuilder) builder).setToken(token);
    }

    void syncToken(Object token) {
        ((SyncDeltaBuilder) builder).setToken(new SyncToken(token));
    }
    
    void setDeltaType(SyncDeltaType deltaType){
        ((SyncDeltaBuilder) builder).setDeltaType(deltaType)
    }

    void CREATE_OR_UPDATE() {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.CREATE_OR_UPDATE)
    }

    void DELETE() {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.DELETE)
    }

    void DELETE(String uid) {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.DELETE)
        ((SyncDeltaBuilder) builder).setUid(new Uid(uid))
    }

    void DELETE(Uid uid) {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.DELETE)
        ((SyncDeltaBuilder) builder).setUid(uid)
    }

    void CREATE() {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.CREATE)
    }

    void UPDATE() {
        ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.UPDATE)
    }

    void previousUid(String uid) {
        ((SyncDeltaBuilder) builder).setPreviousUid(new Uid(uid));
    }

    void previousUid(Uid uid) {
        ((SyncDeltaBuilder) builder).setPreviousUid(uid);
    }

    void object(@DelegatesTo(ConnectorObjectDelegate) Closure closure) {
        ((SyncDeltaBuilder) builder).setObject(ICFObjectBuilder.co(closure));
    }

    void object(ConnectorObject connectorObject) {
        ((SyncDeltaBuilder) builder).setObject(connectorObject);
    }
}
