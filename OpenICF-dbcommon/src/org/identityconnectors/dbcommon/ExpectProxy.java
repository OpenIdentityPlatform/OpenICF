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
package org.identityconnectors.dbcommon;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

/**
 * This is a Test helper class for testing expected method calls and return values of interfaces
 * <p>Limitation:</p><p>First implementation supports just a method name checking</p> 
 *   
 * @version $Revision 1.0$
 * @param <T> Type of the interface for testing
 * @since 1.0
 */
public class ExpectProxy<T> implements InvocationHandler {

    private List<String> methodNames = new ArrayList<String>();
    private List<Object> retVals = new ArrayList<Object>();
    private int count = 0;

    /**
     * Program the expected function call
     * @param methodName the expected method name
     * @param retVal the expected return value or proxy
     * @return
     */
    public ExpectProxy<T> expectAndReturn(final String methodName, final Object retVal) {
        this.methodNames.add(methodName);
        this.retVals.add(retVal);
        return this;
    }

    /**
     * Program the expected method call
     * @param methodName the expected method name
     * @return
     */
    public ExpectProxy<T> expect(final String methodName) {
        this.methodNames.add(methodName);
        //retVals must have same number of values as methodNames
        this.retVals.add(null);
        return this;
    }


    /**
     * Program the expected method call
     * @param methodName the expected method name
     * @param throwEx the expected exception
     * @return
     */
    public ExpectProxy<T> expectAndThrow(final String methodName, final Throwable throwEx) {
        return this.expectAndReturn(methodName, throwEx);
    }    
    
    /**
     * Test that all expected was called in the order
     * @return
     */
    public boolean isDone() {
        return count == methodNames.size();
    }

    /**
     * The InvocationHandler method
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (methodNames.size() > count && this.methodNames.get(count).equals(method.getName())) {
            final Object ret = retVals.get(count++);
            if(ret instanceof Throwable) {
                throw (Throwable) ret;
            }
            return ret;
        }
        Assert.fail("The call of method :" + method+ " was not ecpected. To do so, please call expectAndReturn(methodName,retVal)");
        return null;
    }

    /**
     * Return the Proxy implementation of the Interface
     * @param clazz of the interface
     * @return the proxy
     */
    @SuppressWarnings("unchecked")
    public T getProxy(Class<T> clazz) {
        ClassLoader cl = getClass().getClassLoader();
        Class<?> intef[] = new Class<?>[] { clazz };
        return (T) Proxy.newProxyInstance(cl, intef, this);
    }
}
