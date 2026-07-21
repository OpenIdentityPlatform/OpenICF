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
 * Portions Copyrighted 2026 3A Systems, LLC
 */
package org.identityconnectors.ldap;

import static org.forgerock.opendj.server.embedded.ConfigParameters.configParams;
import static org.forgerock.opendj.server.embedded.ConnectionParameters.connectionParams;
import static org.forgerock.opendj.server.embedded.SetupParameters.setupParams;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.Assert;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import com.forgerock.opendj.ldap.tools.MakeLDIF;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldif.ConnectionChangeRecordWriter;
import org.forgerock.opendj.ldif.LDIFChangeRecordReader;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServerException;
import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;
import org.opends.server.tools.ImportLDIF;
import org.opends.server.util.EmbeddedUtils;

public abstract class LdapConnectorTestBase {

    // Cf. data.ldif and bigcompany.template.

    // Picked at run time rather than fixed, so that a developer box already running a directory
    // server on the traditional 2389/2636 does not break the build. The instance is set up from
    // these, so config and tests cannot drift apart.
    public static final int PORT = findFreePort();
    public static final int SSL_PORT = findFreePort();

    public static final String EXAMPLE_COM_DN = "dc=example,dc=com";

    public static final String ADMIN_DN = "uid=admin,dc=example,dc=com";
    public static final GuardedString ADMIN_PASSWORD = new GuardedString("password".toCharArray());

    public static final String ACME_DN = "o=Acme,dc=example,dc=com";
    public static final String ACME_O = "Acme";

    public static final String CZECH_REPUBLIC_DN = "c=Czech Republic,o=Acme,dc=example,dc=com";
    public static final String CZECH_REPUBLIC_C = "Czech Republic";

    public static final String ACME_USERS_DN = "ou=Users,o=Acme,dc=example,dc=com";

    public static final String BUGS_BUNNY_DN = "uid=bugs.bunny,ou=Users,o=Acme,dc=example,dc=com";
    public static final String BUGS_BUNNY_UID = "bugs.bunny";
    public static final String BBUNNY_UID = "bbunny";
    public static final String BUGS_BUNNY_CN = "Bugs Bunny";
    public static final String BUGS_BUNNY_SN = "Bunny";
    public static final String ELMER_FUDD_DN = "uid=elmer.fudd,ou=Users,o=Acme,dc=example,dc=com";
    public static final String ELMER_FUDD_UID = "elmer.fudd";
    public static final String SYLVESTER_DN = "uid=sylvester,ou=Users,o=Acme,dc=example,dc=com";
    public static final String SYLVESTER_UID = "sylvester";
    public static final String EXPIRED_UID = "expired";

    public static final String BUGS_AND_FRIENDS_DN = "cn=Bugs and Friends,o=Acme,dc=example,dc=com";
    public static final String EXTERNAL_PEERS_DN = "cn=External Peers,o=Acme,dc=example,dc=com";

    public static final String UNIQUE_BUGS_AND_FRIENDS_DN = "cn=Unique Bugs and Friends,o=Acme,dc=example,dc=com";
    public static final String UNIQUE_BUGS_AND_FRIENDS_CN = "Unique Bugs and Friends";
    public static final String UNIQUE_EXTERNAL_PEERS_DN = "cn=Unique External Peers,o=Acme,dc=example,dc=com";
    public static final String UNIQUE_EMPTY_GROUP_DN = "cn=Unique Empty Group,o=Acme,dc=example,dc=com";

    public static final String POSIX_BUGS_AND_FRIENDS_DN = "cn=POSIX Bugs and Friends,o=Acme,dc=example,dc=com";
    public static final String POSIX_EXTERNAL_PEERS_DN = "cn=POSIX External Peers,o=Acme,dc=example,dc=com";
    public static final String POSIX_EMPTY_GROUP_DN = "cn=POSIX Empty Group,o=Acme,dc=example,dc=com";
    public static final String POSIX_BUGS_BUNNY_GROUP = "cn=POSIX Bugs Bunny Group,o=Acme,dc=example,dc=com";

    public static final String SMALL_COMPANY_DN = "o=Small Company,dc=example,dc=com";
    public static final String SMALL_COMPANY_O = "Small Company";
    public static final String SINGLE_ACCOUNT_DN = "uid=single.account,o=Small Company,dc=example,dc=com";
    public static final String SINGLE_ACCOUNT_UID = "single.account";
    public static final String OWNER_DN = "cn=Owner,o=Small Company,dc=example,dc=com";

    public static final String BIG_COMPANY_DN = "o=Big Company,dc=example,dc=com";
    public static final String BIG_COMPANY_O = "Big Company";
    public static final String USER_0_DN = "uid=user.0,ou=People,o=Big Company,dc=example,dc=com";
    public static final String USER_0_UID = "user.0";
    public static final String USER_0_CN = "Aaccf Amar";
    public static final String USER_0_SN = "Amar";
    public static final String USER_0_GIVEN_NAME = "Aaccf";

    private static final int ADMIN_PORT = findFreePort();
    private static final int JMX_PORT = findFreePort();
    /** Replication carries the change log the sync tests read, even with nothing to replicate to. */
    private static final int REPLICATION_PORT = findFreePort();

    private static final String ROOT_DN = "cn=Directory Manager";
    private static final String ROOT_PASSWORD = "password";

    /** EmbeddedDirectoryServer.extractArchiveForSetup() insists on this server root name. */
    private static final String SERVER_ROOT_NAME = "opendj";

    private static final File WORK_DIR = new File(System.getProperty("java.io.tmpdir"), "openicf-ldap-opendj");
    private static final File SERVER_ROOT = new File(new File(WORK_DIR, "instance"), SERVER_ROOT_NAME);
    /** Pristine copy of the directories below, taken once the instance is fully populated. */
    private static final File PRISTINE_DIR = new File(WORK_DIR, "pristine");
    /** The parts of the instance the tests write to; restored from PRISTINE_DIR on every start. */
    private static final String[] MUTABLE_DIRS = { "config", "db", "changelogDb" };

    private static EmbeddedDirectoryServer server;
    private static boolean instanceCreated;

    static {
        // testSSL() registers this too, but by then the embedded server has started and brought
        // SSL up with it. The default SSL context is built once and cached, so registering after
        // that has no effect on it and the test would fail to trust the generated certificate.
        BlindTrustProvider.register();
    }

    @AfterClass
    public static void afterClass() {
        if (EmbeddedUtils.isRunning()) {
            stopServer();
        }
    }

    @BeforeMethod
	public void before() throws Exception {
        if (!EmbeddedUtils.isRunning()) {
            startServer();
        }
    }

    @AfterMethod
	public void after() throws Exception {
        if (restartServerAfterEachTest()) {
            stopServer();
        }
    }

    protected abstract boolean restartServerAfterEachTest();

    public static LdapConfiguration newConfiguration() {
        // IdM will not read the schema, so prefer to test with that setting.
        return newConfiguration(false);
    }

    public static LdapConfiguration newConfiguration(boolean readSchema) {
        LdapConfiguration config = new LdapConfiguration();
        // Cf. opends/config.ldif.
        config.setHost("localhost");
        config.setPort(PORT);
        config.setBaseContexts(ACME_DN, BIG_COMPANY_DN);
        config.setPrincipal(ADMIN_DN);
        config.setCredentials(ADMIN_PASSWORD);
        config.setReadSchema(readSchema);
        return config;
    }

    public static ConnectorFacade newFacade() {
        return newFacade(newConfiguration());
    }

    public static ConnectorFacade newFacade(LdapConfiguration cfg) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LdapConnector.class, cfg);
        return factory.newInstance(impl);
    }

    public static ConnectorObject searchByAttribute(ConnectorFacade facade, ObjectClass oclass, Attribute attr) {
        return searchByAttribute(facade, oclass, attr, (OperationOptions) null);
    }

    public static ConnectorObject searchByAttribute(ConnectorFacade facade, ObjectClass oclass, Attribute attr, String... attributesToGet) {
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet(attributesToGet);
        return searchByAttribute(facade, oclass, attr, builder.build());
    }

    public static ConnectorObject searchByAttribute(ConnectorFacade facade, ObjectClass oclass, Attribute attr, OperationOptions options) {
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, oclass, FilterBuilder.equalTo(attr), options);
        return objects.size() > 0 ? objects.get(0) : null;
    }

    public static ConnectorObject findByAttribute(List<ConnectorObject> objects, String attrName, Object value) {
        for (ConnectorObject object : objects) {
            Attribute attr = object.getAttributeByName(attrName);
            if (attr != null) {
                Object attrValue = AttributeUtil.getSingleValue(attr);
                if (value.equals(attrValue)) {
                    return object;
                }
            }
        }
        return null;
    }

    /**
     * Starts the embedded directory, restoring its data to the state the tests expect.
     *
     * The instance is only built once per JVM: setting it up and importing the Big Company
     * entries is far too slow to repeat for every test that asks for a restart. Afterwards a
     * pristine copy of the mutable directories is kept aside, and starting means putting that
     * copy back, which is what makes each test see the same data.
     */
    protected void startServer() throws IOException {
        try {
            createInstance();
            restorePristineState();
            server = manageServer();
            server.start();
            waitUntilListening();
        } catch (EmbeddedDirectoryServerException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    /**
     * Starting is asynchronous: it reports the server as running before the connection handler
     * has bound its port, so a test that connected straight away would be refused.
     */
    private static void waitUntilListening() throws IOException {
        final int WAIT = 200; // ms
        final int ITERATIONS = 150; // 30 s: a loaded CI runner can be very slow (#111)
        for (int i = 1; !isListening(PORT) || !isListening(SSL_PORT); i++) {
            if (i >= ITERATIONS) {
                throw new IOException("OpenDJ is not listening on ports " + PORT + " and " + SSL_PORT
                        + " " + (WAIT * ITERATIONS) + "ms after being started");
            }
            try {
                Thread.sleep(WAIT);
            } catch (InterruptedException e) {}
        }
    }

    protected static void stopServer() {
        if (server != null) {
            server.stop(LdapConnectorTestBase.class.getName(), null);
            server = null;
        }
        if (!waitUntilStopped()) {
            Assert.fail("OpenDJ failed to stop");
        }
    }

    /**
     * Shutting down is asynchronous, so wait for it to finish. Both conditions matter: the port
     * has to be free before the next start can bind it again, and isRunning() has to agree the
     * server is down, or before() would decide there is nothing to restart and leave the next
     * test without a server.
     */
    private static boolean waitUntilStopped() {
        final int WAIT = 200; // ms
        final int ITERATIONS = 150; // 30 s: a loaded CI runner can be very slow (#111)
        for (int i = 1; EmbeddedUtils.isRunning() || isAnyPortStillBound(); i++) {
            if (i >= ITERATIONS) {
                return false;
            }
            try {
                Thread.sleep(WAIT);
            } catch (InterruptedException e) {}
        }
        return true;
    }

    /** Every port the server binds, since starting again fails on whichever is not free yet. */
    private static boolean isAnyPortStillBound() {
        return isListening(PORT) || isListening(SSL_PORT) || isListening(ADMIN_PORT)
                || isListening(REPLICATION_PORT);
    }

    private static boolean isListening(int port) {
        try {
            new Socket(InetAddress.getLocalHost(), port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static EmbeddedDirectoryServer manageServer() {
        return EmbeddedDirectoryServer.manageEmbeddedDirectoryServer(
                configParams()
                        .serverRootDirectory(SERVER_ROOT.getPath())
                        .configurationFile(new File(SERVER_ROOT, "config/config.ldif").getPath()),
                connectionParams()
                        .hostName("localhost")
                        .ldapPort(PORT)
                        .ldapSecurePort(SSL_PORT)
                        .adminPort(ADMIN_PORT)
                        .bindDn(ROOT_DN)
                        .bindPassword(ROOT_PASSWORD),
                System.out, System.err);
    }

    private static void createInstance() throws IOException, EmbeddedDirectoryServerException {
        if (instanceCreated) {
            return;
        }
        IOUtil.delete(WORK_DIR);
        if (!SERVER_ROOT.mkdirs()) {
            throw new IOException("Cannot create " + SERVER_ROOT);
        }

        EmbeddedDirectoryServer setupServer = manageServer();
        setupServer.extractArchiveForSetup(archive());
        // Set up empty: the data is imported further down, once the configuration it has to
        // satisfy is in place. --ldapsPort implies a generated self-signed certificate, which is
        // all testSSL() needs, as it registers a blind trust provider.
        setupServer.setup(setupParams()
                .baseDn(EXAMPLE_COM_DN)
                .backendType("je")
                .jmxPort(JMX_PORT));

        // Configuration goes in over a connection, so the server has to be up for it.
        startAndApply(setupServer, "test-config.ldif");

        // Now that the server will accept the entries and knows which indexes to build for them,
        // import. Doing this before the configuration above would get entries rejected and leave
        // the VLV index empty.
        importData();

        // uid=admin and the ACIs granting it access can only be added once the entries they refer
        // to exist.
        startAndApply(setupServer, "admin.ldif");

        for (String dir : MUTABLE_DIRS) {
            copyDirectory(new File(SERVER_ROOT, dir).toPath(), new File(PRISTINE_DIR, dir).toPath());
        }
        instanceCreated = true;
    }

    private static void restorePristineState() throws IOException {
        for (String dir : MUTABLE_DIRS) {
            File target = new File(SERVER_ROOT, dir);
            IOUtil.delete(target);
            copyDirectory(new File(PRISTINE_DIR, dir).toPath(), target.toPath());
        }
    }

    /** The OpenDJ distribution archive, handed over by the build; cf. the pom. */
    private static File archive() throws IOException {
        String path = System.getProperty("opendj.archive");
        if (path == null) {
            throw new IOException("The opendj.archive system property is not set; "
                    + "it should point at the OpenDJ distribution archive to set the test instance up from.");
        }
        File archive = new File(path);
        if (!archive.isFile()) {
            throw new IOException("No OpenDJ distribution archive at " + archive);
        }
        return archive;
    }

    private static File generateBigCompanyLdif() throws IOException {
        File template = copyResourceToWorkDir("bigcompany.template");
        File ldif = new File(WORK_DIR, "bigcompany.ldif");
        // The name dictionaries the template draws from ship with the distribution.
        File resourcePath = new File(SERVER_ROOT, "template/config/MakeLDIF");
        // A fixed seed keeps the generated entries stable from run to run.
        int rc = MakeLDIF.run(System.out, System.err,
                "--outputLDIF", ldif.getPath(),
                "--resourcePath", resourcePath.getPath(),
                "--randomSeed", "1",
                template.getPath());
        if (rc != 0) {
            throw new IOException("Generating " + ldif + " from " + template + " failed with code " + rc);
        }
        return ldif;
    }

    /**
     * Grants uid=admin the privileges and ACIs the tests rely on. Kept as an LDIF changes file
     * because part of it modifies cn=config, which cannot be expressed in imported data.
     */
    private static void applyChanges(EmbeddedDirectoryServer target, String resource)
            throws IOException, EmbeddedDirectoryServerException {
        // The ports are only known at run time, so the files spell them as placeholders.
        String ldif = readResource(resource)
                .replace("%REPLICATION_PORT%", String.valueOf(REPLICATION_PORT));
        try (Connection connection = target.getInternalConnection();
                LDIFChangeRecordReader reader = new LDIFChangeRecordReader(new StringReader(ldif));
                ConnectionChangeRecordWriter writer = new ConnectionChangeRecordWriter(connection)) {
            while (reader.hasNext()) {
                writer.writeChangeRecord(reader.readChangeRecord());
            }
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = openResource(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Brings the server up, feeds it an LDIF changes file, and puts it back down. */
    private static void startAndApply(EmbeddedDirectoryServer target, String resource)
            throws IOException, EmbeddedDirectoryServerException {
        target.start();
        waitUntilListening();
        try {
            applyChanges(target, resource);
        } finally {
            target.stop(LdapConnectorTestBase.class.getName(), null);
        }
        if (!waitUntilStopped()) {
            throw new IOException("OpenDJ failed to stop after applying " + resource);
        }
    }

    /**
     * Imports the test entries with the server down.
     *
     * This drives the tool directly rather than through EmbeddedDirectoryServer.importLDIF(),
     * which requires a running server, and an online import would go through the task backend
     * and the admin connector for no benefit here.
     */
    private static void importData() throws IOException {
        File rejects = new File(WORK_DIR, "rejects.ldif");
        int rc = ImportLDIF.mainImportLDIF(new String[] {
                "--configFile", new File(SERVER_ROOT, "config/config.ldif").getPath(),
                "--backendID", "userRoot",
                "--ldifFile", copyResourceToWorkDir("data.ldif").getPath(),
                "--ldifFile", generateBigCompanyLdif().getPath(),
                "--rejectFile", rejects.getPath(),
                "--offline",
                "--noPropertiesFile" }, true, System.out, System.err);
        if (rc != 0) {
            throw new IOException("Importing the test entries into " + SERVER_ROOT + " failed with code " + rc);
        }
        // A rejected entry does not fail the import, it just quietly goes missing and surfaces
        // much later as a test looking for data that is not there.
        if (rejects.length() > 0) {
            throw new IOException("The server rejected some of the test entries; see " + rejects);
        }
    }

    private static InputStream openResource(String name) throws IOException {
        InputStream in = LdapConnectorTestBase.class.getClassLoader().getResourceAsStream("opends/" + name);
        if (in == null) {
            throw new IOException("Missing resource: opends/" + name);
        }
        return in;
    }

    private static File copyResourceToWorkDir(String name) throws IOException {
        File file = new File(WORK_DIR, name);
        try (InputStream in = openResource(name)) {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    private static void copyDirectory(final Path source, final Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            // The server only creates some of these once it has something to put in them.
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Cannot reserve a port for the test directory server", e);
        }
    }
}
