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
package org.identityconnectors.contract.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.contract.exceptions.ObjectNotFoundException;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.SchemaApiOp;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;

/**
 * Contract test of {@link SchemaApiOp} operation.
 * 
 * @author Zdenek Louzensky
 *
 */
public class SchemaApiOpTests extends AbstractSimpleTest {

    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(SchemaApiOpTests.class);
    private static final String TEST_NAME = "Schema";
    
    /*
     * Properties prefixes:
     * it's added .testsuite.${type.name} after the prefix
     */
    private static final String SUPPORTED_OBJECT_CLASSES_PROPERTY_PREFIX = "oclasses." + TEST_NAME;
    private static final String SUPPORTED_OPERATIONS_PROPERTY_PREFIX = "operations." + TEST_NAME;

    /*
     * AttributeInfo field names used in property configuration:
     */
    private static final String ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT = "returnedByDefault";
    private static final String ATTRIBUTE_FIELD_MULTI_VALUE = "multiValue";
    private static final String ATTRIBUTE_FIELD_REQUIRED = "required";
    private static final String ATTRIBUTE_FIELD_CREATEABLE = "createable";
    private static final String ATTRIBUTE_FIELD_UPDATEABLE = "updateable";
    private static final String ATTRIBUTE_FILED_READABLE = "readable";
    private static final String ATTRIBUTE_FIELD_TYPE = "type";

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return SchemaApiOp.class;
    }
    
    /**
     * Tests that the schema doesn't contain {@link Uid}
     */
    @Test
    public void testUidNotPresent() {
        final Schema schema = getConnectorFacade().schema();
        Set<ObjectClassInfo> ocInfos = schema.getObjectClassInfo();
        for (ObjectClassInfo ocInfo : ocInfos) {
            Set<AttributeInfo> attInfos = ocInfo.getAttributeInfo();
            for (AttributeInfo attInfo : attInfos) {
                //ensure there is not Uid present
                assertTrue("Uid can't be present in connector Schema!", !attInfo.is(Uid.NAME));
            }
        }
    }

    /**
     * Tests that returned schema by connector is the same as expected schema to be returned.
     */
    @Test
    public void testSchemaValidity() {
        final Schema schema = getConnectorFacade().schema();
        String msg = null;

        // list of expected object classes
        List<String> expOClasses = (List<String>) getTestPropertyOrFail(List.class.getName(),
                SUPPORTED_OBJECT_CLASSES_PROPERTY_PREFIX);

        // iterate over object classes and check that were expected and check
        // their attributes
        for (ObjectClassInfo ocInfo : schema.getObjectClassInfo()) {
            msg = "Schema returned object class %s that is not expected to be suported.";
            assertTrue(String.format(msg, ocInfo.getType()), 
                    expOClasses.contains(ocInfo.getType()));

            // list of expected attributes for the object class
            List<String> expAttrs = (List<String>) getTestPropertyOrFail(List.class.getName(),
                    "attributes." + ocInfo.getType() + "."
                            + SUPPORTED_OBJECT_CLASSES_PROPERTY_PREFIX);
            
            // check object class attributes
            for (AttributeInfo attr : ocInfo.getAttributeInfo()) {
                msg = "Object class %s contains unexpected attribute: %s.";
                assertTrue(String.format(msg, ocInfo.getType(), attr.getName()),
                        expAttrs.contains(attr.getName()));

                // expected attribute values
                Map<String, String> expAttrValues = (Map<String, String>) getTestPropertyOrFail(
                        Map.class.getName(), attr.getName() + ".attribute." + ocInfo.getType()
                                + "." + SUPPORTED_OBJECT_CLASSES_PROPERTY_PREFIX);

                // check all attribute's fields
                checkAttributeValues(ocInfo, attr, expAttrValues);
            }
            
            // check that there shouldn't be more attributes
            msg = "Schema returned less attributes for object class %s "
                + ", expected: %d, returned: %d";
            assertTrue(String.format(msg, ocInfo.getType(), expAttrs.size(), ocInfo.getAttributeInfo().size()),
                expAttrs.size() == ocInfo.getAttributeInfo().size());
        }
        
        msg = "Schema returned less supported object classes, expected: %d, returned %d.";
        assertTrue(String.format(msg, expOClasses.size(), schema.getObjectClassInfo().size()),
                expOClasses.size() == schema.getObjectClassInfo().size());

        // expected object classes supported by operations
        Map<String, List<String>> expOperations = (Map<String, List<String>>) getTestPropertyOrFail(
                Map.class.getName(), SUPPORTED_OPERATIONS_PROPERTY_PREFIX);
        Map<Class<? extends APIOperation>, Set<ObjectClassInfo>> supportedOperations = schema
                .getSupportedObjectClassesByOperation();

        // iterate over operations
        for (Class<? extends APIOperation> operation : supportedOperations.keySet()) {
            msg = "Schema returned unexpected operation: %s.";
            assertTrue(String.format(msg, operation.getSimpleName()),
                    expOperations.containsKey(operation.getSimpleName()));

            // expected object classes supported by the operation
            List<String> expOClassesByOp = expOperations.get(operation.getSimpleName());
            assertNotNull(expOClassesByOp);
            
            // check that all operations are expected
            for (ObjectClassInfo ocInfo : supportedOperations.get(operation)) {
                msg = "Operation %s supports unexpected object class: %s."; 
                assertTrue(String.format(msg, operation.getSimpleName(), ocInfo.getType()),
                        expOClassesByOp.contains(ocInfo.getType()));
            }
            
            msg =  "Schema returned less object classes supported by operation %s, expected %d, returned %d.";
            assertTrue(String.format(msg, operation.getSimpleName(), expOClassesByOp.size(), supportedOperations.get(operation).size()),                    
                    expOClassesByOp.size() == supportedOperations.get(operation).size());
        }
        
        // check if there shouldn't be more opearations
        msg = "Schema returned less operations, expected: %s, returned %s.";
        assertTrue(String.format(msg, expOperations.keySet(), supportedOperations.keySet()),
                expOperations.size() == supportedOperations.size());

    }

    /**
     * Checks that attribute values are the same as expectedValues.
     */
    private void checkAttributeValues(ObjectClassInfo ocInfo, AttributeInfo attribute,
            Map<String, String> expectedValues) {        
        // check that all attributes are provided
        String msg = "Missing property definition for field '%s' of attribute '" + attribute.getName()
                        + "' in object class " + ocInfo.getType();
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_TYPE), 
                expectedValues.get(ATTRIBUTE_FIELD_TYPE));
        assertNotNull(String.format(msg, ATTRIBUTE_FILED_READABLE), 
                expectedValues.get(ATTRIBUTE_FILED_READABLE));
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_CREATEABLE), 
                expectedValues.get(ATTRIBUTE_FIELD_CREATEABLE));
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_UPDATEABLE), 
                expectedValues.get(ATTRIBUTE_FIELD_UPDATEABLE));
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_REQUIRED), 
                expectedValues.get(ATTRIBUTE_FIELD_REQUIRED));
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_MULTI_VALUE), 
                expectedValues.get(ATTRIBUTE_FIELD_MULTI_VALUE));
        assertNotNull(String.format(msg, ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT), 
                expectedValues.get(ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT));

        msg = "Object class '" + ocInfo.getType() + "', attribute '" + attribute.getName()
                + "': field '%s' expected value is '%s', but returned '%s'.";        
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_TYPE, expectedValues
                .get(ATTRIBUTE_FIELD_TYPE), attribute.getType().getName()), 
                expectedValues.get(ATTRIBUTE_FIELD_TYPE), attribute.getType().getName());
        assertEquals(String.format(msg, ATTRIBUTE_FILED_READABLE, new Boolean(expectedValues
                .get(ATTRIBUTE_FILED_READABLE)), attribute.isReadable()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FILED_READABLE)), attribute.isReadable());
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_CREATEABLE, new Boolean(expectedValues
                .get(ATTRIBUTE_FIELD_CREATEABLE)), attribute.isCreateable()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FIELD_CREATEABLE)), attribute.isCreateable());
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_UPDATEABLE, new Boolean(expectedValues
                .get(ATTRIBUTE_FIELD_UPDATEABLE)), attribute.isUpdateable()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FIELD_UPDATEABLE)), attribute.isUpdateable());
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_REQUIRED, new Boolean(expectedValues
                .get(ATTRIBUTE_FIELD_REQUIRED)), attribute.isRequired()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FIELD_REQUIRED)), attribute.isRequired());
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_MULTI_VALUE, new Boolean(expectedValues
                .get(ATTRIBUTE_FIELD_MULTI_VALUE)), attribute.isMultiValue()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FIELD_MULTI_VALUE)), attribute.isMultiValue());
        assertEquals(String.format(msg, ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT, new Boolean(
                expectedValues.get(ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT)), attribute.isReturnedByDefault()), 
                new Boolean(expectedValues.get(ATTRIBUTE_FIELD_RETURNED_BY_DEFAULT)), attribute.isReturnedByDefault());
    }

    /**
     * Returns property value or fails test if property is not defined.
     */
    private Object getTestPropertyOrFail(String typeName, String propName) {
        Object propValue = null;

        try {
            propValue = getDataProvider().getTestSuiteAttribute(typeName, propName);
        } catch (ObjectNotFoundException ex) {
            fail("Property definition not found: " + ex.getMessage());
        }
        assertNotNull(propValue);

        return propValue;
    }

}
