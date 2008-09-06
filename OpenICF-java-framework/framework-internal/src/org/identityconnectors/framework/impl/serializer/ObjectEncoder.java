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
package org.identityconnectors.framework.impl.serializer;

/**
 * Interface to abstract away the difference between serializing
 * xml and binary
 */
public interface ObjectEncoder {
    /**
     * Writes an object using the appropriate serializer for that object
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the subelement name for xml serialization
     * @param object. The object to serialize
     * @param inline Ignore for binary serialization. For xml serialization,
     * this causes us not to have a sub-element. When inlining, polymorphic typing
     * is not supported.
     */
    public void writeObjectField(String fieldName, Object object, boolean inline);
    
    /**
     * Writes a boolean.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeBooleanField(String fieldName, boolean v);
    
    /**
     * Writes an int.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeIntField(String fieldName, int v);
    
    /**
     * Writes a long.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeLongField(String fieldName, long v);
    
    /**
     * Writes a float.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeFloatField(String fieldName, float v);
    
    /**
     * Writes a double.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeDoubleField(String fieldName, double v);

    /**
     * Writes a Class.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeClassField(String fieldName, Class<?> v);
    
    /**
     * Writes a String.
     * @param fieldName. A hint of the field name. Ignored for binary
     * serialization. Becomes the attribute name for xml serialization
     * @param v. The value to serialize
     */
    public void writeStringField(String fieldName, String v);
    
    /**
     * Writes the value in-line. 
     */
    public void writeStringContents(String str);

    /**
     * Writes the value in-line. 
     */
    public void writeBooleanContents(boolean v);

    /**
     * Writes the value in-line. 
     */
    public void writeIntContents(int v);
    
    /**
     * Writes the value in-line. 
     */
    public void writeLongContents(long v);
    
    /**
     * Writes the value in-line. 
     */
    public void writeFloatContents(float v);
    
    /**
     * Writes the value in-line. 
     */
    public void writeDoubleContents(double v);
    
    /**
     * Special case for byte [] that uses base64 encoding for XML
     */
    public void writeByteArrayContents(byte [] v);
    
    /**
     * Writes the value in-line. 
     */
    public void writeClassContents(Class<?> v);
    
    /**
     * Writes a sub-object
     * @param o The object to write
     */
    public void writeObjectContents(Object o);
}
