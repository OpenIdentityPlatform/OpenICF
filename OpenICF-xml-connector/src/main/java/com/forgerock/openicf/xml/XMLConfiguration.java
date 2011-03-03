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
package com.forgerock.openicf.xml;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class XMLConfiguration extends AbstractConfiguration {

    private String xmlFilePath = null;
    private String xsdFilePath = null;
    private String xsdIcfFilePath = null;

    public XMLConfiguration() {
    }

    @ConfigurationProperty(displayMessageKey = "XML_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XML_FILEPATH_PROPERTY_HELP", confidential = false)
    public String getXmlFilePath() {
        return xmlFilePath;
    }

    public void setXmlFilePath(String xmlFilePath) {
        this.xmlFilePath = xmlFilePath;
    }

    @ConfigurationProperty(displayMessageKey = "XSD_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XSD_FILEPATH_PROPERTY_HELP", confidential = false)
    public String getXsdFilePath() {
        return this.xsdFilePath;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
    }

    @ConfigurationProperty(displayMessageKey = "XSD_ICF_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XSD_ICF_FILEPATH_PROPERTY_HELP", confidential = false)
    public String getXsdIcfFilePath() {
        return this.xsdIcfFilePath;
    }

    public void setXsdIcfFilePath(String xsdFilePath) {
        this.xsdIcfFilePath = xsdFilePath;
    }

    public void validate() {
        Assertions.blankCheck(xmlFilePath, "xmlFilePath");
        Assertions.blankCheck(xsdFilePath, "xsdFilePath");
        Assertions.blankCheck(xsdIcfFilePath, "xsdIcfFilePath");
    }
}
