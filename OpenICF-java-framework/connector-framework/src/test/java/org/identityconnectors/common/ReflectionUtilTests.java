/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.identityconnectors.common;

import static org.identityconnectors.common.ReflectionUtil.containsInterface;
import static org.identityconnectors.common.ReflectionUtil.getAllInterfaces;
import static org.identityconnectors.common.ReflectionUtil.newInstance;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Set;

import org.testng.annotations.Test;

public class ReflectionUtilTests {

    @Test
    public void functional() {
        // getInterfaces
        Set<Class<?>> set = getAllInterfaces(C1.class);
        assertTrue(set.contains(I1.class));
        assertTrue(set.contains(I2.class));
        assertFalse(set.contains(I3.class));
        // containsInterface
        assertTrue(containsInterface(C1.class, I1.class));
        assertTrue(containsInterface(C1.class, I2.class));
        assertFalse(containsInterface(C1.class, I3.class));
    }

    @Test
    public void newInstanceCallsNoArgConstructor() throws Exception {
        assertTrue(newInstance(MethodHandles.lookup(), Constructible.class) instanceof Constructible);
    }

    @Test
    public void newInstancePassesRuntimeExceptionThroughUnchanged() throws Exception {
        try {
            newInstance(MethodHandles.lookup(), ThrowsRuntimeException.class);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "from constructor");
        }
    }

    @Test
    public void newInstancePassesCheckedExceptionThroughUnchanged() throws Exception {
        try {
            newInstance(MethodHandles.lookup(), ThrowsCheckedException.class);
            fail("IOException expected");
        } catch (IOException e) {
            assertEquals(e.getMessage(), "from constructor");
        }
    }

    @Test
    public void newInstancePassesErrorThroughUnchanged() throws Exception {
        try {
            newInstance(MethodHandles.lookup(), ThrowsError.class);
            fail("NoClassDefFoundError expected");
        } catch (NoClassDefFoundError e) {
            assertEquals(e.getMessage(), "from constructor");
        }
    }

    @Test(expectedExceptions = NoSuchMethodException.class)
    public void newInstanceFailsWithoutNoArgConstructor() throws Exception {
        newInstance(MethodHandles.lookup(), NoNoArgConstructor.class);
    }

    @Test(expectedExceptions = IllegalAccessException.class)
    public void newInstanceFailsOnInaccessibleConstructor() throws Exception {
        // publicLookup() is no nestmate: the private constructor fails the access
        // check with an IllegalAccessError cause, which must not escape as an Error
        newInstance(MethodHandles.publicLookup(), PrivateConstructor.class);
    }

    @Test(expectedExceptions = IllegalAccessException.class)
    public void newInstanceFailsOnInaccessibleClass() throws Exception {
        // findConstructor reports a class the lookup cannot see without a cause,
        // which must not become a NullPointerException
        newInstance(MethodHandles.publicLookup(), PackagePrivate.class);
    }

    @Test
    public void newInstanceThrowsLinkageErrorOfClassWithMissingDependency() throws Exception {
        // A connector configuration class whose dependency is missing from the
        // bundle: the callers skip such a connector on NoClassDefFoundError, as
        // Class.newInstance() threw it, so it must not become a "no such
        // constructor" IllegalAccessException.
        final Class<?> clazz = new HidingClassLoader().loadClass(LinksAgainstHidden.class.getName());
        try {
            newInstance(MethodHandles.lookup(), clazz);
            fail("NoClassDefFoundError expected");
        } catch (NoClassDefFoundError e) {
            // the missing dependency, not the class that refers to it
            assertTrue(e.getMessage().contains(Hidden.class.getName().replace('.', '/')),
                    e.getMessage());
        }
    }

    // =======================================================================
    // Helper Classes/Interfaces
    // =======================================================================
    interface I1 {

    }

    interface I2 {

    }

    interface I3 {

    }

    class C1 implements I1, I2 {

    }

    interface I5 extends I1 {

    }

    class C2 implements I5 {

    }

    public static class Constructible {

    }

    public static class ThrowsRuntimeException {
        public ThrowsRuntimeException() {
            throw new IllegalArgumentException("from constructor");
        }
    }

    public static class ThrowsCheckedException {
        public ThrowsCheckedException() throws IOException {
            throw new IOException("from constructor");
        }
    }

    public static class ThrowsError {
        public ThrowsError() {
            throw new NoClassDefFoundError("from constructor");
        }
    }

    public static class NoNoArgConstructor {
        public NoNoArgConstructor(String value) {
        }
    }

    public static class PrivateConstructor {
        private PrivateConstructor() {
        }
    }

    static class PackagePrivate {
    }

    public static class Base {

    }

    public static class Hidden extends Base {

    }

    /**
     * Verifying {@link #widen} checks that {@link Hidden} is a {@link Base},
     * so the class cannot be linked when {@link Hidden} is not loadable.
     */
    public static class LinksAgainstHidden {
        public static Base widen(Hidden hidden) {
            return hidden;
        }
    }

    /**
     * Defines {@link LinksAgainstHidden} itself and cannot load {@link Hidden},
     * like a connector bundle that lacks one of its dependencies.
     */
    private static class HidingClassLoader extends ClassLoader {
        HidingClassLoader() {
            super(ReflectionUtilTests.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(Hidden.class.getName())) {
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(LinksAgainstHidden.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        final byte[] bytes = in.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return loaded;
            }
        }
    }
}
