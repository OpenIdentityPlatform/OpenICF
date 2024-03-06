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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;


import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.special.ClassRealmConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * A CustomComponentConfigurator which adds the project's runtime classpath
 * elements to the plugin classpath.
 *
 * @author Laszlo Hordos
 */
@Component(role = ComponentConfigurator.class, hint = "override")
public class CustomComponentConfigurator extends AbstractComponentConfigurator {
    public void configureComponent(Object component, PlexusConfiguration configuration,
            ExpressionEvaluator expressionEvaluator, ClassRealm containerRealm,
            ConfigurationListener listener) throws ComponentConfigurationException {

        addProjectDependenciesToClassRealm(expressionEvaluator, containerRealm);

        converterLookup.registerConverter(new ClassRealmConverter(containerRealm));

        ObjectWithFieldsConverter converter = new ObjectWithFieldsConverter();

        converter.processConfiguration(converterLookup, component, containerRealm, configuration,
                expressionEvaluator, listener);
    }

    @SuppressWarnings("unchecked")
    private void addProjectDependenciesToClassRealm(ExpressionEvaluator expressionEvaluator,
            ClassRealm containerRealm) throws ComponentConfigurationException {
        List<String> runtimeClasspathElements;
        try {
            runtimeClasspathElements =
                    (List<String>) expressionEvaluator.evaluate("${project.testClasspathElements}");
        } catch (ExpressionEvaluationException e) {
            throw new ComponentConfigurationException(
                    "There was a problem evaluating: ${project.testClasspathElements}", e);
        }

        for (String element : runtimeClasspathElements) {
            try {
                final URL url = new File(element).toURI().toURL();
                containerRealm.addURL(url);
            } catch (MalformedURLException e) {
                throw new ComponentConfigurationException("Unable to access project dependency: "
                        + element, e);
            }
        }
    }
}
