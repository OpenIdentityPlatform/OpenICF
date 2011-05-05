/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package com.forgerock.openicf.xml.tests;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.AssertJUnit;
import com.forgerock.openicf.xml.XMLConfiguration;
import java.io.File;

public class XMLConfigurationTests {

    private XMLConfiguration config;

    @BeforeMethod
    public void init() {
        config = new XMLConfiguration();
    }

    @Test
    public void shouldGetXmlFilepathFromConfiguration() {
        final File filePath = new File("users.xml");

        config.setXmlFilePath(filePath);
        AssertJUnit.assertEquals(filePath, config.getXmlFilePath());
    }

    @Test
    public void shouldGetICFXsdFilepathFromConfiguration() {
        final File xsdPath = new File("xsdRi.xsd");

        config.setXsdIcfFilePath(xsdPath);
        AssertJUnit.assertEquals(xsdPath, config.getXsdIcfFilePath());
    }

    @Test
    public void shouldGetXsdFilepathFromConfiguration() {
        final File xsdPath = new File("xsdTest.xsd");

        config.setXsdFilePath(xsdPath);
        AssertJUnit.assertEquals(xsdPath, config.getXsdFilePath());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenValidatingWithNullFilepath() {
        config.setXmlFilePath(null);
        config.validate();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentExceptionWhenValidatingWithBlankFilepath() {
        config.setXmlFilePath(new File(""));
        config.validate();
    }
}
