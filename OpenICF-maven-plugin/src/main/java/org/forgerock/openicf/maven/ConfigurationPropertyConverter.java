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

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;

/**
 *
 * @author Laszlo Hordos
 */
public class ConfigurationPropertyConverter extends AbstractConfigurationConverter implements
        LogEnabled {
    private Logger log;

    public void enableLogging(final Logger logger) {
        assert logger != null;

        this.log = logger;
    }

    public boolean canConvert(final Class type) {
        assert type != null;

        return PropertyBag.class.isAssignableFrom(type);
    }

    public Object fromConfiguration(final ConverterLookup converterLookup,
            final PlexusConfiguration configuration, final Class type, final Class baseType,
            final ClassLoader classLoader, final ExpressionEvaluator expressionEvaluator,
            final ConfigurationListener listener) throws ComponentConfigurationException {

        return new PropertyBag(configuration, expressionEvaluator, log);
    }
}
