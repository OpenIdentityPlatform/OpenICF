/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.framework.impl.api.local;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a for managing the thread-local class loader
 *
 */
public class ThreadClassLoaderManager {
    private static ThreadLocal<ThreadClassLoaderManager> _instance
        = new ThreadLocal<ThreadClassLoaderManager>() {
        public ThreadClassLoaderManager initialValue() {
            return new ThreadClassLoaderManager();
        }
    };
    
    private final List<ClassLoader> _loaderStack = 
        new ArrayList<ClassLoader>();
        
    private ThreadClassLoaderManager() {
        
    }
    
    /**
     * Returns the thread-local instance of the manager
     * @return
     */
    public static ThreadClassLoaderManager getInstance() {
        return _instance.get();
    }
    
    /**
     * Sets the given loader as the thread-local classloader.
     * @param loader The class loader. May be null.
     */
    public void pushClassLoader(ClassLoader loader) {
        _loaderStack.add(Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
    }
    
    /**
     * Restores the previous loader as the thread-local classloader.
     */
    public void popClassLoader() {
        if (_loaderStack.size() == 0) {
            throw new IllegalStateException("Stack size is 0");
        }
        ClassLoader previous = _loaderStack.remove(_loaderStack.size()-1);
        Thread.currentThread().setContextClassLoader(previous);
    }
    
    /**
     * Returns the current thread-local class loader
     * @return the current thread-local class loader
     */
    public ClassLoader getCurrentClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
    
}
