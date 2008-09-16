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
package org.identityconnectors.contract.data.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import junit.framework.Assert;

/**
 * Expression is made of of:
 *    <ol>
 *    <li>a macro</li>
 *    <li>a string</li>
 *    <li>a combination of strings and macros that return strings (MUST return string though).
 *               this is really just a special case of a string</li>
 *    </ol>
 *  <p>Example of Expression:
 *     <ul><li>${RANDOM, A}</li>
 *         <li>${INTEGER, ${RANDOM, ##}}</li>
 *         <li>${FLOAT, ${RANDOM, #####}${LITERAL, .}${RANDOM, ##}}</li> </ul></p>
 * 
 * @author Dan Vernon
 */
public class Expression {
        
    /**
     * Encapsulates a position counter used for parsing
     * expressions.
     */
    class ExpressionContext {
        private int _currentPosition;
        
        ExpressionContext (int currentPosition) {
            setCurrentPosition(currentPosition);
        }
        
        public void setCurrentPosition(int currentPosition) {
            _currentPosition = currentPosition;            
        }
        
        public int getCurrentPosition() {
            return _currentPosition;
        }
    }

    String _expressionString = null;
    private ConcurrentMap<String, Macro> _macroRegistry = null;
    
    /**
     * constructor
     * 
     * @param macroRegistry
     * @param expressionString
     */
    public Expression(ConcurrentMap<String, Macro> macroRegistry, String expressionString) {
        _macroRegistry = macroRegistry;
        _expressionString = expressionString;
    }
    
    /**
     * Expression {@link String} which came as a constructor parameter
     * @return
     */
    public String getExpressionString() {
        return _expressionString;
    }
    
    /**
     * Resolve the Expression to value using {@link Macro}s
     * 
     * @return {@link Object} value
     */
    public Object resolveExpression() {
        // expression is made of of:
        //      1.  a macro
        //      2.  a string
        //      3.  a combination of strings and macros that return strings (MUST return string though).
        //              this is really just a special case of a string
             
        ExpressionContext context = new ExpressionContext(0);
        Object expressionReturn = null;
        
        // handle empty strings
        if (_expressionString.length()==0) {
            expressionReturn = _expressionString;
        }
        
        for(int i=context.getCurrentPosition();i<_expressionString.length();i++) {
            char ch = _expressionString.charAt(i);
            if (ch == '$' && i + 1 < _expressionString.length()
                    && _expressionString.charAt(i + 1) == '{') {
                int macroStart = i;
                i++;

                int closingBrace = findClosingBrace(_expressionString, i);
                Assert.assertFalse("Improperly formatted expression:  No closing brace found for expression",
                        (i == -1));
                // get the macro
                String macroString = _expressionString.substring(macroStart, closingBrace + 1);
                context.setCurrentPosition(macroStart);
                Object obj = processMacro(context, macroString);
                if (expressionReturn == null) {
                    expressionReturn = obj;
                } else {
                    expressionReturn = concatenate(expressionReturn, obj);
                }
                i += context.getCurrentPosition();                
            } else {
                String processedString = processString(context, _expressionString.substring(i));
                if(expressionReturn == null) {
                    expressionReturn = processedString;
                } else {
                    // fixes the case that you have expression "${MACRO, args}  ", where
                    // MACRO is macro that doesn't return String
                    // this could typically happen at the end of line
                    if (expressionReturn instanceof String || processedString.trim().length()>0) {
                        expressionReturn = concatenate(expressionReturn, processedString);
                    }
                }                
                i += context.getCurrentPosition();
            }
        }
        
        return expressionReturn;
    }
    
    /**
     * Concat two String objects
     * 
     * @param string1
     * @param string2
     * @return
     */
    private String concatenate(Object string1, Object string2) {
        String concatenatedStrings = null;
        
        // we can only append them if they are both strings
        Assert.assertTrue("Cannot concatenate " + string1 + " and " + string2, 
                ( (string1 instanceof String) && (string2 instanceof String) )
                );
        concatenatedStrings = (String)string1 + (String)string2;
        
        return concatenatedStrings;
    }
    
    /**
     * Helper function for finding closing brace
     * 
     * @param expressionText
     * @param i
     * @return
     */
    private int findClosingBrace(String expressionText, int i) {
        int braceLevel = 0;
        for(int index=i;index<expressionText.length();index++) {
            char ch = expressionText.charAt(index);
            switch(ch) {
            case '{':
                braceLevel++;
                break;
                
            case '}':
                braceLevel--;                
                if(braceLevel == 0) {
                    return index;
                }
                break;         
            }
        }
        return -1;
    }

    /**
     * Processes the Expression string. Some escaping could be done here, but currently is not.
     * 
     * @param context
     * @param patternSubstring
     * @return
     */
    private String processString(ExpressionContext context, String patternSubstring) {
        int index = 0;
        
        StringBuffer foundString = new StringBuffer();
        for(index=0;index<patternSubstring.length();index++) {
            char ch = patternSubstring.charAt(index);            
            foundString.append(ch);                        
        }
        
        context.setCurrentPosition(index);

        return foundString.toString();
    }
    
    /**
     * Processes the text using Macro
     * 
     * @param context
     * @param macroText
     * @return
     */
    private Object processMacro(ExpressionContext context, String macroText) {
        int index = 0;
        int embeddedMacroLevel = 0;
        List<String> parametersStrings = new ArrayList<String>();
        
        // make sure it's a valid macro
        Assert.assertTrue("Macro text not properly formated ${<macro} for " + macroText, 
                (macroText.startsWith("${")) && (macroText.endsWith("}")) );
        
        // TODO Could this be cleaner?
        // strip off ${ and }       
        macroText = macroText.substring(2, macroText.length() - 1);
        
        StringBuffer foundParameter = new StringBuffer();        
        // find the parameters by splitting on ','
        // treat everything between '${' and '}' as 
        // a single entity.
        for(index=0;index<macroText.length();index++) {
            char ch = macroText.charAt(index);
            switch(ch) {
            case '$':
                foundParameter.append(ch);
                
                // next character must be either a '$'
                // or a '{' or it's an error
                index++;
                Assert.assertTrue(index < macroText.length());
                ch = macroText.charAt(index);
                foundParameter.append(ch);
                if(ch == '{') {
                    embeddedMacroLevel++;
                }                
                break;
                
            case '}':
                if(embeddedMacroLevel > 0) {
                    embeddedMacroLevel--;
                }
                foundParameter.append(ch);
                break;
                
            case ',':
                if(embeddedMacroLevel == 0) {
                    if (parametersStrings.size() > 0 && parametersStrings.get(0).equals(LiteralMacro.MACRO_NAME)) {
                        parametersStrings.add(foundParameter.toString());
                    }
                    else {
                        parametersStrings.add(foundParameter.toString().trim());
                    }
                    foundParameter = new StringBuffer();
                } else {
                    foundParameter.append(ch);
                }
                break;
                
            default:
                foundParameter.append(ch);
                break;
            }
        }
        
        // add the last one in ... there may not be one in certain
        // cases, so check length first
        String lastParameter;
        if (parametersStrings.size() > 0 && parametersStrings.get(0).equals(LiteralMacro.MACRO_NAME)) {
            lastParameter = foundParameter.toString();
        }
        else {
            lastParameter = foundParameter.toString().trim();
        }        
        if(lastParameter.length() > 0) {            
            parametersStrings.add(lastParameter);
        }
        
        // handle special case - LITERAL macro 
        if (parametersStrings.size() > 0 && parametersStrings.get(0).equals(LiteralMacro.MACRO_NAME)) {
            StringBuffer buf = new StringBuffer();
            for (int i = 1; i < parametersStrings.size(); i++) {
                buf.append(parametersStrings.get(i));
                if (i + 1 < parametersStrings.size()) {
                    buf.append(",");
                }
            }

            // add in one for the trailing } that was removed earlier
            context.setCurrentPosition(index + 1);
            return buf.toString();
        }
        
        List<Object> resolvedParameters = new ArrayList<Object>();
        
        for(String parameterString : parametersStrings) {
            Expression expression = new Expression(_macroRegistry, parameterString);
            resolvedParameters.add(expression.resolveExpression());
        }
        
        // the resolved first parameter MUST be a string that represents
        // the macro name.
        String macroName = (String)resolvedParameters.get(0);
        Assert.assertNotNull("Macro name not found", macroName);
        Macro macro = _macroRegistry.get(macroName);
        Assert.assertNotNull("No macro defined for " + macroName, macro);
        // add in one for the trailing } that was removed earlier
        context.setCurrentPosition(index + 1); 
        return macro.resolve(resolvedParameters.toArray());
    }
}
