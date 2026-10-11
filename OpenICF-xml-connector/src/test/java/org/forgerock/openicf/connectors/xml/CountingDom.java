/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openicf.connectors.xml;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Wraps a DOM in proxies that count the DOM calls made through them. A walk that is linear in the
 * size of the document makes a bounded number of calls per node, so the count tells a linear walk
 * from a quadratic one without timing it (#157). Each node has one proxy, so identity comparisons
 * of nodes still hold, and nodes passed back to the DOM are unwrapped first.
 */
final class CountingDom {

    private final Map<Object, Object> proxies = new IdentityHashMap<Object, Object>();
    private long calls;

    Document wrap(Document document) {
        return (Document) wrap((Object) document);
    }

    long calls() {
        return calls;
    }

    private Object wrap(Object target) {
        if (!(target instanceof Node || target instanceof NodeList || target instanceof NamedNodeMap)) {
            return target;
        }
        Object proxy = proxies.get(target);
        if (proxy == null) {
            proxy = Proxy.newProxyInstance(CountingDom.class.getClassLoader(), domInterfaces(target.getClass()),
                    new Counted(target));
            proxies.put(target, proxy);
        }
        return proxy;
    }

    private static Object unwrap(Object object) {
        if (object != null && Proxy.isProxyClass(object.getClass())
                && Proxy.getInvocationHandler(object) instanceof Counted) {
            return ((Counted) Proxy.getInvocationHandler(object)).target;
        }
        return object;
    }

    private static Class<?>[] domInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (Modifier.isPublic(i.getModifiers()) && i.getName().startsWith("org.w3c.dom.")) {
                    interfaces.add(i);
                }
            }
        }
        return interfaces.toArray(new Class<?>[0]);
    }

    private final class Counted implements InvocationHandler {

        private final Object target;

        Counted(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            calls++;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    args[i] = unwrap(args[i]);
                }
            }
            try {
                return wrap(method.invoke(target, args));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
