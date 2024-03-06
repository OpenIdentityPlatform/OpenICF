/*
 *
 * Copyright (c) 2010-2012 ForgeRock Inc. All Rights Reserved
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
package org.forgerock.openicf.connectors.xml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URISyntaxException;
import java.net.URL;

import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class XMLConfiguration extends AbstractConfiguration {

    private File xmlFilePath = null;
    private File xsdFilePath = null;
    private File xsdIcfFilePath = null;
    private boolean createFileIfNotExists = false;

    public XMLConfiguration() {
        try {
            URL defaultSchema = XMLConfiguration.class.getResource("resource-schema-1.xsd");
            if (null != defaultSchema) {
                xsdIcfFilePath = new File(defaultSchema.toURI());
            }
        } catch (URISyntaxException e) {
            // Should never happen.
            throw new UndeclaredThrowableException(e);
        }
    }

    @ConfigurationProperty(displayMessageKey = "XML_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XML_FILEPATH_PROPERTY_HELP", required = true)
    public File getXmlFilePath() {
        return xmlFilePath;
    }

    public void setXmlFilePath(File xmlFilePath) {
        this.xmlFilePath = xmlFilePath;
    }

    @ConfigurationProperty(displayMessageKey = "XSD_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XSD_FILEPATH_PROPERTY_HELP", required = true)
    public File getXsdFilePath() {
        return this.xsdFilePath;
    }

    public void setXsdFilePath(File xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
    }

    @ConfigurationProperty(displayMessageKey = "XSD_ICF_FILEPATH_PROPERTY_DISPLAY", helpMessageKey = "XSD_ICF_FILEPATH_PROPERTY_HELP")
    public File getXsdIcfFilePath() {
        return this.xsdIcfFilePath;
    }

    public void setXsdIcfFilePath(File xsdFilePath) {
        this.xsdIcfFilePath = xsdFilePath;
    }

    @ConfigurationProperty(displayMessageKey = "CREATE_FILE_IF_NOT_EXISTS_DISPLAY", helpMessageKey = "CREATE_FILE_IF_NOT_EXISTS_HELP")
    public boolean isCreateFileIfNotExists() {
        return createFileIfNotExists;
    }

    public void setCreateFileIfNotExists(boolean createFileIfNotExists) {
        this.createFileIfNotExists = createFileIfNotExists;
    }

    public void validate() {
        if (null == xsdFilePath) {
            throw new IllegalArgumentException("Missing xsdFilePath property");
        } else if (!xsdFilePath.canRead()) {
            throw new IllegalArgumentException("Unreadable xsdFilePath at: " + xsdFilePath.getAbsolutePath());
        }
        if (null == xmlFilePath) {
            throw new IllegalArgumentException("Missing xmlFilePath property");
        } else if (!xmlFilePath.exists()) {
            if (createFileIfNotExists) {
                try {
                    xmlFilePath.getParentFile().mkdir();
                    if (xmlFilePath.createNewFile()) {
                        xmlFilePath.delete();
                    }
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Xml file can not be created at " + xmlFilePath.getAbsolutePath());
                }
            } else {
                throw new ConfigurationException("Xml file does not exists: " + xmlFilePath.toString());
            }
        } else if (!xmlFilePath.canWrite()) {
            throw new IllegalArgumentException("Xml file can not be written at " + xmlFilePath.getAbsolutePath());
        }
        if (null != xsdIcfFilePath && !xsdIcfFilePath.canRead()) {
            throw new IllegalArgumentException("Connector schema file can not be read from " + xsdIcfFilePath.getAbsolutePath());
        } else if (null == xsdIcfFilePath) {
            try {
                URL defaultSchema = XMLConfiguration.class.getResource("/resource-schema-1.xsd");
                if (null != defaultSchema) {
                    xsdIcfFilePath = new File(defaultSchema.toURI());
                }
            } catch (URISyntaxException e) {
                // Should never happen.
                throw new UndeclaredThrowableException(e);
            }
        }
        if (null == xsdIcfFilePath) {
            throw new IllegalArgumentException("Missing xsdIcfFilePath property");
        } else if (!xsdIcfFilePath.canRead()) {
            throw new IllegalArgumentException("Unreadable xsdIcfFilePath at: " + xsdIcfFilePath.getAbsolutePath());
        }
    }
}
