/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import groovyx.net.http.HttpResponseDecorator
import org.identityconnectors.common.CollectionUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException
import org.identityconnectors.framework.common.exceptions.PermissionDeniedException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ObjectClass

public class Neo4JUtils {

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper()

    private static JsonFactory JSON_FACTORY = new JsonFactory()

    private static Log logger = Log.getLog(Neo4JUtils.class)

    private static final String ERRORS = "errors"
    private static final String DATA = "data"
    private static final String RESULTS = "results"
    public static final String RELATION = "__RELATION__"
    public static final String FROM = "__FROM__"
    public static final String FROM_LABEL = "__FROM_LABEL__"
    public static final String TO = "__TO__"
    public static final String TO_LABEL = "__TO_LABEL__"

    public static final String LABELS = "__LABEL__"

    static {
        JSON_FACTORY.setCodec(OBJECT_MAPPER)
    }


    public static Object getJsonValue(Object value) {
        return JsonOutput.toJson(value)
    }

 
    
    public static Object getValue(Object value) {
        if (value == null) {
            return "NULL"
        }

        if (value instanceof Collection) {
            return '[' + (value as Collection).collect { getValue(it) }.join(',') + ']'
        }

        if (value instanceof String || value instanceof Character) {
            return "'" + value + "'"
        }

        if (value instanceof Boolean) {
            return (value as Boolean) ? "TRUE" : "FALSE"
        }

        if (value instanceof Number) {
            return value
        }

        throw new InvalidAttributeValueException("Unsupported property type:" + value.class)
    }

    public static Map createNode(Set<Attribute> attributes) {
        def node = [:]
        attributes.each { attr ->
            if (!AttributeUtil.isSpecial(attr)) {
                if (attr.value != null && attr.value.any()) {
                    //Value can be String/Char/Number/Boolean or Array of these
                    switch (attr.value.size()) {
                        case 0: break
                        case 1:
                            node.(attr.name) = AttributeUtil.getSingleValue(attr)
                            break
                        default:
                            node.(attr.name) = attr.value
                    }
                }
            }
        }
        return node;
    }

    public static String fetchLabels(ObjectClass objectClass, Attribute attribute) {
        def labelSet = CollectionUtil.newCaseInsensitiveSet();

        if (null != attribute) {
            attribute.value.each {
                if (it instanceof String) {
                    labelSet.add(it)
                } else {
                    throw new InvalidAttributeValueException("Expect '__LABELS__' to be String")
                }
            }
        }

        if (ObjectClass.ACCOUNT.is(objectClass.objectClassValue)) {
            labelSet.add("User")
        } else if (!AttributeUtil.isSpecialName(objectClass.objectClassValue)) {
            labelSet.add(objectClass.objectClassValue)
        }

        if (labelSet.isEmpty()) {
            throw new InvalidAttributeValueException("Expect '__LABELS__' is required")
        }

        def labels = ""
        labelSet.each {
            labels = "${labels}:`${it}`"
        }
        return labels;
    }

    public static Object parserResponse(HttpResponseDecorator resp, Closure closure) {
        if (resp.getEntity() == null || resp.getEntity().getContentLength() == 0)
            logger.ok("Empty response")
        else {
            JsonParser parser = JSON_FACTORY.createParser(resp.getEntity().getContent());
            try {
                while (!parser.isClosed()) {
                    // get the token
                    JsonToken token = parser.nextToken();
                    // if its the last token then we are done
                    if (token == null)
                        break;

                    if (JsonToken.FIELD_NAME.equals(token)) {

                        if (RESULTS.equals(parser.getCurrentName())) {
                            // The first token should be start of array
                            token = parser.nextToken();
                            if (!JsonToken.START_ARRAY.equals(token)) {
                                continue;
                            }
                            // The next token should be {
                            token = parser.nextToken();
                            if (!JsonToken.START_OBJECT.equals(token)) {
                                continue;
                            }

                            while (token != null && !JsonToken.END_OBJECT.equals(token)) {
                                token = parser.nextToken();

                                if (JsonToken.FIELD_NAME.equals(token) && DATA.equals(parser.getCurrentName())) {

                                    // The first token should be start of array
                                    token = parser.nextToken();
                                    if (!JsonToken.START_ARRAY.equals(token)) {
                                        break;
                                    }
                                    // The next token should be {
                                    token = parser.nextToken();
                                    if (!JsonToken.START_OBJECT.equals(token)) {
                                        break;
                                    }
                                    def data = parser.readValuesAs(Map.class)

                                    while (data.hasNext()) {
                                        def ret = closure(data.next())
                                        if (ret instanceof Boolean && ret) {
                                            continue
                                        }
                                        return ret
                                    }
                                }
                            }

                        } else if (ERRORS.equals(parser.getCurrentName())) {
                            // The first token should be start of array
                            token = parser.nextToken();
                            if (!JsonToken.START_ARRAY.equals(token)) {
                                continue;
                            }
                            // The next token should be {
                            token = parser.nextToken();
                            if (!JsonToken.START_OBJECT.equals(token)) {
                                continue;
                            }

                            def data = parser.readValuesAs(Map.class)
                            if (data.hasNext()) {
                                def error = data.next();
                                if ("Neo.ClientError.Schema.ConstraintViolation".equals(error.code)) {
                                    throw new AlreadyExistsException(error.message);
                                } else if ("Neo.ClientError.Schema.ConstraintAlreadyExists".equals(error.code)) {
                                    throw new AlreadyExistsException(error.message);
                                } else if ("Neo.ClientError.Statement.EntityNotFound".equals(error.code)) {
                                    throw new UnknownUidException(error.message);
                                } else if ("Neo.ClientError.Security.AuthenticationFailed".equals(error.code)) {
                                    throw new InvalidCredentialException(error.message);
                                } else if ("Neo.ClientError.Security.AuthorizationFailed".equals(error.code)) {
                                    throw new PermissionDeniedException(error.message);
                                } else if ("Neo.ClientError.Security.AuthenticationRateLimit".equals(error.code)) {
                                    throw new ConnectorSecurityException(error.message);
                                } else if ("Neo.ClientError.Request.InvalidFormat".equals(error.code)) {
                                    throw new InvalidAttributeValueException(error.message);
                                } else if (null != error.code) {
                                    throw new ConnectorException(error.code + " " + error.message)
                                }
                            }
                        }
                    }
                }
            } finally {
                parser.close();
            }
        }
        return null
    }

}