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
package org.identityconnectors.contract.data.groovy;

import java.util.LinkedList;
import java.util.List;

/**
 * <p>Support for lazy evaluation (property value is evaluated just 
 * when get(String) query is called in GroovyDataProvider).</p>
 * 
 * @author Zdenek Louzensky
 *
 */
public abstract class Lazy {         
    
    protected List<Object> successors = new LinkedList<Object>();    
    protected Object value;

    public Lazy plus(String s) {
        successors.add(s);
        return this;
    }
    
    public Lazy plus(Lazy lazy) {
        successors.add(lazy);
        return this;        
    }

    public static Lazy get(Object prop) {
        return new Get(prop);
    }
    
    public static Lazy random(Object pattern) {
        return new Random(pattern);
    }
    
    public static Lazy random(Object pattern, Class clazz) {
        return new Random(pattern, clazz);
    }

    public List<Object> getSuccessors() {
        return successors;
    }

    public void setSuccessors(List<Object> successors) {
        this.successors = successors;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
    
    
}
