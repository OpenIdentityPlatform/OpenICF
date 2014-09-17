/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */


import groovy.sql.Sql
import groovy.text.SimpleTemplateEngine
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.SearchResult
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter

import java.sql.Connection

def operation = operation as OperationType
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection
def filter = filter as Filter
def log = log as Log
def objectClass = objectClass as ObjectClass
def options = options as OperationOptions
def ORG = new ObjectClass("organization")


log.info("Entering " + operation + " Script");

def sql = new Sql(connection);

//Need to handle the __UID__ and __NAME__ in queries - this map has entries for each objectType, 
//and is used to translate fields that might exist in the query object from the ICF identifier
//back to the real property name.
def fieldMap = [
        "organization": [
                "__UID__"    : "id",
                "__NAME__"   : "name",
                "description": "description",
                "timestamp"  : "timestamp",
                "tableName"  : "Organizations"
        ],
        "__ACCOUNT__" : [
                "__UID__"     : "id",
                "__NAME__"    : "uid",
                "password"    : "password",
                "firstname"   : "firstname",
                "lastname"    : "lastname",
                "fullname"    : "fullname",
                "email"       : "email",
                "organization": "organization",
                "timestamp"   : "timestamp",
                "tableName"   : "Users"

        ],
        "__GROUP__"   : [
                "__UID__"    : "id",
                "__NAME__"   : "name",
                "gid"        : "gid",
                "description": "description",
                "timestamp"  : "timestamp",
                "tableName"  : "Groups"
        ]
]

def query = "SELECT * FROM ${tableName}"
def whereParams = fieldMap[objectClass.objectClassValue]
def where = ""


if (filter instanceof EqualsFilter && ((EqualsFilter) filter).getAttribute().is(Uid.NAME)) {
    //This is a Read

    def id = AttributeUtil.getStringValue(((EqualsFilter) filter).getAttribute());
    where = " WHERE ${__UID__} = :UID"
    whereParams["UID"] = id
} else if (filter != null) {
    //This is a Search

    def queryTemplate = filter.accept(new SQLFilterVisitor(), whereParams)
    where = " WHERE " + queryTemplate

    log.ok("Search WHERE clause is: " + where)
}

def pagedResultsCookie = null
def pageSize = 0

if (null != options.getPageSize() && options.getPageSize() > 0) {
    pageSize = options.getPageSize()
    /*
    Select * from Users order by firstname DESC, id DESC LIMIT 3
    Select * from Users where firstname <= 'John' AND (id < 5 OR firstname < 'John') order by firstname DESC, id DESC

    Select m2.* from Users m1, Users m2 where m1.id = m2.id AND m1.firstname <= 'John' AND (m1.id < 5 OR m1.firstname < 'John') ORDER BY m1.firstname DESC, m1.id DESC LIMIT 3
    */
    if (StringUtil.isBlank(options.getPagedResultsCookie())) {
        //First Page
        query = query + " LIMIT " + options.getPageSize()
        //return new SearchResult("NEXT");
    } else {
        //Next Page
        where = "(" + where + ") AND ${__UID__} > :pagedResultsCookie LIMIT " + options.getPageSize()
        whereParams[pagedResultsCookie] = options.getPagedResultsCookie()
    }
    where = where + "ORDER BY id ASC"    
} else {
    //If paged search requested ignore the sorting
    options?.sortKeys?.each {
        if (!AttributeUtil.namesEqual(it.field, Uid.NAME)) {
            if (it.isAscendingOrder()) {
                where = where + " ORDER BY ${it.field} ASC ,"
            } else {
                where = where + " ORDER BY ${it.field} DESC ,"
            }
        }
    }
}

query = new SimpleTemplateEngine().createTemplate(query + where).make(whereParams)

sql.eachRow((Map) whereParams, (String) query, { row ->

    def connectorObject = ICFObjectBuilder.co {
        switch (objectClass) {
            case ObjectClass.ACCOUNT:
                uid row.id as String
                id row.uid
                attribute 'uid', row.uid
                attribute 'fullname', row.fullname
                attribute 'firstname', row.firstname
                attribute 'lastname', row.lastname
                attribute 'email', row.email
                attribute 'organization', row.organization

                break;
            case ObjectClass.GROUP:
                uid row.id as String
                id row.name
                delegate.objectClass(objectClass)
                attribute 'gid', row.gid
                attribute 'description', row.description

                break;
            case ORG:
                uid row.id as String
                id row.name
                setObjectClass objectClass
                attribute 'description', row.description

                break;
            default:
                throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                        objectClass.objectClassValue + " is not supported.")
        }
    }

    if (pageSize > 0) {
        pageSize--
        //Just for simple paging by ID
        pagedResultsCookie = connectorObject.uid.uidValue
    }

    handler connectorObject
})
if (pageSize <= 0) {
    // There are no more page left
    pagedResultsCookie = null
}

return new SearchResult(pagedResultsCookie, -1);