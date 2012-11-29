/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import com.sun.xml.xsom.XSSchemaSet;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Sample Class Doc
 *
 * @author $author$
 * @version $Revision$ $Date$
 */
public class ConcurrentXMLHandler implements XMLHandler {

    final private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private XMLHandler proxy;
    private volatile int invokers = 0;

    public ConcurrentXMLHandler(XMLConfiguration config, Schema connSchema, XSSchemaSet xsdSchemas) {
        proxy = new XMLHandlerImpl(config, connSchema, xsdSchemas);
    }

    public Uid create(ObjectClass objClass, Set<Attribute> attributes) {
        Uid uid = null;
        lock.writeLock().lock();
        try {
            uid = proxy.create(objClass, attributes);
        }
        finally {
            lock.writeLock().unlock();
        }
        return uid;
    }

    public Uid update(ObjectClass objClass, Uid uid, Set<Attribute> replaceAttributes) {
        Uid newUid = null;
        lock.writeLock().lock();
        try {
            newUid = proxy.update(objClass, uid, replaceAttributes);
        }
        finally {
            lock.writeLock().unlock();
        }
        return newUid;
    }

    public void delete(ObjectClass objClass, Uid uid) {
        lock.writeLock().lock();
        try {
            proxy.delete(objClass, uid);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Collection<ConnectorObject> search(String query, ObjectClass objectClass) {
        Collection<ConnectorObject> result = null;
        lock.readLock().lock();
        try {
            result = proxy.search(query, objectClass);
        }
        finally {
            lock.readLock().unlock();
        }
        return result;
    }

    public Uid authenticate(String username, GuardedString password) {
        Uid result = null;
        lock.readLock().lock();
        try {
            result = proxy.authenticate(username, password);
        }
        finally {
            lock.readLock().unlock();
        }
        return result;
    }    

    public XMLHandler init() {
        lock.readLock().lock();
        try {
            if (0 == invokers) {
                proxy.init();
            }
            invokers++;
        }
        finally {
            lock.readLock().unlock();
        }
        return this;
    }

    public void dispose() {
        lock.readLock().lock();
        try {
            invokers--;
            if (0 == invokers) {
                proxy.dispose();
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isSupportUid(ObjectClass objectClass) {
        return proxy.isSupportUid(objectClass);
    }


}
