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
package org.identityconnectors.ldap;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.identityconnectors.common.IOUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.util.EmbeddedUtils;

public abstract class LdapConnectorTestBase {

    // Cf. data.ldif and bigcompany.ldif.

    public static final String ACME_DN = "o=Acme,dc=example,dc=com";
    public static final String ACME_O = "Acme";

    public static final String WALT_DISNEY_CN = "Walt Disney";
    public static final String BUGS_BUNNY_DN = "uid=bugs.bunny,ou=Users,o=Acme,dc=example,dc=com";
    public static final String BUGS_BUNNY_UID = "bugs.bunny";
    public static final String BUGS_BUNNY_CN = "Bugs Bunny";
    public static final String ELMER_FUDD_DN = "uid=elmer.fudd,ou=Users,o=Acme,dc=example,dc=com";
    public static final String ELMER_FUDD_UID = "elmer.fudd";
    public static final String SYLVESTER_CN = "Sylvester";

    public static final String LOONEY_TUNES_DN = "cn=Looney Tunes,o=Acme,dc=example,dc=com";
    public static final String LOONEY_TUNES_CN = "Looney Tunes";
    public static final String LOONEY_TUNES_DESCRIPTION = "Characters from Looney Tunes";
    public static final String BUGS_AND_FRIENDS_CN = "Bugs and Friends";

    public static final String CZECH_REPUBLIC_DN = "c=Czech Republic,o=Acme,dc=example,dc=com";
    public static final String CZECH_REPUBLIC_C = "Czech Republic";

    public static final String SMALL_COMPANY_DN = "o=Small Company,dc=example,dc=com";
    public static final String SMALL_COMPANY_O = "Small Company";
    public static final String SINGLE_ACCOUNT_DN = "uid=single.account,o=Small Company,dc=example,dc=com";
    public static final String SINGLE_ACCOUNT_UID = "single.account";

    public static final String BIG_COMPANY_DN = "o=Big Company,dc=example,dc=com";
    public static final String BIG_COMPANY_O = "Big Company";
    public static final String USER_0_DN = "uid=user.0,ou=People,o=Big Company,dc=example,dc=com";
    public static final String USER_0_UID = "user.0";
    public static final String USER_0_SN = "Amar";
    public static final String USER_0_GIVEN_NAME = "Aaccf";

    // Cf. test/opends/config/config.ldif and setup-test-opends.xml.

    private static final String[] FILES = {
        "config/config.ldif",
        "config/admin-backend.ldif",
        "config/schema/00-core.ldif",
        "config/schema/01-pwpolicy.ldif",
        "config/schema/02-config.ldif",
        "config/schema/03-changelog.ldif",
        "config/schema/03-rfc2713.ldif",
        "config/schema/03-rfc2714.ldif",
        "config/schema/03-rfc2739.ldif",
        "config/schema/03-rfc2926.ldif",
        "config/schema/03-rfc3112.ldif",
        "config/schema/03-rfc3712.ldif",
        "config/schema/03-uddiv3.ldif",
        "config/schema/04-rfc2307bis.ldif",
        "db/userRoot/00000000.jdb"
    };

    @AfterClass
    public static void afterClass() {
        if (EmbeddedUtils.isRunning()) {
            stopServer();
        }
    }

    @Before
    public void before() throws Exception {
        if (!EmbeddedUtils.isRunning()) {
            startServer();
        }
    }

    @After
    public void after() throws Exception {
        if (restartServerAfterEachTest()) {
            stopServer();
        }
    }

    protected abstract boolean restartServerAfterEachTest();

    public static LdapConfiguration newConfiguration(String... extObjectClassesAndNamingAttributes) {
        LdapConfiguration config = new LdapConfiguration();
        // Cf. test/opends/config/config.ldif.
        config.setPort(1389);
        config.setBaseDNs(ACME_DN, BIG_COMPANY_DN);
        config.setAuthentication("simple");
        config.setBindDN("uid=admin,dc=example,dc=com");
        config.setBindPassword(new GuardedString("password".toCharArray()));
        String[] extendedObjectClasses = new String[extObjectClassesAndNamingAttributes.length];
        String[] extendedNamingAttributes = new String[extObjectClassesAndNamingAttributes.length];
        for (int i = 0; i < extObjectClassesAndNamingAttributes.length; i++) {
            String entry = extObjectClassesAndNamingAttributes[i];
            int colon = entry.indexOf(':');
            assert colon > 0 && colon + 1 < entry.length();
            extendedObjectClasses[i] = entry.substring(0, colon);
            extendedNamingAttributes[i] = entry.substring(colon + 1);
        }
        config.setExtendedObjectClasses(extendedObjectClasses);
        config.setExtendedNamingAttributes(extendedNamingAttributes);
        // Otherwise it would try to use VLV, which we don't yet support.
        config.setSimplePagedSearchPreferred(true);
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

    public ConnectorObject searchByAttribute(ConnectorFacade facade, ObjectClass oclass, Attribute attr) {
        return searchByAttribute(facade, oclass, attr, null);
    }

    public ConnectorObject searchByAttribute(ConnectorFacade facade, ObjectClass oclass, Attribute attr, OperationOptions options) {
        List<ConnectorObject> objects = TestHelpers.searchToList(facade, oclass, FilterBuilder.equalTo(attr), options);
        return objects.size() > 0 ? objects.get(0) : null;
    }

    protected void startServer() throws IOException {
        File root = new File(System.getProperty("java.io.tmpdir"), "opends");
        IOUtil.delete(root);
        if (!root.mkdirs()) {
            throw new IOException();
        }
        for (String path : FILES) {
            File file = new File(root, path);
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IOException(file.getAbsolutePath());
            }
            IOUtil.extractResourceToFile(LdapConnectorTestBase.class, "opends/" + path, file);
        }

        File configDir = new File(root, "config");
        File configFile = new File(configDir, "config.ldif");
        File schemaDir = new File(configDir, "schema");
        File lockDir = new File(root, "locks");
        if (!lockDir.mkdirs()) {
            throw new IOException();
        }

        try {
            DirectoryEnvironmentConfig config = new DirectoryEnvironmentConfig();
            config.setServerRoot(root);
            config.setConfigFile(configFile);
            config.setSchemaDirectory(schemaDir);
            config.setLockDirectory(lockDir);
            EmbeddedUtils.startServer(config);
        } catch (ConfigException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        } catch (InitializationException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    protected static void stopServer() {
        EmbeddedUtils.stopServer("org.test.opends.EmbeddedOpenDS", null);
    }
}
