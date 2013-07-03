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
 */
package org.identityconnectors.dbcommon;

import java.sql.Types;

/**
 * The SQL parameter / util class.
 *
 * @since 1.0
 */
public final class SQLParam {

    private final String name;
    private final Object value;
    private final int sqlType;

    /**
     * The Sql param is a pair of value and its sqlType.
     *
     * @param name
     *            name of the attribute
     * @param value
     *            value
     * @param sqlType
     *            sql type
     */
    public SQLParam(final String name, final Object value, final int sqlType) {
        if (name == null || name.length() == 0) {
            // TODO localize this
            throw new IllegalArgumentException("SQL param name should be not null");
        }
        this.name = name;
        this.value = value;
        this.sqlType = sqlType;
    }

    /**
     * The Sql param is a pair of value and its sqlType.
     *
     * @param name
     *            name of the attribute
     * @param value
     *            value
     */
    public SQLParam(final String name, final Object value) {
        if (name == null || name.length() == 0) {
            // TODO localize this
            throw new IllegalArgumentException("SQL param name should be not null");
        }
        this.name = name;
        this.value = value;
        sqlType = Types.NULL;
    }

    /**
     * Accessor for the name property.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * The param value.
     *
     * @return a value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sql Type.
     *
     * @return a type
     */
    public int getSqlType() {
        return sqlType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (obj.getClass() != this.getClass())) {
            return false;
        }
        SQLParam other = (SQLParam) obj;
        return (name == other.name || (name != null && name.equals(other.name)))
                && (value == other.value || (value != null && value.equals(other.value)))
                && sqlType == other.sqlType;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (null == name ? 0 : name.hashCode());
        hash = 31 * hash + (null == value ? 0 : value.hashCode());
        hash = 31 * hash + sqlType;
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        if (getName() != null) {
            ret.append(getName());
            ret.append("=");
        }
        ret.append("\"" + getValue() + "\"");
        switch (getSqlType()) {
        case Types.ARRAY:
            ret.append(":[ARRAY]]");
            break;
        case Types.BIGINT:
            ret.append(":[BIGINT]");
            break;
        case Types.BINARY:
            ret.append(":[BINARY]");
            break;
        case Types.BIT:
            ret.append(":[BIT]");
            break;
        case Types.BLOB:
            ret.append(":[BLOB]");
            break;
        case Types.BOOLEAN:
            ret.append(":[BOOLEAN]");
            break;
        case Types.CHAR:
            ret.append(":[CHAR]");
            break;
        case Types.CLOB:
            ret.append(":[CLOB]");
            break;
        case Types.DATALINK:
            ret.append(":[DATALINK]");
            break;
        case Types.DATE:
            ret.append(":[DATE]");
            break;
        case Types.DECIMAL:
            ret.append(":[DECIMAL]");
            break;
        case Types.DISTINCT:
            ret.append(":[DISTINCT]");
            break;
        case Types.DOUBLE:
            ret.append(":[DOUBLE]");
            break;
        case Types.FLOAT:
            ret.append(":[FLOAT]");
            break;
        case Types.INTEGER:
            ret.append(":[INTEGER]");
            break;
        case Types.JAVA_OBJECT:
            ret.append(":[JAVA_OBJECT]");
            break;
        case Types.LONGVARBINARY:
            ret.append(":[LONGVARBINARY]");
            break;
        case Types.LONGVARCHAR:
            ret.append(":[LONGVARCHAR]");
            break;
        case Types.NULL:
            break;
        case Types.NUMERIC:
            ret.append(":[NUMERIC]");
            break;
        case Types.OTHER:
            ret.append(":[OTHER]");
            break;
        case Types.REAL:
            ret.append(":[REAL]");
            break;
        case Types.REF:
            ret.append(":[REF]");
            break;
        case Types.SMALLINT:
            ret.append(":[SMALLINT]");
            break;
        case Types.STRUCT:
            ret.append(":[STRUCT]");
            break;
        case Types.TIME:
            ret.append(":[TIME]");
            break;
        case Types.TIMESTAMP:
            ret.append(":[TIMESTAMP]");
            break;
        case Types.TINYINT:
            ret.append(":[TINYINT]");
            break;
        case Types.VARBINARY:
            ret.append(":[VARBINARY]");
            break;
        case Types.VARCHAR:
            ret.append(":[VARCHAR]");
            break;
        default:
            ret.append(":[SQL Type:" + getSqlType() + "]");
        }
        return ret.toString();

    }

}
