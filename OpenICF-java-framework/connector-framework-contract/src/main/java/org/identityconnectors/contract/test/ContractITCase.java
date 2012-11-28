/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock Inc. All rights reserved.
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

package org.identityconnectors.contract.test;

import org.testng.ITestContext;
import org.testng.annotations.Factory;
import org.testng.annotations.Parameters;
import com.google.inject.Guice;
import com.google.inject.Injector;


/**
 * A ContractITCase is the factory of the OpenICF connector contract tests.
 *
 * @author Laszlo Hordos
 */
public class ContractITCase {

    /*
    <testConfig>default</testConfig>
    <connectorName>org.forgerock.openicf.connectors.BasicConnector</connectorName>
    <bundleJar>target/basic-connector-1.1.0.0-SNAPSHOT.jar</bundleJar>
    <bundleName>org.forgerock.openicf.connectors.basic-connector</bundleName>
    <bundleVersion>1.1.0.0-SNAPSHOT</bundleVersion>
     */
    @Factory
    public Object[] createInstances(ITestContext context) {
        Class[] testClasses = new Class[]{
                AttributeTests.class,
                AuthenticationApiOpTests.class,
                ConfigurationTests.class,
                CreateApiOpTests.class,
                DeleteApiOpTests.class,
                GetApiOpTests.class,
                MultiOpTests.class,
                SchemaApiOpTests.class,
                ScriptOnConnectorApiOpTests.class,
                ScriptOnResourceApiOpTests.class,
                SearchApiOpTests.class,
                SyncApiOpTests.class,
                TestApiOpTests.class,
                UpdateApiOpTests.class,
                ValidateApiOpTests.class
        };
        Injector injector = Guice.createInjector(new FrameworkModule(ConnectorHelper.createDataProvider()));
        Object[] result = new Object[testClasses.length];
        for (int i = 0; i < testClasses.length; i++) {
            result[i] = injector.getInstance(testClasses[i]);
        }
        return result;
    }
}
