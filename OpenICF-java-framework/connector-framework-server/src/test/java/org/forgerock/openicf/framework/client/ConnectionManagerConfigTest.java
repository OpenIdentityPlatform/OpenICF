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
package org.forgerock.openicf.framework.client;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConnectionManagerConfigTest {

    private static final String KEY = ConnectionManagerConfig.HOSTNAME_VERIFICATION_PROPERTY;

    private String saved;

    @BeforeMethod
    public void saveProperty() {
        saved = System.getProperty(KEY);
        System.clearProperty(KEY);
    }

    @AfterMethod
    public void restoreProperty() {
        if (saved == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, saved);
        }
    }

    @Test
    public void hostnameVerificationDefaultsFromSystemProperty() {
        assertTrue(new ConnectionManagerConfig().isHostnameVerification());
        System.setProperty(KEY, "false");
        assertFalse(new ConnectionManagerConfig().isHostnameVerification());
        // exactly "false", as in the legacy client
        System.setProperty(KEY, "FALSE");
        assertTrue(new ConnectionManagerConfig().isHostnameVerification());
        System.setProperty(KEY, "flase");
        assertTrue(new ConnectionManagerConfig().isHostnameVerification());
    }

    /**
     * The legacy client reads the property on every connection; an existing
     * configuration must follow a later change of it the same way.
     */
    @Test
    public void hostnameVerificationFollowsLaterPropertyChanges() {
        ConnectionManagerConfig config = new ConnectionManagerConfig();
        assertTrue(config.isHostnameVerification());
        System.setProperty(KEY, "false");
        assertFalse(config.isHostnameVerification());
        System.clearProperty(KEY);
        assertTrue(config.isHostnameVerification());
    }

    @Test
    public void explicitSettingOverridesSystemProperty() {
        ConnectionManagerConfig config = new ConnectionManagerConfig();
        config.setHostnameVerification(false);
        assertFalse(config.isHostnameVerification());

        System.setProperty(KEY, "false");
        config = new ConnectionManagerConfig();
        config.setHostnameVerification(true);
        assertTrue(config.isHostnameVerification());
    }
}
