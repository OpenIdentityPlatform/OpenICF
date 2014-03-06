/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.forgerock.openicf.connectors.docbook;

import org.identityconnectors.common.script.Script;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

import java.io.File;
import java.net.URI;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the DocBook Connector.
 *
 * @author $author$
 * @version $Revision$ $Date$
 */
public class DocBookConfiguration extends AbstractConfiguration {

    // String

    @ConfigurationProperty(order = 1, displayMessageKey = "string.display",
            groupMessageKey = "basic.group", helpMessageKey = "string.help", required = false,
            confidential = false)
    public String getString() {
        return null;
    }

    public void setString(String value) {
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "string.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "string.array.help",
            required = false, confidential = false)
    public String[] getStringArray() {
        return null;
    }

    public void setStringArray(String[] value) {
    }

    // long

    @ConfigurationProperty(order = 3, displayMessageKey = "long.display",
            groupMessageKey = "basic.group", helpMessageKey = "long.help", required = true,
            confidential = false)
    public long getLong() {
        return 1L;
    }

    public void setLong(long value) {
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "long.array.display",
            groupMessageKey = "advanced.group", helpMessageKey = "long.array.help",
            required = true, confidential = false, operations = { SyncOp.class })
    public long[] getLongArray() {
        return null;
    }

    public void setLongArray(long[] value) {
    }

    // Long

    @ConfigurationProperty(order = 5, displayMessageKey = "Long.display",
            groupMessageKey = "basic.group", helpMessageKey = "Long.help", required = true,
            confidential = false)
    public Long getLongObject() {
        return null;
    }

    public void setLongObject(Long value) {
    }

    @ConfigurationProperty(order = 6, displayMessageKey = "Long.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "Long.array.help", required = true,
            confidential = false)
    public Long[] getLongObjectArray() {
        return null;
    }

    public void setLongObjectArray(Long[] value) {
    }

    // char

    @ConfigurationProperty(order = 7, displayMessageKey = "char.display",
            groupMessageKey = "basic.group", helpMessageKey = "char.help", required = true,
            confidential = false)
    public char getChar() {
        return 'a';
    }

    public void setChar(char value) {
    }

    @ConfigurationProperty(order = 8, displayMessageKey = "char.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "char.array.help", required = true,
            confidential = false, operations = { CreateOp.class, UpdateOp.class, DeleteOp.class })
    public char[] getCharArray() {
        return null;
    }

    public void setCharArray(char[] value) {
    }

    // Character

    @ConfigurationProperty(order = 9, displayMessageKey = "character.display",
            groupMessageKey = "basic.group", helpMessageKey = "character.help", required = true,
            confidential = false)
    public Character getCharacter() {
        return null;
    }

    public void setCharacter(Character value) {
    }

    @ConfigurationProperty(order = 10, displayMessageKey = "character.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "character.array.help",
            required = true, confidential = false, operations = { CreateOp.class, UpdateOp.class,
                DeleteOp.class })
    public Character[] getCharacterArray() {
        return null;
    }

    public void setCharacterArray(Character[] value) {
    }

    // double

    @ConfigurationProperty(order = 11, displayMessageKey = "double.display",
            groupMessageKey = "basic.group", helpMessageKey = "double.help", required = true,
            confidential = false)
    public double getDouble() {
        return Math.PI;
    }

    public void setDouble(double value) {
    }

    @ConfigurationProperty(order = 12, displayMessageKey = "double.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "double.array.help",
            required = false, confidential = false, operations = { CreateOp.class, UpdateOp.class,
                DeleteOp.class })
    public double[] getDoubleArray() {
        return null;
    }

    public void setDoubleArray(double[] value) {
    }

    // Double

    @ConfigurationProperty(order = 13, displayMessageKey = "Double.display",
            groupMessageKey = "basic.group", helpMessageKey = "Double.help", required = false,
            confidential = false)
    public Double getDoubleObject() {
        return null;
    }

    public void setDoubleObject(Double value) {
    }

    @ConfigurationProperty(order = 14, displayMessageKey = "Double.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "Double.array.help",
            required = false, confidential = false)
    public Double[] getDoubleObjectArray() {
        return null;
    }

    public void setDoubleObjectArray(Double[] value) {
    }

    // float

    @ConfigurationProperty(order = 15, displayMessageKey = "float.display",
            groupMessageKey = "basic.group", helpMessageKey = "float.help", required = true,
            confidential = false)
    public float getFloat() {
        return Float.MAX_VALUE;
    }

    public void setFloat(float value) {
    }

    @ConfigurationProperty(order = 16, displayMessageKey = "float.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "float.array.help", required = false,
            confidential = false)
    public float[] getFloatArray() {
        return null;
    }

    public void setFloatArray(float[] value) {
    }

    // Float

    @ConfigurationProperty(order = 17, displayMessageKey = "Float.display",
            groupMessageKey = "basic.group", helpMessageKey = "Float.help", required = false,
            confidential = false)
    public Float getFloatObject() {
        return null;
    }

    public void setFloatObject(Float value) {
    }

    @ConfigurationProperty(order = 18, displayMessageKey = "Float.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "Float.array.help", required = false,
            confidential = false)
    public Float[] getFloatObjectArray() {
        return null;
    }

    public void setFloatObjectArray(Float[] value) {
    }

    // int

    @ConfigurationProperty(order = 19, displayMessageKey = "int.display",
            groupMessageKey = "basic.group", helpMessageKey = "int.help", required = true,
            confidential = false)
    public int getInt() {
        return Integer.MAX_VALUE;
    }

    public void setInt(int value) {
    }

    @ConfigurationProperty(order = 20, displayMessageKey = "int.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "int.array.help", required = false,
            confidential = false)
    public int[] getIntArray() {
        return null;
    }

    public void setIntArray(int[] value) {
    }

    // Integer

    @ConfigurationProperty(order = 21, displayMessageKey = "Integer.display",
            groupMessageKey = "basic.group", helpMessageKey = "Integer.help", required = false,
            confidential = false)
    public Integer getInteger() {
        return null;
    }

    public void setInteger(Integer value) {
    }

    @ConfigurationProperty(order = 22, displayMessageKey = "Integer.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "Integer.array.help",
            required = true, confidential = false)
    public Integer[] getIntegerArray() {
        return null;
    }

    public void setIntegerArray(Integer[] value) {
    }

    // boolean

    @ConfigurationProperty(order = 23, displayMessageKey = "boolean.display",
            groupMessageKey = "basic.group", helpMessageKey = "boolean.help", required = false,
            confidential = false)
    public boolean getBoolean() {
        return true;
    }

    public void setBoolean(boolean value) {
    }

    @ConfigurationProperty(order = 24, displayMessageKey = "boolean.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "boolean.array.help",
            required = true, confidential = false)
    public boolean[] getBooleanArray() {
        return null;
    }

    public void setBooleanArray(boolean[] value) {
    }

    // Boolean

    @ConfigurationProperty(order = 25, displayMessageKey = "Boolean.display",
            groupMessageKey = "basic.group", helpMessageKey = "Boolean.help", required = false,
            confidential = false)
    public Boolean getBooleanObject() {
        return null;
    }

    public void setBooleanObject(Boolean value) {
    }

    @ConfigurationProperty(order = 26, displayMessageKey = "Boolean.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "Boolean.array.help",
            required = true, confidential = false)
    public Boolean[] getBooleanObjectArray() {
        return null;
    }

    public void setBooleanObjectArray(Boolean[] value) {
    }

    // URI

    @ConfigurationProperty(order = 27, displayMessageKey = "uri.display",
            groupMessageKey = "basic.group", helpMessageKey = "uri.help", required = false,
            confidential = false)
    public URI getURI() {
        return null;
    }

    public void setURI(URI value) {
    }

    @ConfigurationProperty(order = 28, displayMessageKey = "uri.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "uri.array.help", required = false,
            confidential = false)
    public URI[] getURIArray() {
        return null;
    }

    public void setURIArray(URI[] value) {
    }

    // File

    @ConfigurationProperty(order = 29, displayMessageKey = "file.display",
            groupMessageKey = "basic.group", helpMessageKey = "file.help", required = false,
            confidential = false)
    public File getFile() {
        return null;
    }

    public void setFile(File value) {
    }

    @ConfigurationProperty(order = 30, displayMessageKey = "file.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "file.array.help", required = false,
            confidential = false)
    public File[] getFileArray() {
        return null;
    }

    public void setFileArray(File[] value) {
    }

    // GuardedByteArray

    @ConfigurationProperty(order = 31, displayMessageKey = "guardedbytearray.display",
            groupMessageKey = "basic.group", helpMessageKey = "guardedbytearray.help",
            required = false, confidential = true)
    public GuardedByteArray getGuardedByteArray() {
        return null;
    }

    public void setGuardedByteArray(GuardedByteArray value) {
    }

    @ConfigurationProperty(order = 32, displayMessageKey = "guardedbytearray.array.display",
            groupMessageKey = "basic.group", helpMessageKey = "guardedbytearray.array.help",
            required = false, confidential = true)
    public GuardedByteArray[] getGuardedByteArrayArray() {
        return null;
    }

    public void setGuardedByteArrayArray(GuardedByteArray[] value) {
    }

    // GuardedString

    @ConfigurationProperty(order = 33, displayMessageKey = "guardedstring.display",
            groupMessageKey = "basic.group", helpMessageKey = "guardedstring.help",
            required = false, confidential = true)
    public GuardedString getGuardedString() {
        return null;
    }

    public void setGuardedString(GuardedString value) {
    }

    @ConfigurationProperty(order = 34, displayMessageKey = "guardedstring.array.display",
            groupMessageKey = "advanced.group", helpMessageKey = "guardedstring.array.help",
            required = false, confidential = true, operations = { AuthenticateOp.class })
    public GuardedString[] getGuardedStringArray() {
        return null;
    }

    public void setGuardedStringArray(GuardedString[] value) {
    }

    // Script

    @ConfigurationProperty(order = 35, displayMessageKey = "script.display",
            groupMessageKey = "advanced.group", helpMessageKey = "script.help", required = false,
            confidential = false, operations = { ScriptOnConnectorOp.class,
                ScriptOnResourceOp.class })
    public Script getScript() {
        return null;
    }

    public void setScript(Script value) {
    }

    @ConfigurationProperty(order = 36, displayMessageKey = "script.array.display",
            groupMessageKey = "advanced.group", helpMessageKey = "script.array.help",
            required = false, confidential = false, operations = { ScriptOnConnectorOp.class,
                ScriptOnResourceOp.class })
    public Script[] getScriptArray() {
        return null;
    }

    public void setScriptArray(Script[] value) {
    }

    /**
     * {@inheritDoc}
     */
    public void validate() {

    }

}
