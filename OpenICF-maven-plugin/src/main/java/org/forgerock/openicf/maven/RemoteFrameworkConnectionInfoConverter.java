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

package org.forgerock.openicf.maven;

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.RemoteFrameworkConnectionInfo;

/**
 * A RemoteFrameworkConnectionInfoConverter.
 *
 * @author Laszlo Hordos
 */
public class RemoteFrameworkConnectionInfoConverter extends AbstractConfigurationConverter
        implements LogEnabled {
    private Logger log;

    public void enableLogging(final Logger logger) {
        assert logger != null;

        this.log = logger;
    }

    public boolean canConvert(final Class type) {
        assert type != null;

        return RemoteFrameworkConnectionInfo.class.isAssignableFrom(type);
    }

    public Object fromConfiguration(final ConverterLookup converterLookup,
            final PlexusConfiguration configuration, final Class type, final Class baseType,
            final ClassLoader classLoader, final ExpressionEvaluator expressionEvaluator,
            final ConfigurationListener listener) throws ComponentConfigurationException {

        log.info("Convert configuration to RemoteFrameworkConnectionInfo");

        PlexusConfiguration hostConfig = configuration.getChild("host", false);
        PlexusConfiguration keyConfig = configuration.getChild("key", false);
        PlexusConfiguration sslConfig = configuration.getChild("useSSL", false);

        if (hostConfig == null || keyConfig == null) {
            throw new ComponentConfigurationException("Required properties are 'host' and 'key'");
        }

        ConfigurationConverter converter = converterLookup.lookupConverterForType(String.class);

        String host =
                (String) converter.fromConfiguration(converterLookup, hostConfig, String.class,
                        baseType, classLoader, expressionEvaluator, listener);
        if (StringUtil.isBlank(host)){
            throw new ComponentConfigurationException("OpenICF Server host is required");
        }
        log.debug("OpenICF Server host: " + host);

        String key =
                (String) converter.fromConfiguration(converterLookup, keyConfig, String.class,
                        baseType, classLoader, expressionEvaluator, listener);
        if (key == null){
            throw new ComponentConfigurationException("OpenICF Server key is required");
        }
        log.debug("OpenICF Server key: " + (StringUtil.isNotBlank(key) ? "'*****'" : "''"));

        boolean useSSL = false;
        if (sslConfig != null
                && "true".equals(converter.fromConfiguration(converterLookup, sslConfig,
                        String.class, baseType, classLoader, expressionEvaluator, listener))) {
            useSSL = true;
            log.debug("OpenICF Server use SSL");
        }

        Integer port = 8759;
        converter = converterLookup.lookupConverterForType(Integer.class);

        PlexusConfiguration portConfig = configuration.getChild("port", false);
        if (null != portConfig) {
            port =
                    (Integer) converter.fromConfiguration(converterLookup, portConfig,
                            Integer.class, baseType, classLoader, expressionEvaluator, listener);
            log.debug("OpenICF Server port: " + port);
        }
        Integer timeout = 60000;

        PlexusConfiguration timeoutConfig = configuration.getChild("timeout", false);
        if (null != timeoutConfig) {
            timeout =
                    (Integer) converter.fromConfiguration(converterLookup, timeoutConfig,
                            Integer.class, baseType, classLoader, expressionEvaluator, listener);
            log.debug("OpenICF Server timeout: " + timeout);
        }

        return new RemoteFrameworkConnectionInfo(host, port, new GuardedString(key.toCharArray()),
                useSSL, useSSL ? getTrustManager() : null, timeout);
    }

    /**
     * Create a trust manager that trusts all certificates It is not using a
     * particular keyStore
     */
    protected List<TrustManager> getTrustManager() {
        return Arrays.asList((TrustManager) new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                    String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                    String authType) {
            }
        });
    }
}
