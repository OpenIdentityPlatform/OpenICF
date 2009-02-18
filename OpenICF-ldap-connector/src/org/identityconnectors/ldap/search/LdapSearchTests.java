/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.ldap.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.QualifiedUid;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnector;
import org.identityconnectors.ldap.LdapConnectorTestBase;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;

public class LdapSearchTests extends LdapConnectorTestBase {

    // TODO operational attributes.
    // TODO LDAP directory attributes (entryDN, etc.).

    @Override
    protected boolean restartServerAfterEachTest() {
        return false;
    }

    @Test
    public void testSimplePagedSearch() {
        ConnectorFacade facade = newFacade();

        List<ConnectorObject> objects = TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null);
        assertNotNull(getObjectByName(objects, BUGS_BUNNY_DN));
        assertNotNull(getObjectByName(objects, ELMER_FUDD_DN));
        // 1000 is the default search size limit for OpenDS.
        assertTrue(objects.size() > 1000);
    }

    @Test
    public void testWithFilter() {
        ConnectorFacade facade = newFacade();
        ConnectorObject bunny = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN));
        assertEquals(BUGS_BUNNY_DN, bunny.getName().getNameValue());
    }

    @Test
    public void testAttributesToGet() {
        ConnectorFacade facade = newFacade();

        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setAttributesToGet("employeeNumber", "telephoneNumber");
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(USER_0_DN), optionsBuilder.build());

        Set<Attribute> attrs = CollectionUtil.newSet(object.getAttributes());
        assertTrue(attrs.remove(AttributeUtil.find(Uid.NAME, attrs)));
        assertTrue(attrs.remove(AttributeUtil.find(Name.NAME, attrs)));
        assertTrue(attrs.remove(AttributeUtil.find("employeeNumber", attrs)));
        assertTrue(attrs.remove(AttributeUtil.find("telephoneNumber", attrs)));

        assertTrue(attrs.isEmpty());
    }

    @Test
    public void testAttributesToGetNotPresentInEntryAreNotReturned() {
        // XXX not sure this is the right behavior, but that's what we are doing currently.

        ConnectorFacade facade = newFacade();

        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setAttributesToGet("employeeNumber");
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(BUGS_BUNNY_DN), optionsBuilder.build());

        assertNull(object.getAttributeByName("employeeNumber"));
    }

    @Test
    public void testScope() {
        ConnectorFacade facade = newFacade();
        // Find an organization to pass in OP_CONTAINER.
        ObjectClass oclass = new ObjectClass("organization");
        ConnectorObject organization = searchByAttribute(facade, oclass, new Name(BIG_COMPANY_DN));

        // There are no accounts directly under the organization...
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setScope(OperationOptions.SCOPE_ONE_LEVEL);
        optionsBuilder.setContainer(new QualifiedUid(oclass, organization.getUid()));
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null, optionsBuilder.build());
        assertTrue(objects.isEmpty());

        // ... but there are some in the organization subtree.
        optionsBuilder.setScope(OperationOptions.SCOPE_SUBTREE);
        objects = TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null, optionsBuilder.build());
        assertFalse(objects.isEmpty());
    }

    @Test
    public void testMultipleBaseDNs() {
        ConnectorFacade facade = newFacade();

        // This should find accounts from both base DNs.
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null);
        assertNotNull(getObjectByName(objects, BUGS_BUNNY_DN));
        assertNotNull(getObjectByName(objects, USER_0_DN));
    }

    @Test
    public void testBaseDNsOption() {
        LdapConfiguration config = newConfiguration();
        config.setBaseDNs(ACME_DN, SMALL_COMPANY_DN, BIG_COMPANY_DN);
        ConnectorFacade facade = newFacade(config);

        // Specifying both OP_BASE_DNS and OP_CONTAINER is prohibited.
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setOption(LdapConnector.OP_BASE_DNS, new String[] { ACME_DN, SMALL_COMPANY_DN });
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null, optionsBuilder.build());
        assertNotNull(getObjectByName(objects, BUGS_BUNNY_DN));
        assertNotNull(getObjectByName(objects, ELMER_FUDD_DN));
        assertNotNull(getObjectByName(objects, SINGLE_ACCOUNT_DN));
        // We only searched inside Acme and Small Company, not inside Big Company.
        assertNull(getObjectByName(objects, USER_0_UID));
    }

    @Test(expected = ConnectorException.class)
    public void testBaseDNsOptionConflictsWithContainerOption() {
        ConnectorFacade facade = newFacade();
        // Find an organization to pass in OP_CONTAINER.
        ObjectClass oclass = new ObjectClass("organization");
        ConnectorObject organization = searchByAttribute(facade, oclass, new Name(BIG_COMPANY_DN));

        // Specifying both OP_BASE_DNS and OP_CONTAINER is prohibited.
        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        optionsBuilder.setOption(LdapConnector.OP_BASE_DNS, new String[] { ACME_DN });
        optionsBuilder.setContainer(new QualifiedUid(oclass, organization.getUid()));
        TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null, optionsBuilder.build());
    }

    @Test(expected = ConnectorException.class)
    public void testBaseDNsFromOptionOnlyAllowedFromConfigBaseDNs() {
        LdapConfiguration config = newConfiguration();
        config.setBaseDNs(ACME_DN);
        ConnectorFacade facade = newFacade(config);

        OperationOptionsBuilder optionsBuilder = new OperationOptionsBuilder();
        // Specifying a base DN for OP_BASE_DNS which is not in LdapConfiguration.getBaseDNs() is prohibited.
        optionsBuilder.setOption(LdapConnector.OP_BASE_DNS, new String[] { BIG_COMPANY_DN });
        TestHelpers.searchToList(facade, ObjectClass.ACCOUNT, null, optionsBuilder.build());
    }

    @Test
    public void testSearchObjectClassNotInSchema() {
        ConnectorFacade facade = newFacade();

        // Simplest: try w/o filter.
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, new ObjectClass("country"), null, null);
        ConnectorObject czechRep = getObjectByName(objects, CZECH_REPUBLIC_DN);

        // Try with a name filter. Filtering will be done by the framework, because the LDAP attribute for Name is not known.
        // Also try with options.
        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME, CZECH_REPUBLIC_DN));
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("c");
        objects = TestHelpers.searchToList(facade, new ObjectClass("country"), filter, builder.build());
        czechRep = getObjectByName(objects, CZECH_REPUBLIC_DN);
        assertEquals(CZECH_REPUBLIC_C, AttributeUtil.getAsStringValue(czechRep.getAttributeByName("c")));
    }

    private static ConnectorObject getObjectByName(List<ConnectorObject> objects, String name) {
        for (ConnectorObject object : objects) {
            if (name.equals(object.getName().getNameValue())) {
                return object;
            }
        }
        return null;
    }
}
