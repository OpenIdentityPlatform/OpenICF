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
