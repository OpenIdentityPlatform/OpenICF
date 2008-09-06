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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.identityconnectors.common.logging.Log;

import junit.framework.Assert;

/**
 * {@link Macro} implementation which generates the string randomly, the supported
 * characters are:
 * <ul><li># - numeric</li>
 *  <li>a - lowercase letter</li>
 *  <li>A - uppercase letter</li>
 *  <li>? - lowercase and uppercase letter</li>
 *  <li>. - any character</li>
 * </ul>
 * <p>Example: ${RANDOM, #####}, ${RANDOM, AAAAA##}</p>
 * 
 * @author Dan Vernon
 */
public class RandomMacro implements Macro {

    private static final Log LOG = Log.getLog(RandomMacro.class);
    private static Random rnd;
    
    /**
     * Initializes static random generator if not already initialized.
     */
    public RandomMacro() {
        synchronized (RandomMacro.class) {
            if (rnd == null) {
                rnd = new Random(System.currentTimeMillis());
            }
        }
    }
    
    /**
     * {@inheritDoc}     
     */
    public String getName() {
        return "RANDOM";
    }

    /**
     * {@inheritDoc}     
     */
    public Object resolve(Object[] parameters) {
        LOG.ok("enter");
        
        // should be two parameter
        Assert.assertEquals(2, parameters.length);
        
        // first parameter is macro name
        Assert.assertEquals(parameters[0], getName());
        
        // and the second must be a string
        Assert.assertTrue(parameters[1] instanceof String);
        String pattern = (String)parameters[1];
        
        // trim it and create the random string
        pattern = pattern.trim();
        String value = createRandomString(pattern, getDefaultCharacterSetMap());
        
        LOG.ok("''{0}'' macro with parameter ''{1}'' resolves to (''{2}'',''{3}'')", getName(), pattern, value.getClass().getName(), value.toString());        
        
        return value;
    }
    
    /**
     * Creates characters map
     * 
     * @return
     */
    private Map<Character, Set<Character>> getDefaultCharacterSetMap () {  
        Map<Character, Set<Character>> characterSetMap = new HashMap<Character, Set<Character>>();
        
        // these are the Sets used by the map.  For any character
        // in the macro, a get is done on the map with that character
        // as the key, and the Set returned represents the list of
        // characters to pick from randomly
        Set<Character> alpha_lower = new TreeSet<Character>();
        Set<Character> alpha_upper = new TreeSet<Character>();
        Set<Character> alpha_mixed = new TreeSet<Character>();
        Set<Character> alpha_numeric = new TreeSet<Character>();
        Set<Character> numeric = new TreeSet<Character>();

        // all lower case letters
        addRange(alpha_lower, 'a', 'z');
        // all upper case letters
        addRange(alpha_upper, 'A', 'Z');
        // all numbers
        addRange(numeric, '0', '9');
        //all lower case, upper case, and numbers
        alpha_numeric.addAll(alpha_lower);
        alpha_numeric.addAll(alpha_upper);
        alpha_numeric.addAll(numeric);
        // all lower case and upper case
        alpha_mixed.addAll(alpha_lower);
        alpha_mixed.addAll(alpha_upper);
        
        // setup the mappings
        characterSetMap.put(new Character('#'), numeric);
        characterSetMap.put(new Character('a'), alpha_lower);
        characterSetMap.put(new Character('A'), alpha_upper);
        characterSetMap.put(new Character('?'), alpha_mixed);
        characterSetMap.put(new Character('.'), alpha_numeric);
        
        return characterSetMap;
    }
    
    /**
     * Fills the Set with Characters
     * 
     * @param s {@link Set} to be filled
     * @param min
     * @param max
     */
    private void addRange(Set<Character> s, char min, char max) {
        if (max < min) {
            char temp = min;
            min = max;
            max = temp;
        }

        for (char i = min; i <= max; i++) {
            s.add(new Character(i));
        }
    }
    
    /**
     * Gets random character from the set
     * 
     * @param rnd
     * @param validChars
     * @return
     */
    private char randomCharacter(Random rnd, Set<Character> validChars) {
        int patternRange = validChars.size();
        int next = 0;
        synchronized (RandomMacro.class) {
            next = rnd.nextInt(patternRange);
        }
        Character[] charArray  = validChars.toArray(new Character[0]);
        Character charValue = charArray[next];
        return charValue.charValue();
    }

    /**
     * Generates random character
     * 
     * @param pattern
     * @param characterSetMap
     * @return
     */
    private String createRandomString(String pattern, Map<Character, Set<Character>> characterSetMap) {
        StringBuffer replacement = new StringBuffer();
        for (int i = 0; i < pattern.length(); i++) {
            Set<Character> characterSet = characterSetMap.get(new Character(pattern
                    .charAt(i)));
            Assert.assertNotNull(
                            "Unrecognized character in random template.  Unable to retrieve characterSet",
                            characterSet);

            replacement.append(randomCharacter(rnd, characterSet));
        }
        return replacement.toString();
    }
    
}
