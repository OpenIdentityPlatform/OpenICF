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
package org.identityconnectors.contract.data;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import junit.framework.Assert;

import java.io.File;
import java.io.FileOutputStream;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.contract.data.macro.ArrayMacro;
import org.identityconnectors.contract.data.macro.ByteArrayMacro;
import org.identityconnectors.contract.data.macro.CharacterMacro;
import org.identityconnectors.contract.data.macro.Expression;
import org.identityconnectors.contract.data.macro.GetMacro;
import org.identityconnectors.contract.data.macro.ListMacro;
import org.identityconnectors.contract.data.macro.LiteralMacro;
import org.identityconnectors.contract.data.macro.Macro;
import org.identityconnectors.contract.data.macro.MapEntryMacro;
import org.identityconnectors.contract.data.macro.MapMacro;
import org.identityconnectors.contract.data.macro.NotSuppliedMacro;
import org.identityconnectors.contract.data.macro.NullMacro;
import org.identityconnectors.contract.data.macro.ObjectMacro;
import org.identityconnectors.contract.data.macro.RandomMacro;
import org.identityconnectors.contract.data.macro.SysPropsMacro;
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.framework.test.TestHelpers;


/**
 * Default implementation of {@link DataProvider}, it gets configuration from
 * config file set by "defaultdataprovider.propertyFile" JVM property. It uses
 * {@link Macro} implementation for processing the config entries.
 *
 * @author Dan Vernon
 */
public class DefaultDataProvider implements DataProvider {

    private static final String PARAM_PROPERTY_FILE = "defaultdataprovider.propertyFile";
    private static final String PARAM_PROPERTY_OUT_FILE = "test.parameters.outFile";
    private static final String GLOBAL_PROPERTY_FILE = "dataprovider-global.properties";

    private Map<String, Object> _data = new HashMap<String, Object>();
    private static final Log LOG = Log.getLog(DefaultDataProvider.class);
    private ConcurrentMap<String, Macro> _macroRegistry = new ConcurrentHashMap<String, Macro>();
    private File _propertyOutFile = null;

    /**
     * Constructor, reads the properties and initialize the {@link Macro}s
     */
    public DefaultDataProvider() {
        Properties props = new Properties();

        String propsFilename = System.getProperty(PARAM_PROPERTY_FILE);

        // get the property ouf file name if provided
        String pOut = System.getProperty(PARAM_PROPERTY_OUT_FILE);
        if (StringUtil.isNotBlank(pOut)) {
            try {
                _propertyOutFile = new File(pOut);
                if (!_propertyOutFile.exists()) {
                    _propertyOutFile.createNewFile();
                }                
                if (!_propertyOutFile.canWrite()) {
                    _propertyOutFile = null;
                    LOG.warn("Unable to write to ''{0}'' file, the test parameters will not be stored", pOut);
                } else {
                    LOG.info("Storing parameter values to ''{0}'', you can rerun the test with the same parameters later", pOut);
                }
            } catch (IOException iOException) {
                LOG.warn("Unable to create ''{0}'' file, the test parameters will not be stored", pOut);
            }
        }
        try {
            //load global properties
            Properties globalProp = IOUtil.getResourceAsProperties(getClass().getClassLoader(), GLOBAL_PROPERTY_FILE);
            props.putAll(globalProp);
        } catch (IOException ex) {
            LOG.warn("Cannot load global properties file ''{0}''", GLOBAL_PROPERTY_FILE);
        }
        
        // load property file if specified
        if (StringUtil.isNotBlank(propsFilename)) {
            
            LOG.ok("Loading ''{0}''", propsFilename);            
            try {                
                Properties specProp = IOUtil.loadPropertiesFile(propsFilename);
                props.putAll(specProp);

            } catch (IOException e) {
                LOG.error(e, "Unable to open propertyfile ''{0}''", propsFilename);
            } 
        } else {
            //load properties from TestHelper otherwise
            LOG.ok("Loading properties from TestHelper");
            Properties userProp = TestHelpers.getProperties();
            props.putAll(userProp);
        }

        Set<Entry<Object, Object>> propertySet = props.entrySet();
        for (Entry<Object, Object> entry : propertySet) {
            String value = (String) entry.getValue();
            String key = (String) entry.getKey();
            Object newValue = null;
            if (value != null) {
                newValue = value.trim();
                newValue = new Expression(_macroRegistry, value);
            }

            _data.put(key, newValue);
        }

        // default macros
        addMacro(new ListMacro());
        addMacro(new RandomMacro());
        addMacro(new GetMacro(this));
        addMacro(new LiteralMacro());
        addMacro(new ByteArrayMacro());
        addMacro(new SysPropsMacro());
        addMacro(new NotSuppliedMacro());
        addMacro(new MapEntryMacro());
        addMacro(new MapMacro());
        addMacro(new ObjectMacro());
        addMacro(new NullMacro());
        addMacro(new ArrayMacro());
        addMacro(new CharacterMacro());

    // load additional macros
    //loadMacros();
    }

    /**
     * add macro to the registry
     * @param macro {@link Macro}
     */
    private void addMacro(Macro macro) {
        _macroRegistry.put(macro.getName(), macro);
    }

    /**
     * load additional macros configued in the config file
     */
    @SuppressWarnings("unchecked")
    private void loadMacros() {
        try {
            List<String> macroList = (List<String>)getTestSuiteAttribute(
                    List.class.getName(), "macrosToLoad");
            for(String macroClassName : macroList) {
                Class<?> macroClass = Class.forName(macroClassName);
                Assert.assertNotNull("Could not get Class for " + macroClassName, macroClass);
                Object macro = macroClass.newInstance();
                Assert.assertNotNull("Could not create instance of " + macroClassName, macro);
                Assert.assertTrue("Object created from " + macroClassName +
                        " is not an instance of Macro",
                        macro instanceof Macro);
                addMacro((Macro)macro);

            }
        } catch (Exception ex) {
            Assert.fail("Error loading macros:  " + ex.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object get(String dataTypeName, String name, String componentName,
            int sequenceNumber) throws ObjectNotFoundException {
        // put the parameters in the Map ... this will fail if called recursively

        String shortTypeName = getShortTypeName(dataTypeName);

        Assert.assertFalse(_data.keySet().contains("param.sequenceNumber"));
        Assert.assertFalse(_data.keySet().contains("param.componentName"));
        Assert.assertFalse(_data.keySet().contains("param.name"));
        Assert.assertFalse(_data.keySet().contains("param.dataTypeName"));

        StringBuffer sbPath = new StringBuffer();
        if(sequenceNumber != -1) {
            sbPath.append(sequenceNumber);
            sbPath.append(".");
            _data.put("param.sequenceNumber", new Integer(sequenceNumber).toString());
        }

        sbPath.append(componentName);
        sbPath.append(".");
        sbPath.append(name);
        sbPath.append(".");
        sbPath.append(shortTypeName);
        LOG.info("getting data for ''{0}''", sbPath);

        _data.put("param.componentName", componentName);
        _data.put("param.name", name);
        _data.put("param.dataTypeName", shortTypeName);

        try {
            Object obj = get(sbPath.toString());
            LOG.info("Fully resolved ''{0}'' to value ''{1}''",
                    sbPath.toString(), obj);
            return obj;

        } catch (ObjectNotFoundException ex) {
            LOG.info(ex, "Unable to find data for ''{0}''", sbPath.toString());
            throw ex;
        } catch (Exception ex) {
            LOG.error(ex, "Error occured expanding macro");
        } finally {
            _data.remove("param.dataTypeName");
            _data.remove("param.name");
            _data.remove("param.componentName");
            _data.remove("param.sequenceNumber");

            Assert.assertFalse(_data.keySet().contains("param.sequenceNumber"));
            Assert.assertFalse(_data.keySet().contains("param.componentName"));
            Assert.assertFalse(_data.keySet().contains("param.name"));
            Assert.assertFalse(_data.keySet().contains("param.dataTypeName"));
        }

        // found nothing, so return nothing
        throw new ObjectNotFoundException("Can't find object for key:  " + name);

    }

    /**
     * {@inheritDoc}
     */
    public Object get(String dataTypeName, String name, String componentName)
            throws ObjectNotFoundException {

        return get(dataTypeName, name, componentName, -1);
    }

    /**
     * {@inheritDoc}
     */
    public String getString(String name, String componentName,
            int sequenceNumber) throws ObjectNotFoundException {
        return (String)get(String.class.getName(), name,
                componentName, sequenceNumber);
    }

    /**
     * {@inheritDoc}
     */
    public String getString(String name, String componentName)
            throws ObjectNotFoundException {
        return (String)get(String.class.getName(),
                name, componentName);
    }

    /**
     * {@inheritDoc}
     */
    public Object getTestSuiteAttribute(String typeName, String propName)
            throws ObjectNotFoundException {

        return get(propName + "." + "testsuite." + getShortTypeName(typeName));
    }

    /**
     * Gets the value by the name from the _data map. The functionality is as
     * the following: if the input name is "something.name", it tries to find
     * the value by full name, if not found it tries to find "name" value
     * recursively
     *
     * @param name key
     * @return value
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException if the value is not found
     */
    public Object get(String name) throws ObjectNotFoundException {
        try {
            return getAndResolve(name);
        } catch (ObjectNotFoundException ex) {
            throw new ObjectNotFoundException("Cannot find object: " + name);
        }
    }

    /**
     * Finds the value of specified property - recursive search is performed.
     * @param name Property name.
     * @return value
     * @throws ObjectNotFoundException if the value is not found
     */
    private Object getAndResolve(String name) throws ObjectNotFoundException {
        Object obj = recursiveGet(name);
        // The name passed in may be a macro, or it may have been
        // resolved by a more generic name (e.g. account.text),
        // so be certain it's in the map in it's resolved form.
        if (obj instanceof Expression) {
            Expression expression = (Expression) obj;
            try {
                obj = expression.resolveExpression();
                // handle the special case were we resolved
                // an expression, but the result of the expression
                // was to pretend we didn't find anything
                if (obj instanceof ObjectNotFoundException) {
                    throw (ObjectNotFoundException) obj;
                }
            } catch (Exception ex) {
                LOG.error("Caught exception while resolving expression ''{0}''", expression
                        .getExpressionString());
                throw new ObjectNotFoundException(ex);
            }
        }

        _data.put(name, obj);
        return obj;
    }

    /**
     * used by {@link DefaultDataProvider#get(java.lang.String) for recursive
     * get
     *
     * @param name key
     * @return value
     * @throws org.identityconnectors.contract.data.DataProvider.ObjectNotFoundException if the object is not found
     */
    private Object recursiveGet(String name) throws ObjectNotFoundException {
        if(!_data.containsKey(name)) {
            int separatorIndex = name.indexOf('.', 0);
            if(separatorIndex != -1) {
                separatorIndex++;
                if(separatorIndex < name.length()) {
                    return recursiveGet(name.substring(separatorIndex));
                }
            }
            throw new ObjectNotFoundException("Can't find object for key:  " + name);

        }

        Object obj = _data.get(name);
        return obj;
    }

    /**
     * {@inheritDoc}
     */
    public Object getConnectorAttribute(String typeName, String propName)
            throws ObjectNotFoundException {

        return get(propName + "." + "connector." + getShortTypeName(typeName));
    }

    /**
     * gets short name for the type, eg. java.lang.String returns string
     *
     * @param Type Name
     * @return Short Name
     */
    private String getShortTypeName(String typeName) {
        String shortName = typeName;

        if(typeName.equals(GuardedString.class.getName())) {
            shortName = "string";        
        } else if (typeName.equals("[B")) {
            shortName = "bytearray";        
        } else {
            int lindex = typeName.lastIndexOf(".");
            if (lindex != -1) {
                shortName = typeName.substring(lindex + 1);
                shortName = shortName.toLowerCase();
            } else {
                LOG.warn("Can't get short type for ''{0}''", typeName);
            }
        }

        return shortName;
    }


    /**
     * writes key, value to _propertyOutFile
     */
    private void writeDataToFile() {
        // do nothing if not having out file
        if (_propertyOutFile == null) {
            return;
        }
        Properties outFile = new Properties();
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(_propertyOutFile);
            outFile.load(fis);
            fis.close();
            fis = null;
            for (Entry<String, Object> entry : _data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String svalue = flatten(value);                
                LOG.ok("Putting ''{0}'',''{1}'' into outfile", key, svalue);

                outFile.put(key, svalue);
            }
            fos = new FileOutputStream(_propertyOutFile);
            outFile.store(fos, null);
        } catch (IOException ex) {
            LOG.warn("Unable to write to data file ''{0}''", _propertyOutFile.getAbsolutePath());
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ex) {
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ex) {
                }
            }
        }
    }
    
    /**
     * Returns passed value as String literal or Macro definition.
     */
    private String flatten(Object value) {
        String svalue = null;
        if (value instanceof Expression) {
            svalue = ((Expression) value).getExpressionString();
        } else if (value instanceof String) {
            svalue = String.format("${LITERAL,%s}", value);
        } else if (value instanceof Double) {
            svalue = String.format("${DOUBLE,%f}", value);
        } else if (value instanceof Float) {
            svalue = String.format("${FLOAT,%f}", value);
        } else if (value instanceof Integer) {
            svalue = String.format("${INTEGER,%d}", value);
        } else if (value instanceof byte[]) {
            svalue = String.format("${BYTEARRAY,%s}", new String((byte[]) value));
        } else if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object item : ((List)value)) {
                sb.append(",");
                sb.append(flatten(item));
            }
            svalue = String.format("${LIST%s}", sb.toString());
        } else if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Object, Object> entry: ((Map<Object,Object>)value).entrySet()) {
                String key = flatten(entry.getKey());
                String val = flatten(entry.getValue());
                sb.append(",");
                sb.append(String.format("${MAPENTRY, %s, %s}",key, val));
            }
            svalue = String.format("${MAP%s}", sb.toString());
        } else if (value instanceof ObjectNotFoundException) {
            svalue = "${NOTSUPPLIED}";
        } else {
            try {
                // try casting
                svalue = (String) value;
            }
            catch (ClassCastException ex) {
                // resolve as OBJECT macro
                svalue = String.format("${OBJECT, %s, %s}", value.getClass().getName(), value.toString());
            }
            if (svalue == null) svalue = "";
        }
        
        return svalue;
    }
    
    /**
     * Writes generated properties to a file if an appropriate property is set (not by default).
     */
    public void dispose() {    
        writeDataToFile();
    }
}
