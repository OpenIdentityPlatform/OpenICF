/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 ForgeRock AS. All rights reserved.
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
 */
package org.forgerock.openicf.csvfile;

import static org.testng.Assert.assertEquals;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.test.common.TestHelpers;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Attempts to test the {@link CSVFileConnector} with the framework.
 * 
 */
public class CSVFileConnectorTests {

    // set up logging
    private static final Log log = Log.getLog(CSVFileConnectorTests.class);

    @Test(dataProvider = "provideNumbers")
    public void exampleTest1(CSVFileConfiguration config) throws Exception {

        FileWriter f2 = new FileWriter(config.getFilePath(), false);
        f2.write(new StringBuilder("uid").append(config.getFieldDelimiter()).append(
                OperationalAttributes.PASSWORD_NAME).append(config.getFieldDelimiter()).append(
                "fullName").append(config.getFieldDelimiter()).append("groups").toString());
        f2.close();

        final ConnectorFacade facade = getFacade(config);

        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("foo01"));
        createAttributes.add(AttributeBuilder.build("fullName", "Foo Bar"));
        createAttributes.add(AttributeBuilder.buildPassword("Password".toCharArray()));
        createAttributes.add(AttributeBuilder.build("groups", "sample1", "sample2"));

        Uid uid = facade.create(ObjectClass.ACCOUNT, createAttributes, null);
        Assert.assertNotNull(uid);

        ConnectorObject co = facade.getObject(ObjectClass.ACCOUNT, uid, null);

        assertEquals(co.getUid().getUidValue(), "foo01");
        assertEquals(co.getName().getNameValue(), "foo01");
        assertEquals(co.getAttributeByName("groups").getValue(), Arrays.asList(new String[] {
            "sample1", "sample2" }));

        facade.delete(ObjectClass.ACCOUNT, uid, null);

        Assert.assertNull(facade.getObject(ObjectClass.ACCOUNT, uid, null));
    }

    @DataProvider(name = "provideNumbers")
    public Iterator<Object[]> provideData() throws Exception {
        List<Object[]> tests = new ArrayList<Object[]>();
        
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("connector-case1.csv"));
        config.setFieldDelimiter("*");
        config.setUsingMultivalue(true);
        config.setUniqueAttribute("uid");
        config.setNameAttribute("uid");
        config.setPasswordAttribute(OperationalAttributes.PASSWORD_NAME);        
        tests.add(new Object[]{config});
        
        config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("connector-case2.csv"));
        config.setMultivalueDelimiter("$");
        config.setUsingMultivalue(true);
        config.setUniqueAttribute("uid");
        config.setNameAttribute("uid");
        config.setPasswordAttribute(OperationalAttributes.PASSWORD_NAME);
        tests.add(new Object[]{config});
        
        return tests.iterator();
    }

    protected ConnectorFacade getFacade(CSVFileConfiguration config) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        // **test only**
        APIConfiguration impl = TestHelpers.createTestConfiguration(CSVFileConnector.class, config);
        return factory.newInstance(impl);
    }
}
