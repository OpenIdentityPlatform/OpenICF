/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.identityconnectors.contract.data.macro;

import java.lang.reflect.Constructor;
import org.identityconnectors.common.logging.Log;
import org.junit.Assert;

/**
 * This macro takes two parameters, first is a class and second is the actual value.
 * The target class has to have contructor with the the String parameter.
 * 
 * @author Tomas Knappek
 */
public class ObjectMacro implements Macro {

    private static final Log LOG = Log.getLog(ObjectMacro.class);

    public String getName() {
        return "OBJECT";
    }

    public Object resolve(Object[] parameters) {

        LOG.ok("enter");

        // should be three parameters
        Assert.assertEquals(3, parameters.length);

        // first parameter is macro name
        Assert.assertEquals(parameters[0], getName());

        //second is the target class, has to be String
        Assert.assertTrue(parameters[1] instanceof String);
        String inClazz = (String) parameters[1];

        //second is the value, has to be String
        Assert.assertTrue(parameters[2] instanceof String);
        String inValue = (String) parameters[2];
        
        try {
            Class clazz = Class.forName(inClazz);
            Constructor c = clazz.getConstructor(String.class);
            
            return c.newInstance(inValue);
            
        } catch (Exception ex) {
            LOG.error(ex,"Unable to process the Object macro with class: '{{0}}' and value: '{{1}}'" , inClazz, inValue);
            throw new RuntimeException(ex);
        } 
        

    }
}
