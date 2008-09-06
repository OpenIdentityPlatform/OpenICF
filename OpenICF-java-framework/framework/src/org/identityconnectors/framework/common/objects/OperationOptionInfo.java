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
package org.identityconnectors.framework.common.objects;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.FrameworkUtil;

public final class OperationOptionInfo {
    private String _name;
    private Class<?> _type;
    
    public OperationOptionInfo(String name,
            Class<?> type) {
        Assertions.nullCheck(name, "name");
        Assertions.nullCheck(type, "type");
        FrameworkUtil.checkOperationOptionType(type);
        _name = name;
        _type = type;
    }
    
    public String getName() {
        return _name;
    }
    
    public Class<?> getType() {
        return _type;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof OperationOptionInfo) {
            OperationOptionInfo other =
                (OperationOptionInfo)o;
            if (!_name.equals(other._name)) {
                return false;
            }
            if (!_type.equals(other._type)) {
                return false;
            }
            return true;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return _name.hashCode();
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("OperationOptionInfo( ");
        builder.append(_name);
        builder.append(_type.toString());
        builder.append(") ");
        return builder.toString();
    }
}
