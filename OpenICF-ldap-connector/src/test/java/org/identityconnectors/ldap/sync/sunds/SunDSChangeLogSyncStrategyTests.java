/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.ldap.sync.sunds;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import static java.util.Collections.emptyList;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.SunDSTestBase;
import org.identityconnectors.ldap.LdapConstants.ServerType;

public class SunDSChangeLogSyncStrategyTests extends SunDSTestBase {

    private static final Log log = Log.getLog(SunDSChangeLogSyncStrategyTests.class);

    private static final int STABLE_CHANGELOG_INTERVAL = 2000; /* milliseconds */

    private static LdapConnection newConnection(LdapConfiguration config) throws NamingException {
        LdapConnection conn = new LdapConnection(config);
        cleanupBaseContext(conn);
        waitForChangeLogToStabilize(conn);
        return conn;
    }

    private static void waitForChangeLogToStabilize(LdapConnection conn) {
        int lastChangeNumber = -1;
        int previousLastChangeNumber;
        do {
            if (lastChangeNumber > 0) {
                log.ok("Waiting for change log to stabilize (last change number: {0})", lastChangeNumber);
                try {
                    Thread.sleep(STABLE_CHANGELOG_INTERVAL);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
            previousLastChangeNumber = lastChangeNumber;
            lastChangeNumber = new SunDSChangeLogSyncStrategy(conn, ObjectClass.ACCOUNT).getChangeLogAttributes().getLastChangeNumber();
        } while (lastChangeNumber != previousLastChangeNumber);
    }

    private List<SyncDelta> doTest(LdapConnection conn, String ldif, int expected) throws NamingException {
        SunDSChangeLogSyncStrategy sync = new SunDSChangeLogSyncStrategy(conn, ObjectClass.ACCOUNT);
        SyncToken token = sync.getLatestSyncToken();

        LdapModifyForTests.modify(conn, ldif);
        waitForChangeLogToStabilize(conn);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "sn", "givenName", "uid");
        OperationOptions options = builder.build();

        final List<SyncDelta> result = new ArrayList<SyncDelta>();
        // SyncTokenResultsHandler rather than a plain SyncResultsHandler because the strategy
        // hands the final token back through handleResult(); in production the framework always
        // passes a handler of this type.
        sync.sync(token, new SyncTokenResultsHandler() {
            public boolean handle(SyncDelta delta) {
                result.add(delta);
                return true;
            }

            public void handleResult(SyncToken token) {
                // The tests assert on the deltas, not the returned token.
            }
        }, options);
        return result;
    }

    @Test
    public void testSimple() throws Exception {
        LdapConnection conn = newConnection(newConfiguration());
        String baseContext = conn.getConfiguration().getBaseContexts()[0];

        String entryDN = "uid=foobar," + baseContext;
        List<SyncDelta> result = doTest(conn,
                "dn: " +entryDN + "\n" +
                "changetype: add\n" +
                "objectClass: inetOrgPerson\n" +
                "objectClass: organizationalPerson\n" +
                "objectClass: person\n" +
                "objectClass: top\n" +
                "uid: foobar\n" +
                "cn: Foo Bar\n" +
                "sn: Bar\n", 1);

        assertEquals(1, result.size());
        SyncDelta delta = result.get(0);
        // add maps to CREATE since the ICF 1.4 upgrade; it was CREATE_OR_UPDATE when this last ran.
        assertEquals(SyncDeltaType.CREATE, delta.getDeltaType());
        ConnectorObject object = delta.getObject();
        assertEquals(new Uid(entryDN), object.getUid());
        assertEquals(new Name(entryDN), object.getName());
        assertEquals(AttributeBuilder.build("uid", "foobar"), object.getAttributeByName("uid"));
        assertEquals(AttributeBuilder.build("cn", "Foo Bar"), object.getAttributeByName("cn"));
        assertEquals(AttributeBuilder.build("sn", "Bar"), object.getAttributeByName("sn"));

        result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: modrdn\n" +
                "newRdn: cn=Foo Bar", 1);
        entryDN = "cn=Foo Bar," + baseContext;

        assertEquals(1, result.size());
        delta = result.get(0);
        // modrdn maps to UPDATE since the ICF 1.4 upgrade; it was CREATE_OR_UPDATE before.
        assertEquals(SyncDeltaType.UPDATE, delta.getDeltaType());
        object = delta.getObject();
        assertEquals(new Uid(entryDN), object.getUid());
        assertEquals(new Name(entryDN), object.getName());
        assertEquals(AttributeBuilder.build("uid", emptyList()), object.getAttributeByName("uid"));
        assertEquals(AttributeBuilder.build("cn", "Foo Bar"), object.getAttributeByName("cn"));
        assertEquals(AttributeBuilder.build("sn", "Bar"), object.getAttributeByName("sn"));

        result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: modify\n" +
                "add: cn\n" +
                "cn: Dummy User", 1);

        assertEquals(1, result.size());
        delta = result.get(0);
        // modify maps to UPDATE since the ICF 1.4 upgrade; it was CREATE_OR_UPDATE before.
        assertEquals(SyncDeltaType.UPDATE, delta.getDeltaType());
        object = delta.getObject();
        assertEquals(AttributeBuilder.build("cn", "Foo Bar", "Dummy User"), object.getAttributeByName("cn"));

        result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: modrdn\n" +
                "newRdn: cn=Dummy User\n" +
                "deleteOldRdn: FALSE", 1);
        entryDN = "cn=Dummy User," + baseContext;

        assertEquals(1, result.size());
        delta = result.get(0);
        // modrdn maps to UPDATE since the ICF 1.4 upgrade; it was CREATE_OR_UPDATE before.
        assertEquals(SyncDeltaType.UPDATE, delta.getDeltaType());
        object = delta.getObject();
        assertEquals(new Uid(entryDN), object.getUid());
        assertEquals(new Name(entryDN), object.getName());
        assertEquals(AttributeBuilder.build("cn", "Foo Bar", "Dummy User"), object.getAttributeByName("cn"));

        result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: modify\n" +
                "delete: cn\n" +
                "cn: Foo Bar", 1);

        assertEquals(1, result.size());
        delta = result.get(0);
        // modify maps to UPDATE since the ICF 1.4 upgrade; it was CREATE_OR_UPDATE before.
        assertEquals(SyncDeltaType.UPDATE, delta.getDeltaType());
        object = delta.getObject();
        assertEquals(AttributeBuilder.build("cn", "Dummy User"), object.getAttributeByName("cn"));

        result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: delete", 1);

        assertEquals(1, result.size());
        delta = result.get(0);
        assertEquals(SyncDeltaType.DELETE, delta.getDeltaType());
        assertEquals(new Uid(entryDN), delta.getUid());
    }

    @Test
    public void testAllBlocksReturnedFromSingleSyncCall() throws NamingException {
        LdapConfiguration config = newConfiguration();
        // Set a small block size so connector would have to do
        // a couple of searches to return all deltas.
        config.setChangeLogBlockSize(2);
        LdapConnection conn = newConnection(config);
        String baseContext = conn.getConfiguration().getBaseContexts()[0];

        int COUNT = 10;
        StringBuilder ldif = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            String name = "user." + i;
            String entryDN = "uid=" + name + "," + baseContext;
            ldif.append(MessageFormat.format(
                    "dn: {0}\n" +
                    "changetype: add\n" +
                    "objectClass: inetOrgPerson\n" +
                    "objectClass: organizationalPerson\n" +
                    "objectClass: person\n" +
                    "objectClass: top\n" +
                    "uid: {1}\n" +
                    "cn: {1}\n" +
                    "sn: {1}\n" +
                    "\n",
                    entryDN, name));
        }

        List<SyncDelta> result = doTest(conn, ldif.toString(), COUNT);
        assertEquals(10, result.size());
        for (int i = 0; i < COUNT; i++) {
            String name = "user." + i;
            String entryDN = "uid=" + name + "," + baseContext;
            ConnectorObject object = result.get(i).getObject();
            assertEquals(new Uid(entryDN), object.getUid());
            assertEquals(new Name(entryDN), object.getName());
        }
    }

    @Test
    public void testFilterOutByBaseContexts() throws NamingException {
        LdapConfiguration config = newConfiguration();
        String baseContext = config.getBaseContexts()[0];
        config.setBaseContextsToSynchronize("ou=Subcontext," + baseContext);
        LdapConnection conn = newConnection(config);
        testExpectingNoDelta(conn);
    }

    @Test
    public void testFilterOutByModifiersNames() throws NamingException {
        LdapConfiguration config = newConfiguration();
        // The DN the connection binds as, which the server records as the modifier of the modify
        // below; on Sun DSEE this was cn=Directory Manager.
        config.setModifiersNamesToFilterOut(ADMIN_DN);
        LdapConnection conn = newConnection(config);
        String baseContext = conn.getConfiguration().getBaseContexts()[0];
        String entryDN = "uid=foobar," + baseContext;

        // Unlike the other filter tests this one drives a modify, not the shared add: OpenDJ
        // attributes an add to creatorsName and records modifiersName only on a modify, which is
        // what this filter matches. The add that creates the entry is synced from a token taken
        // before it, in a separate window, so it does not count towards the assertion below.
        doTest(conn,
                "dn: " + entryDN + "\n" +
                "changetype: add\n" +
                "objectClass: inetOrgPerson\n" +
                "objectClass: organizationalPerson\n" +
                "objectClass: person\n" +
                "objectClass: top\n" +
                "uid: foobar\n" +
                "cn: Foo Bar\n" +
                "sn: Bar\n", 1);

        List<SyncDelta> result = doTest(conn,
                "dn: " + entryDN + "\n" +
                "changeType: modify\n" +
                "add: description\n" +
                "description: changed", 0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterOutByAttributes() throws NamingException {
        LdapConfiguration config = newConfiguration();
        config.setAttributesToSynchronize("telephoneNumber");
        LdapConnection conn = newConnection(config);
        testExpectingNoDelta(conn);
    }

    @Test
    public void testFilterOutByObjectClasses() throws NamingException {
        LdapConfiguration config = newConfiguration();
        config.setObjectClassesToSynchronize("groupOfUniqueNames");
        LdapConnection conn = newConnection(config);
        testExpectingNoDelta(conn);
    }

    @Test
    public void testAccountSynchronizationFilter() throws NamingException {
        LdapConfiguration config = newConfiguration();
        config.setAccountSynchronizationFilter("cn=value");
        LdapConnection conn = newConnection(config);
        testExpectingNoDelta(conn);
    }

    @Test(enabled = false)
	private void testExpectingNoDelta(LdapConnection conn) throws NamingException {
        String baseContext = conn.getConfiguration().getBaseContexts()[0];
        String entryDN = "uid=foobar," + baseContext;
        List<SyncDelta> result = doTest(conn,
                "dn: " +entryDN + "\n" +
                "changetype: add\n" +
                "objectClass: inetOrgPerson\n" +
                "objectClass: organizationalPerson\n" +
                "objectClass: person\n" +
                "objectClass: top\n" +
                "uid: foobar\n" +
                "cn: Foo Bar\n" +
                "sn: Bar\n", 0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSyncSupported() throws NamingException {
        LdapConfiguration config = newConfiguration();
        LdapConnection conn = newConnection(config);
        // The test instance is OpenDJ; it was Sun DSEE back when this test last ran. Both serve
        // the change log this strategy reads, so sync stays supported either way.
        assertEquals(ServerType.OPENDJ, conn.getServerType());
        Schema schema = newFacade(config).schema();
        ObjectClassInfo accountInfo = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
        assertTrue(schema.getSupportedObjectClassesByOperation().get(SyncApiOp.class).contains(accountInfo));
    }
}
