/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
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

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.ReaderFactory;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SchemaOp;

/**
 * A ConnectorDocBuilder does ...
 * 
 * @author Laszlo Hordos
 */
public class ConnectorDocBuilder {

    private final ConnectorMojoBridge handler;

    public ConnectorDocBuilder(ConnectorMojoBridge bridge) {
        this.handler = bridge;
    }

    /**
     * Execute the generation of the report.
     * 
     * @throws org.apache.maven.reporting.MavenReportException
     *             if any
     */
    protected void executeReport() throws MojoExecutionException {
        ConnectorInfoManager mgr = null;
        try {
            mgr =
                    ConnectorInfoManagerFactory.getInstance().getLocalManager(
                            handler.getBuildOutputDirectory().toURI().toURL());
        } catch (Exception e) {
            handler.getLog().error("Failed to get the ConnectorInfoManager", e);
            return;
        }

        for (ConnectorInfo info : mgr.getConnectorInfos()) {
            handler.getLog().debug("Processing ConnectorInfo: " + info.toString());
            try {
                Context context = new VelocityContext();
                context.put("connectorInfo", info);
                context.put("connectorDisplayName", info.getConnectorDisplayName());

                APIConfiguration config = info.createDefaultAPIConfiguration();
                context.put("APIConfiguration", config);

                Class<? extends Connector> connectorClass = null;

                try {
                    connectorClass = ((LocalConnectorInfoImpl) info).getConnectorClass();
                    if (SchemaOp.class.isAssignableFrom(connectorClass)) {
                        Schema schema = null;
                        try {
                            // TODO: Configure the connector
                            schema =
                                    ConnectorFacadeFactory.getInstance().newInstance(config)
                                            .schema();
                        } catch (Throwable t) {
                            handler.getLog().error("Getting Schema with ConnectorFacade", t);
                        }
                        try {
                            SchemaOp connector = (SchemaOp) connectorClass.newInstance();
                            schema = connector.schema();
                        } catch (Throwable t) {
                            handler.getLog().error("Getting Schema with Connector Instance", t);
                        }

                        if (null != schema) {
                            Map<ObjectClassInfo, Set<Class<? extends APIOperation>>> supportedOperations =
                                    new HashMap<ObjectClassInfo, Set<Class<? extends APIOperation>>>(
                                            schema.getObjectClassInfo().size());

                            for (Class<? extends APIOperation> op : config.getSupportedOperations()) {
                                for (ObjectClassInfo oci : schema
                                        .getSupportedObjectClassesByOperation(op)) {
                                    if (null != oci) {
                                        Set<Class<? extends APIOperation>> ops =
                                                supportedOperations.get(oci);
                                        if (null == ops) {
                                            ops =
                                                    new TreeSet<Class<? extends APIOperation>>(
                                                            new ClassComparator());
                                            supportedOperations.put(oci, ops);
                                        }
                                        ops.add(op);
                                    }
                                }
                            }

                            context.put("schema", schema);
                            context.put("supportedOperations", supportedOperations);
                        }
                    }
                } catch (Throwable e) {
                    handler.getLog().error("Getting the default Schema.", e);
                }

                try {
                    Set<String> interfaces = new TreeSet<String>();
                    for (Class clazz : connectorClass.getInterfaces()) {
                        interfaces.add(clazz.getSimpleName());
                    }
                    context.put("connectorInterfaces", interfaces);
                } catch (Throwable e) {
                    handler.getLog().error("Getting the connector interfaces.", e);
                }

                Set<ConfigurationProperty> requiredConfigurationProperties =
                        new TreeSet<ConfigurationProperty>(new ConfigurationPropertyComparator());
                context.put("requiredConfigurationProperties", requiredConfigurationProperties);
                Set<ConfigurationProperty> optionalConfigurationProperties =
                        new TreeSet<ConfigurationProperty>(new ConfigurationPropertyComparator());
                context.put("optionalConfigurationProperties", optionalConfigurationProperties);

                Map<String, Set<ConfigurationProperty>> requiredOperationProperties =
                        new TreeMap<String, Set<ConfigurationProperty>>();

                context.put("requiredOperationProperties", requiredOperationProperties);

                for (String propertyName : config.getConfigurationProperties().getPropertyNames()) {
                    ConfigurationProperty property =
                            config.getConfigurationProperties().getProperty(propertyName);

                    if (property.isRequired()) {
                        requiredConfigurationProperties.add(property);
                    } else if (null != property.getOperations()
                            && !property.getOperations().isEmpty()) {
                        for (Class<? extends APIOperation> clazz : property.getOperations()) {
                            Set<ConfigurationProperty> properties =
                                    requiredOperationProperties.get(clazz.getSimpleName());
                            if (null == properties) {
                                properties =
                                        new TreeSet<ConfigurationProperty>(
                                                new ConfigurationPropertyComparator());

                                requiredOperationProperties.put(clazz.getSimpleName(), properties);
                            }
                            properties.add(property);
                        }

                    } else {
                        optionalConfigurationProperties.add(property);
                    }
                }

                context.put("connectorPoolingSupported", config.isConnectorPoolingSupported());

                context.put("PathTool", new PathTool());

                context.put("FileUtils", new FileUtils());

                context.put("StringUtils", new org.codehaus.plexus.util.StringUtils());

                context.put("ConnectorUtils", new ConnectorUtils());

                context.put("i18n", handler.getI18N());

                context.put("project", handler.getMavenProject());

                handler.generate(this, context, connectorClass);

            } catch (ResourceNotFoundException e) {
                throw new MojoExecutionException("Resource not found.", e);
            } catch (VelocityException e) {
                throw new MojoExecutionException(e.toString(), e);
            }
        }
    }

    /**
     * Create the velocity template
     * 
     * @param context
     *            velocity context that has the parameter values
     * @param templateName
     *            velocity template which will the context be merged
     * @throws org.apache.velocity.exception.ResourceNotFoundException
     *             , VelocityException, IOException
     */
    public void processTemplate(VelocityEngine engine, Context context, String templateName,
            Writer writer) throws MojoExecutionException {
        try {

            engine.setApplicationAttribute("baseDirectory", handler.getBasedir());

            String sourceEncoding = handler.getSourceEncoding();
            if (StringUtils.isEmpty(sourceEncoding)) {
                sourceEncoding = ReaderFactory.FILE_ENCODING;
                handler.getLog().warn(
                        "File encoding has not been set, using platform encoding "
                                + handler.getSourceEncoding()
                                + ", i.e. build is platform dependent!");
            }

            Template velocityTemplate =
                    engine.getTemplate(handler.getTemplateDirectory() + "/" + templateName,
                            sourceEncoding);

            velocityTemplate.merge(context, writer);

        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Template not found. ( "
                    + handler.getTemplateDirectory() + "/" + templateName + " )");
        } catch (VelocityException e) {
            throw new VelocityException(e.toString());
        } catch (Exception e) {
            if (e.getCause() != null) {
                handler.getLog().warn(e.getCause());
            }
            throw new MojoExecutionException(e.toString(), e.getCause());
        }
    }

}
