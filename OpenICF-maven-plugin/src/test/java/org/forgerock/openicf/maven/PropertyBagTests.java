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
package org.forgerock.openicf.maven;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;
import org.identityconnectors.framework.impl.api.ConfigurationPropertiesImpl;
import org.identityconnectors.framework.impl.api.ConfigurationPropertyImpl;
import org.testng.annotations.Test;

/**
 * {@code <configurationProperties>} values from the POM are converted to the
 * typed connector configuration properties by {@link PropertyBag}: a value of
 * a supported type must convert without the "not supported" warning, and a
 * malformed number must fail naming the value rather than with a bare
 * NumberFormatException.
 */
public class PropertyBagTests {

    private static final ExpressionEvaluator IDENTITY = new ExpressionEvaluator() {
        public Object evaluate(String expression) {
            return expression;
        }

        public File alignToBaseDirectory(File file) {
            return file;
        }
    };

    /** Collects the warnings, discards everything else. */
    private static final class WarningLogger extends AbstractLogger {

        final StringBuilder warnings = new StringBuilder();

        WarningLogger() {
            super(Logger.LEVEL_DEBUG, "test");
        }

        public void debug(String message, Throwable throwable) {
        }

        public void info(String message, Throwable throwable) {
        }

        public void warn(String message, Throwable throwable) {
            warnings.append(message);
        }

        public void error(String message, Throwable throwable) {
        }

        public void fatalError(String message, Throwable throwable) {
        }

        public Logger getChildLogger(String name) {
            return this;
        }
    }

    private static ConfigurationPropertiesImpl intProperty(String name) {
        ConfigurationPropertyImpl property = new ConfigurationPropertyImpl();
        property.setName(name);
        property.setType(Integer.TYPE);
        ConfigurationPropertiesImpl properties = new ConfigurationPropertiesImpl();
        properties.setProperties(Collections.singletonList(property));
        return properties;
    }

    private static XmlPlexusConfiguration configuration(String name, String value) {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configurationProperties");
        configuration.addChild(name, value);
        return configuration;
    }

    @Test
    public void convertsAnIntWithoutTheUnsupportedWarning() throws Exception {
        WarningLogger log = new WarningLogger();
        ConfigurationPropertiesImpl properties = intProperty("port");

        new PropertyBag(configuration("port", "8759"), IDENTITY, log)
                .mergeConfigurationProperties(properties);

        assertEquals(properties.getProperty("port").getValue(), 8759);
        assertEquals(log.warnings.toString(), "");
    }

    @Test
    public void rejectsAMalformedIntNamingTheValue() throws Exception {
        try {
            new PropertyBag(configuration("port", "80x"), IDENTITY, new WarningLogger())
                    .mergeConfigurationProperties(intProperty("port"));
            fail("Expected a MojoExecutionException");
        } catch (MojoExecutionException e) {
            assertEquals(e.getMessage(), "Failed to convert value '80x' of port to int");
            assertTrue(e.getCause() instanceof NumberFormatException, String.valueOf(e.getCause()));
        }
    }
}
