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
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.MapFilterVisitor
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.SearchResult
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

// Parameters:
// The connector sends the following:
// connection: handler to the SQL connection
// configuration : handler to the connector's configuration object
// objectClass: a String describing the Object class (__ACCOUNT__ / __GROUP__ / other)
// operation: an OperationType describing the operation ("SEARCH" here)
// log: a handler to the Log facility
// options: a handler to the OperationOptions Map
// query: a handler to the Query Map
//
// The Query map describes the filter used.
//
// query = [ operation: "CONTAINS", left: attribute, right: "value", not: true/false ]
// query = [ operation: "ENDSWITH", left: attribute, right: "value", not: true/false ]
// query = [ operation: "STARTSWITH", left: attribute, right: "value", not: true/false ]
// query = [ operation: "EQUALS", left: attribute, right: "value", not: true/false ]
// query = [ operation: "GREATERTHAN", left: attribute, right: "value", not: true/false ]
// query = [ operation: "GREATERTHANOREQUAL", left: attribute, right: "value", not: true/false ]
// query = [ operation: "LESSTHAN", left: attribute, right: "value", not: true/false ]
// query = [ operation: "LESSTHANOREQUAL", left: attribute, right: "value", not: true/false ]
// query = null : then we assume we fetch everything
//
// AND and OR filter just embed a left/right couple of queries.
// query = [ operation: "AND", left: query1, right: query2 ]
// query = [ operation: "OR", left: query1, right: query2 ]
//
// Returns: A list of Maps. Each map describing one row.
// !!!! Each Map must contain a '__UID__' and '__NAME__' attribute.
// This is required to build a ConnectorObject.

log.info("Entering " + operation + " Script");

def sql = new Sql(connection);
def where = "";

if (filter != null) {

    def query = MapFilterVisitor.INSTANCE.accept(null, filter)
    //Need to handle the __UID__ in queries
    if (query.get("left").equalsIgnoreCase("__UID__") && objectClass.is("__ACCOUNT__")) query.put("left", "id");
    if (query.get("left").equalsIgnoreCase("__UID__") && objectClass.is("__GROUP__")) query.put("left", "id");
    if (query.get("left").equalsIgnoreCase("__UID__") && objectClass.is("organization")) query.put("left", "id")

    // We can use Groovy template engine to generate our custom SQL queries
    def engine = new groovy.text.SimpleTemplateEngine();

    def whereTemplates = [
            CONTAINS          : ' WHERE $left ${not ? "NOT " : ""}LIKE \'%$right%\'',
            ENDSWITH          : ' WHERE $left ${not ? "NOT " : ""}LIKE \'%$right\'',
            STARTSWITH        : ' WHERE $left ${not ? "NOT " : ""}LIKE \'$right%\'',
            EQUALS            : ' WHERE $left ${not ? "<>" : "="} \'$right\'',
            GREATERTHAN       : ' WHERE $left ${not ? "<=" : ">"} \'$right\'',
            GREATERTHANOREQUAL: ' WHERE $left ${not ? "<" : ">="} \'$right\'',
            LESSTHAN          : ' WHERE $left ${not ? ">=" : "<"} \'$right\'',
            LESSTHANOREQUAL   : ' WHERE $left ${not ? ">" : "<="} \'$right\''
    ]

    def wt = whereTemplates.get(query.get("operation"));
    def binding = [left: query.get("left"), right: query.get("right"), not: query.get("not")];
    def template = engine.createTemplate(wt).make(binding);
    where = template.toString();
    log.ok("Search WHERE clause is: " + where)
}

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        sql.eachRow("SELECT * FROM Users" + where, { row ->
            handler {
                uid row.id as String
                id row.uid
                attribute 'uid', row.uid
                attribute 'displayName', row.displayName
                attribute 'firstname', row.firstname
                attribute 'lastname', row.lastname
                attribute 'email', row.email
                attribute 'employeeNumber', row.employeeNumber
                attribute 'employeeType', row.employeeType
                attribute 'description', row.description
                attribute 'mobilePhone', row.mobilePhone
            }
        }
        );
        break

    case ObjectClass.GROUP:
        sql.eachRow("SELECT * FROM Groups" + where, { row ->
            handler {
                uid row.id as String
                id row.name
                attribute 'gid', row.gid
                attribute 'description', row.description
            }
        });
        break

    case ORG:
        sql.eachRow("SELECT * FROM Organizations" + where, { row ->
            handler {
                uid row.id as String
                id row.name
                attribute 'description', row.description
            }
        });
        break

    default:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" + objectClass)
}

return new SearchResult();