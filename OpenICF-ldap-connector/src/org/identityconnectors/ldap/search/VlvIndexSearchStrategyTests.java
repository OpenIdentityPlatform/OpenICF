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
package org.identityconnectors.ldap.search;

import static org.identityconnectors.common.CollectionUtil.newSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.logging.LogSpi;
import org.identityconnectors.common.logging.Log.Level;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.ldap.LdapConfiguration;
import org.identityconnectors.ldap.LdapConnection;
import org.identityconnectors.ldap.LdapConnector;
import org.identityconnectors.ldap.SunDSTestBase;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.junit.Test;

public class VlvIndexSearchStrategyTests extends SunDSTestBase {

    public static LdapConfiguration newConfiguration(int blockSize) {
        LdapConfiguration config = newConfiguration();
        config.setUseBlocks(true);
        config.setBlockCount(blockSize);
        config.setUsePagedResultControl(false);
        config.validate();
        return config;
    }

    /**
     * The VLV index specification allows for rounding errors when computing
     * the server target index (see the "Si = Sc * (Ci / Cc)" computation in
     * draft-ietf-ldapext-ldapv3-vlv-09.txt). This test ensures the VLV index
     * search strategy works around these rounding errors.
     */
    @Test
    public void testOverlapWorkaround() throws Exception {
        // Rounding errors occur with small block sizes, so using a size of 2.
        LdapConfiguration config = newConfiguration(2);

        LdapConnection conn = new LdapConnection(config);
        cleanupBaseContext(conn);
        conn.close();

        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(LdapConnector.class, config);
        ConnectorFacade facade = factory.newInstance(impl);

        // When Sc and Cc are 90 and the block size is 2, rounding
        // errors occur at indexes 27, 50, 53, and 64.
        final int COUNT = 90;

        String baseContext = config.getBaseContexts()[0];
        for (int i = 0; i < COUNT; i++) {
            String userName = "user." + i;
            Set<Attribute> attrs = newSet();
            attrs.add(new Name("uid=" + userName + "," + baseContext));
            attrs.add(AttributeBuilder.build("uid", userName));
            attrs.add(AttributeBuilder.build("cn", userName));
            attrs.add(AttributeBuilder.build("sn", userName));
            facade.create(ObjectClass.ACCOUNT, attrs, null);
        }

        ToListResultsHandler handler = new ToListResultsHandler();

        Log oldLog = VlvIndexSearchStrategy.getLog();
        OverlapLogImpl overlapLog = new OverlapLogImpl(oldLog);
        try {
            VlvIndexSearchStrategy.setLog(createLog(VlvIndexSearchStrategy.class, overlapLog));
            facade.search(ObjectClass.ACCOUNT, null, handler, null);
        } finally {
            VlvIndexSearchStrategy.setLog(oldLog);
        }

        assertTrue("The server should have sent overlapping blocks", overlapLog.hasOverlap());
        assertEquals(COUNT, handler.getObjects().size());
    }

    private static Log createLog(Class<?> clazz, LogSpi spi) throws Exception {
        Method getLogMethod = Log.class.getDeclaredMethod("getLog", Class.class, LogSpi.class);
        getLogMethod.setAccessible(true);
        return (Log) getLogMethod.invoke(null, clazz, spi);
    }

    static class OverlapLogImpl implements LogSpi {

        private final Log delegate;

        private boolean overlap = false;

        public OverlapLogImpl(Log delegate) {
            this.delegate = delegate;
        }

        public boolean isLoggable(Class<?> clazz, Level level) {
            return true; // We need to, in order to ensure log() will be called.
        }

        public void log(Class<?> clazz, String method, Level level, String message, Throwable ex) {
            if (VlvIndexSearchStrategy.class.equals(clazz) && message != null && message.contains("overlap")) {
                overlap = true;
            }
            if (delegate.isLoggable(level)) {
                delegate.log(clazz, method, level, message, ex);
            }
        }

        public boolean hasOverlap() {
            return overlap;
        }
    }
}
