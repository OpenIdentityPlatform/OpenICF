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
/*
 * Portions Copyrighted  2012 ForgeRock Inc.
 */
package org.identityconnectors.framework.impl.api.local;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.pooling.ObjectPoolConfiguration;
import org.identityconnectors.framework.common.exceptions.ConnectorException;


public class ObjectPool<T> {

    private static final Log _log = Log.getLog(ObjectPool.class);

    /**
     * Statistics bean
     */
    public static final class Statistics {
        private final int _numIdle;
        private final int _numActive;
        
        private Statistics(int numIdle, int numActive) {
            _numIdle = numIdle;
            _numActive = numActive;
        }
        
        /**
         * Returns the number of idle objects
         */
        public int getNumIdle() {
            return _numIdle;
        }
        
        /**
         * Returns the number of active objects
         */
        public int getNumActive() {
            return _numActive - _numIdle;
        }
    }
    
    /**
     * An object plus additional book-keeping
     * information about the object
     */
    private  class PooledObject implements ObjectPoolEntry<T>{
        /**
         * The underlying object 
         */
        private final T _object;
        
        /**
         * True if this is currently active, false if
         * it is idle
         */
        private boolean _isActive;
        
        /**
         * Last state change (change from active to
         * idle or vice-versa)
         */
        private long _lastStateChangeTimestamp;
        
        /**
         * Is this a freshly created object (never been pooled)?
         */
        private boolean _isNew;
        
        public PooledObject(T object) {
            _object = object;
            _isNew = true;
            touch();
        }
        
        public T getPooledObject() {
            return _object;
        }

        public void close() throws IOException {
            try {
            returnObject(this);
            } catch (InterruptedException e){
                _log.error(e,"Failed to close/dispose PooledObject object");
            }
        }

        public boolean isNew() {
            return _isNew;
        }
        
        public void setNew(boolean n) {
            _isNew = n;
        }
        
        public void setActive(boolean v) {
            if (_isActive != v) {
                touch();
                _isActive = v;
            }
        }
        
        private void touch() {
            _lastStateChangeTimestamp = System.currentTimeMillis();
        }

        public boolean isOlderThan(long maxAge) {
            return maxAge < (System.currentTimeMillis() - _lastStateChangeTimestamp);
        }
    }

    /**
     * Set contains all the PooledObject was made by this pool.
     * It contains all idle and borrowed(active) objects.
     */
    private Set<PooledObject> activeObjects;

    /**
     * Queue of idle objects. The one that has
     * been idle for the longest comes first in the queue
     */
    private ConcurrentLinkedQueue<PooledObject> _idleObjects =  new ConcurrentLinkedQueue<PooledObject>();

    /**
     * Limits the maximum available pooled object in the pool.
     */
    private Semaphore totalPermit;

    /**
     * Lock to maintain the state changes of the pool.
     */
    /** Lock held by take, poll, etc */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();

    /**
     * ObjectPoolHandler we use for managing object lifecycle
     */
    private final ObjectPoolHandler<T> _handler;
    
    /**
     * Configuration for this pool.
     */
    private final ObjectPoolConfiguration _config;

    /**
     * Is the pool shutdown
     */
    private volatile boolean _isShutdown = false;
    
    /**
     * Create a new ObjectPool
     * @param handler Handler for objects
     * @param config Configuration for the pool
     */
    public ObjectPool(ObjectPoolHandler<T> handler,
            ObjectPoolConfiguration config) {
        
        Assertions.nullCheck(handler, "handler");
        Assertions.nullCheck(config, "config");
        
        _handler = handler;
        //clone it
        _config = _handler.validate(config);
        activeObjects = new HashSet<PooledObject>(_config.getMaxObjects());
        totalPermit = new Semaphore(_config.getMaxObjects());
    }

    /**
     * Get the state of the pool.
     *
     * @return true if the {@link #shutdown()} method was called before.
     */
    public boolean isShutdown() {
        return _isShutdown;
    }

    /**
     * Return an object to the pool
     * @param pooled
     */
    private void returnObject(PooledObject pooled) throws InterruptedException {
        if (isShutdown() || _config.getMaxIdle() < 1) {
            dispose(pooled);
        } else {
            try {
                for (PooledObject entry : _idleObjects) {
                    if ((_config.getMaxIdle() <= _idleObjects.size()) || entry
                            .isOlderThan(_config.getMinEvictableIdleTimeMillis())) {
                        if (_idleObjects.remove(entry)) {
                            dispose(entry);
                        }
                    }
                }
            } finally {
                pooled.setActive(false);
                pooled.setNew(false);
                _idleObjects.add(pooled);
                signalNotEmpty();
            }
        }
    }
    
    /**
     * Borrow an object from the pool.
     * @return An object
     */
    public ObjectPoolEntry borrowObject() {
        PooledObject rv = null;
        try {
            do {
                rv = borrowObjectNoTest();
                try {
                    _handler.testObject(rv.getPooledObject());
                } catch (Exception e) {
                    if (null != rv) {
                        dispose(rv);
                        //if it's a new object, break out of the loop
                        //immediately
                        if (rv.isNew()) {
                            throw ConnectorException.wrap(e);
                        }
                        rv = null;
                    }
                }
            } while (null == rv);
            rv.setActive(true);
        } catch (InterruptedException e) {
            _log.error(e, "Failed to borrow object from pool.");
            throw ConnectorException.wrap(e);
        }
        return rv;
    }

    /**
     * Borrow an object from the pool, but don't test
     * it (it gets tested by the caller *outside* of
     * synchronization)
     * @return the object
     */
    private PooledObject borrowObjectNoTest() throws InterruptedException {
        if (isShutdown()) {
            throw new IllegalStateException("Object pool already shutdown");
        }

        // First borrow from the idle pool
        PooledObject pooledConn = borrowIdleObject();
        if (null == pooledConn) {
            long nanos = TimeUnit.SECONDS.toNanos(_config.getMaxWait());
            final ReentrantLock lock = this.takeLock;
            lock.lockInterruptibly();
            try {
                do {
                    if (totalPermit.tryAcquire()) {
                        //If the pool is empty and there are available permits then create a new instance.
                        return makeObject();
                    } else {
                        // Wait for permit or object to became available
                        try {
                            nanos = notEmpty.awaitNanos(nanos);
                        } catch (InterruptedException ie) {
                            notEmpty.signal(); // propagate to non-interrupted thread
                            throw ConnectorException.wrap(ie);
                        }

                        if (nanos <= 0) {
                            throw new ConnectorException("TimeOut");
                        }
                        // Try to borrow from the idle pool
                        pooledConn = borrowIdleObject();
                        if (null != pooledConn) {
                            return pooledConn;
                        }
                    }
                } while (nanos > 0);
            } finally {
                lock.unlock();
            }
        }
        return pooledConn;
    }

    /**
     * Polls the head object from the queue.
     * <p/>
     * Polls the head object and before it returns it checks the {@code MaxIdle} size and the {@code
     * MinEvictableIdleTime} before accepts the object.
     *
     * @return null if there was no fresh/new object in the queue.
     * @throws InterruptedException
     */
    private PooledObject borrowIdleObject() throws InterruptedException {
        for (PooledObject pooledConn = _idleObjects.poll(); pooledConn != null; pooledConn = _idleObjects.poll()) {
            int size = _idleObjects.size();
            if (_config.getMinIdle() < size + 1 && ((_config.getMaxIdle() < size) || pooledConn
                    .isOlderThan(_config.getMinEvictableIdleTimeMillis()))) {
                dispose(pooledConn);
            } else {
                return pooledConn;
            }
        }
        return null;
    }

    /**
     * Closes any idle objects in the pool.
     * <p/>
     * Existing active objects will remain alive and
     * be allowed to shutdown gracefully, but no more 
     * objects will be allocated.
     */
    public void shutdown() {
        _isShutdown = true;
        //just evict idle objects
        //if there are any active objects still
        //going, leave them alone so they can return
        //gracefully
        for (PooledObject entry = _idleObjects.poll(); entry != null; entry = _idleObjects.poll()) {
            try {
                dispose(entry);
            } catch (InterruptedException e) {
                _log.error(e, "Failed to dispose PooledObject object");
            }
        }
    }
    
    /**
     * Gets a snapshot of the pool's stats at a point in time.
     * @return The statistics
     */
    public Statistics getStatistics() {
        return new Statistics(_idleObjects.size(), activeObjects.size());
    }

    /**
     * This is a long running process to create and init the connector instance.
     * <p/>
     *
     * @throws ConnectorException
     *         if something happens.
     */
    private PooledObject makeObject() {
        synchronized (activeObjects) {
            PooledObject pooledConn = new PooledObject(
                    (activeObjects.size() > 0) ? _handler.makeObject() : _handler.makeFirstObject());
            activeObjects.add(pooledConn);
            return pooledConn;
        }
    }

    /**
     * Dispose of an object, but don't throw any exceptions
     *
     * @param entry
     */
    private void dispose(PooledObject entry) throws InterruptedException {
        final ReentrantLock lock = this.takeLock;
        lock.lockInterruptibly();
        try {
            synchronized (activeObjects) {
                //Make sure the disposed object was the last item in the activeObjects
                if (activeObjects.remove(entry) && activeObjects.isEmpty()) {
                    _handler.disposeLastObject(entry.getPooledObject());
                } else {
                    _handler.disposeObject(entry.getPooledObject());
                }
            }
        } catch (Exception e) {
            _log.warn(e, "disposeObject() is not supposed to throw");
        } finally {
            totalPermit.release();
            notEmpty.signal();
            lock.unlock();
        }
    }

    /**
     * Signals a waiting take. Called only from borrowObjectNoTest
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }
}
