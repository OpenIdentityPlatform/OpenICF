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
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.logging.Logger;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.script.Script;
import org.identityconnectors.common.script.ScriptBuilder;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConfigurationProperties;
import org.identityconnectors.framework.api.ConfigurationProperty;

/**
 *
 * @author Laszlo Hordos
 */
public class PropertyBag {

    public final PlexusConfiguration configuration;

    public final ExpressionEvaluator evaluator;

    private final Logger log;

    public PropertyBag(final PlexusConfiguration configuration,
            final ExpressionEvaluator evaluator, final Logger log)
            throws ComponentConfigurationException {
        if (null == configuration) {
            throw new ComponentConfigurationException("Missing 'configuration'");
        }
        if (null == evaluator) {
            throw new ComponentConfigurationException("Missing 'evaluator'");
        }
        if (null == log) {
            throw new ComponentConfigurationException("Missing 'log'");
        }

        this.configuration = configuration;
        this.evaluator = evaluator;
        this.log = log;
    }

    public void mergeConfigurationProperties(final ConfigurationProperties configurationProperties)
            throws MojoExecutionException {
        for (String propertyName : configurationProperties.getPropertyNames()) {

            PlexusConfiguration child = configuration.getChild(propertyName, false);

            if (null != child) {
                ConfigurationProperty property = configurationProperties.getProperty(propertyName);
                if (property.getType().isArray()) {
                    if (Character.TYPE.equals(property.getType().getComponentType())
                            || Character.class.equals(property.getType().getComponentType())) {
                        if (child.getValue() != null) {
                            property.setValue(child.getValue().toCharArray());
                        } else {
                            property.setValue(null);
                        }
                        continue;
                    }

                    if (StringUtil.isNotBlank(child.getValue())) {
                        throw new MojoExecutionException("Configuration Property '" + propertyName
                                + "' must be configured as Array <" + propertyName
                                + "><value>Foo</value></" + propertyName + ">");
                    }
                    PlexusConfiguration[] arrayValues = child.getChildren("value");
                    Object array = null;
                    if (null != arrayValues) {
                        array =
                                Array.newInstance(property.getType().getComponentType(),
                                        arrayValues.length);
                        for (int i = 0; i < arrayValues.length; i++) {
                            Array.set(array, i, castValue(propertyName, arrayValues[i], property
                                    .getType().getComponentType()));
                        }

                    }
                    property.setValue(array);
                } else {
                    property.setValue(castValue(propertyName, child, property.getType()));
                }
            }
        }
    }

    private Object castValue(String name, PlexusConfiguration value, Class<?> targetType)
            throws MojoExecutionException {
        if (null == value.getValue()) {
            return null;
        }
        String sourceValue = null;
        try {
            sourceValue = String.valueOf(evaluator.evaluate(value.getValue()));
        } catch (ExpressionEvaluationException e) {
            throw new MojoExecutionException(e.getMessage());
        }
        Object targetValue = null;
        if (targetType.equals(String.class)) {
            return sourceValue;
        }
        if (targetType.isPrimitive() && StringUtil.isBlank(sourceValue)) {
            throw new MojoExecutionException("Failed to convert empty string value of " + name
                    + "to " + targetType);
        }

        if (targetType.equals(Long.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = Long.valueOf(sourceValue);
            }
        } else if (targetType.equals(Long.TYPE)) {
            targetValue = Long.valueOf(sourceValue);
        } else if (targetType.equals(Character.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = sourceValue.charAt(0);
            }
        } else if (targetType.equals(Character.TYPE)) {
            targetValue = sourceValue.charAt(0);
        } else if (targetType.equals(Double.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = Double.valueOf(sourceValue);
            }
        } else if (targetType.equals(Double.TYPE)) {
            targetValue = Double.valueOf(sourceValue);
        } else if (targetType.equals(Float.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = Float.valueOf(sourceValue);
            }
        } else if (targetType.equals(Float.TYPE)) {
            targetValue = Float.valueOf(sourceValue);
        } else if (targetType.equals(Integer.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = Integer.valueOf(sourceValue);
            }
        } else if (targetType.equals(Integer.TYPE)) {
            targetValue = Integer.valueOf(sourceValue);
        } else if (targetType.equals(Boolean.class)) {
            if (StringUtil.isNotBlank(sourceValue)) {
                targetValue = Boolean.valueOf(sourceValue);
            }
        } else if (targetType.equals(Boolean.TYPE)) {
            targetValue = Boolean.valueOf(sourceValue);
        } else if (targetType.equals(URI.class)) {
            try {
                targetValue = new URI(sourceValue);
            } catch (URISyntaxException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else if (targetType.equals(File.class)) {
            targetValue = new File(sourceValue);
        } else if (targetType.equals(GuardedByteArray.class)) {
            targetValue = new GuardedByteArray(sourceValue.getBytes());
        } else if (targetType.equals(GuardedString.class)) {
            targetValue = new GuardedString(sourceValue.toCharArray());
        } else if (targetType.equals(Script.class)) {
            String scriptLanguage = value.getAttribute("scriptLanguage");
            if (StringUtil.isBlank(scriptLanguage)) {
                throw new MojoExecutionException(
                        "Missing argument 'scriptLanguage' for Script attribute: " + name);
            }
            targetValue =
                    new ScriptBuilder().setScriptLanguage(scriptLanguage)
                            .setScriptText(sourceValue).build();
        } else {
            log.warn("Cast to targetType: '" + targetType + "' is not supported");
        }
        return targetValue;
    }

}
