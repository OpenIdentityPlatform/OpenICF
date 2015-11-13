/*
 * Copyright 2003-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.forgerock.openicf.connectors.scriptedcrest;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourceResponse;

import groovy.json.JsonException;
import groovy.json.JsonLexer;
import groovy.json.JsonToken;
import groovy.lang.Closure;
import static groovy.json.JsonTokenType.*;

/**
 * JSON slurper which parses text or reader content into a data structure of lists and maps.
 * <p/>
 * Example usage: <code><pre>
 * def slurper = new JsonSlurper()
 * def result = slurper.parseText('{"person":{"name":"Guillaume","age":33,"pets":["dog","cat"]}}')
 * <p/>
 * assert result.person.name == "Guillaume"
 * assert result.person.age == 33
 * assert result.person.pets.size() == 2
 * assert result.person.pets[0] == "dog"
 * assert result.person.pets[1] == "cat"
 * </pre></code>
 *
 * @author Guillaume Laforge
 * @since 1.8.0
 */
public class StreamingJsonSlurper {

    /**
     * Parse a JSON data structure from content from a reader
     *
     * @param reader
     *         reader over a JSON content
     * @return a data structure of lists and maps
     */
    public Object parse(Reader reader, Closure handler, Closure<Object> callback) {
        Object content = null;

        JsonLexer lexer = new JsonLexer(reader);

        JsonToken token = lexer.nextToken();
        if (token.getType() == OPEN_CURLY) {
            content = parseObject(lexer, handler);
        } else if (token.getType() == OPEN_BRACKET) {
            parseArray(lexer, handler);
        } else {
            throw new JsonException("A JSON payload should start with " + OPEN_CURLY.getLabel()
                    + " or " + OPEN_BRACKET.getLabel() + ".\n" + "Instead, '" + token.getText()
                    + "' was found " + "on line: " + token.getStartLine() + ", " + "column: "
                    + token.getStartColumn());
        }

        if (null != callback) {
            callback.call(lexer, content);
        }
        return content;
    }

    public Object parse(HttpEntity entity, Closure handler, Closure<Object> callback) {
        Reader reader = null;
        try {
            return parse(IOGroovyMethods.newReader(entity.getContent()), handler, callback);
        } catch (IOException ioe) {
            throw new JsonException("Unable to process Entity Content", ioe);
        } finally {
            if (reader != null) {
                DefaultGroovyMethodsSupport.closeWithWarning(reader);
            }
        }
    }

    /**
     * Parse an array from the lexer
     *
     * @param lexer
     *         the lexer
     * @return a list of JSON values
     */
    @SuppressWarnings("unchecked")
    private List parseArray(JsonLexer lexer, Closure handler) {
        List content = new ArrayList();

        JsonToken currentToken;

        for (; ; ) {
            currentToken = lexer.nextToken();

            if (currentToken == null) {
                throw new JsonException("Expected a value on line: " + lexer.getReader().getLine()
                        + ", " + "column: " + lexer.getReader().getColumn() + ".\n"
                        + "But got an unterminated array.");
            }

            if (currentToken.getType() == OPEN_CURLY) {
                if (null != handler) {
                    handler.call(parseObject(lexer, null));
                } else {
                    content.add(parseObject(lexer, handler));
                }
            } else if (currentToken.getType() == OPEN_BRACKET) {
                content.add(parseArray(lexer, handler));
            } else if (currentToken.getType().ordinal() >= NULL.ordinal()) {
                content.add(currentToken.getValue());
            } else if (currentToken.getType() == CLOSE_BRACKET) {
                return content;
            } else {
                throw new JsonException("Expected a value, an array, or an object " + "on line: "
                        + currentToken.getStartLine() + ", " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }

            currentToken = lexer.nextToken();

            if (currentToken == null) {
                throw new JsonException("Expected " + CLOSE_BRACKET.getLabel() + " " + "or "
                        + COMMA.getLabel() + " " + "on line: " + lexer.getReader().getLine() + ", "
                        + "column: " + lexer.getReader().getColumn() + ".\n"
                        + "But got an unterminated array.");
            }

            // Expect a comma for an upcoming value
            // or a closing bracket for the end of the array
            if (currentToken.getType() == CLOSE_BRACKET) {
                break;
            } else if (currentToken.getType() != COMMA) {
                throw new JsonException("Expected a value or " + CLOSE_BRACKET.getLabel() + " "
                        + "on line: " + currentToken.getStartLine() + " " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }
        }

        return content;
    }

    /**
     * Parses an object from the lexer
     *
     * @param lexer
     *         the lexer
     * @return a Map representing a JSON object
     */
    private Map parseObject(JsonLexer lexer, Closure handler) {
        Map content = new HashMap();

        JsonToken previousToken = null;
        JsonToken currentToken = null;

        for (; ; ) {
            currentToken = lexer.nextToken();

            if (currentToken == null) {
                throw new JsonException("Expected a String key on line: "
                        + lexer.getReader().getLine() + ", " + "column: "
                        + lexer.getReader().getColumn() + ".\n" + "But got an unterminated object.");
            }

            // expect a string key, or already a closing curly brace

            if (currentToken.getType() == CLOSE_CURLY) {
                return content;
            } else if (currentToken.getType() != STRING) {
                throw new JsonException("Expected " + STRING.getLabel() + " key " + "on line: "
                        + currentToken.getStartLine() + ", " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }

            String mapKey = (String) currentToken.getValue();

            currentToken = lexer.nextToken();

            if (currentToken == null) {
                throw new JsonException("Expected a " + COLON.getLabel() + " " + "on line: "
                        + lexer.getReader().getLine() + ", " + "column: "
                        + lexer.getReader().getColumn() + ".\n" + "But got an unterminated object.");
            }

            // expect a colon between the key and value pair

            if (currentToken.getType() != COLON) {
                throw new JsonException("Expected " + COLON.getLabel() + " " + "on line: "
                        + currentToken.getStartLine() + ", " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }

            currentToken = lexer.nextToken();

            if (currentToken == null) {
                throw new JsonException("Expected a value " + "on line: "
                        + lexer.getReader().getLine() + ", " + "column: "
                        + lexer.getReader().getColumn() + ".\n" + "But got an unterminated object.");
            }

            // value can be an object, an array, a number, string, boolean or
            // null values

            if (currentToken.getType() == OPEN_CURLY) {
                content.put(mapKey, parseObject(lexer, null));
            } else if (currentToken.getType() == OPEN_BRACKET) {
                if (mapKey.equals(QueryResponse.FIELD_RESULT)) {
                    content.put(mapKey, parseArray(lexer, handler));
                } else {
                    content.put(mapKey, parseArray(lexer, null));
                }
            } else if (currentToken.getType().ordinal() >= NULL.ordinal()) {
                content.put(mapKey, currentToken.getValue());
            } else {
                throw new JsonException("Expected a value, an array, or an object " + "on line: "
                        + currentToken.getStartLine() + ", " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }

            previousToken = currentToken;
            currentToken = lexer.nextToken();

            // premature end of the object

            if (currentToken == null) {
                throw new JsonException("Expected " + CLOSE_CURLY.getLabel() + " or "
                        + COMMA.getLabel() + " " + "on line: " + previousToken.getEndLine() + ", "
                        + "column: " + previousToken.getEndColumn() + ".\n"
                        + "But got an unterminated object.");
            }

            // Expect a comma for an upcoming key/value pair
            // or a closing curly brace for the end of the object
            if (currentToken.getType() == CLOSE_CURLY) {
                break;
            } else if (currentToken.getType() != COMMA) {
                throw new JsonException("Expected a value or " + CLOSE_CURLY.getLabel() + " "
                        + "on line: " + currentToken.getStartLine() + ", " + "column: "
                        + currentToken.getStartColumn() + ".\n" + "But got '"
                        + currentToken.getText() + "' instead.");
            }
        }

        return content;
    }
}
