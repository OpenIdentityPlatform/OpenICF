/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.connectors;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConnector;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public class ScriptedRESTConnectorTest extends RESTTestBase {

    protected static final String TEST_NAME = "REST";

    protected ConnectorFacade getFacade() {
        return getFacade(ScriptedRESTConnector.class, TEST_NAME);
    }

    @Test
    public void testURI() throws Exception {
        URI host = new URI("http://localhost:8080/openidm");
        host.getAuthority();
    }

    @Test
    public void validate() throws Exception {
        final ConnectorFacade facade = getFacade();
        facade.validate();
    }

    @Test
    public void test() throws Exception {
        final ConnectorFacade facade = getFacade();
        facade.test();
    }

    @Test(enabled = false)
    public void testCreate() throws Exception {
        final ConnectorFacade facade = getFacade();
        Set<Attribute> createAttributes = new HashSet<Attribute>();
        createAttributes.add(new Name("foo"));
        createAttributes.add(AttributeBuilder.buildCurrentPassword("Passw0rd".toCharArray()));
        createAttributes.add(AttributeBuilder.buildEnabled(true));

        Uid uid1 = facade.create(ObjectClass.ACCOUNT, createAttributes, null);

        Assert.assertEquals(uid1.getUidValue(), "foo");
    }

}
