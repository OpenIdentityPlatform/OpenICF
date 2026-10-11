/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */

package org.forgerock.openicf.connectors.xml;

import static org.forgerock.openicf.connectors.xml.XmlConnectorTestUtil.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Collections;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

/**
 * Runs the packaged bundle the way a connector server does: through a child-first bundle class
 * loader that takes xml-apis, Xerces and Saxon from the bundle's own lib directory.
 */
public class XMLConnectorBundleIT {

    @Test
    public void bundleSavesLoadsAndFindsEntries() throws Exception {
        File bundle = new File(System.getProperty("bundleJar"));
        assertTrue(bundle.isFile(), "bundle jar: " + bundle);
        ConnectorInfoManager manager = ConnectorInfoManagerFactory.getInstance().getLocalManager(bundle.toURI().toURL());
        assertEquals(manager.getConnectorInfos().size(), 1);
        APIConfiguration api = manager.getConnectorInfos().get(0).createDefaultAPIConfiguration();
        File xmlFile = getRandomXMLFile();
        ConfigurationProperties properties = api.getConfigurationProperties();
        properties.setPropertyValue("xmlFilePath", xmlFile);
        properties.setPropertyValue("xsdFilePath", XSD_SCHEMA_FILEPATH);
        properties.setPropertyValue("createFileIfNotExists", true);
        ConnectorFacade facade = ConnectorFacadeFactory.getInstance().newInstance(api);

        Uid alice = facade.create(ObjectClass.ACCOUNT, account("alice"), null);
        facade.create(ObjectClass.ACCOUNT, account("bob"), null);
        facade.update(ObjectClass.ACCOUNT, alice,
                Collections.singleton(AttributeBuilder.build(ATTR_ACCOUNT_LAST_NAME, "Changed")), null);
        Uid bob = facade.authenticate(ObjectClass.ACCOUNT, "bob", new GuardedString("secret-bob".toCharArray()), null);
        facade.delete(ObjectClass.ACCOUNT, bob, null);

        assertEquals(namesInFile(xmlFile), Collections.singletonList("alice"));
        ConnectorObject read = facade.getObject(ObjectClass.ACCOUNT, alice, null);
        assertEquals(AttributeUtil.getStringValue(read.getAttributeByName(ATTR_ACCOUNT_LAST_NAME)), "Changed");
    }
}
