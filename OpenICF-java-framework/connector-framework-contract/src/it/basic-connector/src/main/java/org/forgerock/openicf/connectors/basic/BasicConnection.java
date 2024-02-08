/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock Inc. All rights reserved.
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
package org.forgerock.openicf.connectors.basic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Class to represent a Basic Connection
 * 
 * @author $author$
 * @version $Revision$ $Date$
 */
public class BasicConnection {

    private static final ConcurrentMap<ObjectClass, ConcurrentMap<Uid, ConnectorObject>> store =
            new ConcurrentHashMap<ObjectClass, ConcurrentMap<Uid, ConnectorObject>>(2);

    static {
        store.put(ObjectClass.ACCOUNT, new ConcurrentHashMap<Uid, ConnectorObject>());
        store.put(ObjectClass.GROUP, new ConcurrentHashMap<Uid, ConnectorObject>());
    }

    private BasicConfiguration configuration;

    public BasicConnection(BasicConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Release internal resources
     */
    public void dispose() {
        // implementation
    }

    /**
     * If internal connection is not usable, throw IllegalStateException
     */
    public void test() {
        // implementation
    }

    public ConcurrentMap<Uid, ConnectorObject> getStorage(ObjectClass objectClass) {
        ConcurrentMap<Uid, ConnectorObject> collection = store.get(objectClass);
        if (null == collection) {
            throw new ConnectorException("Unsupported ObjectClass: " + objectClass);
        }
        return collection;
    }

}
