/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS. All Rights Reserved
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
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.ReaderFactory;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConfigurationProperty;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.api.ConnectorInfo;
import org.identityconnectors.framework.api.ConnectorInfoManager;
import org.identityconnectors.framework.api.ConnectorInfoManagerFactory;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.ResolveUsernameApiOp;
import org.identityconnectors.framework.api.operations.SchemaApiOp;
import org.identityconnectors.framework.api.operations.ScriptOnConnectorApiOp;
import org.identityconnectors.framework.api.operations.ScriptOnResourceApiOp;
import org.identityconnectors.framework.api.operations.SearchApiOp;
import org.identityconnectors.framework.api.operations.SyncApiOp;
import org.identityconnectors.framework.api.operations.TestApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.impl.api.local.LocalConnectorInfoImpl;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SchemaOp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * A ConnectorDocBuilder .
 *
 * @author Laszlo Hordos
 */
public class ConnectorDocBuilder {

    private final ConnectorMojoBridge handler;

    public ConnectorDocBuilder(ConnectorMojoBridge bridge) {
        this.handler = bridge;
    }

    protected static final List<Class<? extends APIOperation>> OBJECTCLASS_OPERATIONS =
            CollectionUtil.newList(AuthenticationApiOp.class, CreateApiOp.class, DeleteApiOp.class,
                    ResolveUsernameApiOp.class, SearchApiOp.class, SyncApiOp.class,
                    UpdateApiOp.class);

    protected static final List<Class<? extends APIOperation>> OPERATIONS = CollectionUtil.newList(
            AuthenticationApiOp.class, CreateApiOp.class, DeleteApiOp.class,
            ResolveUsernameApiOp.class, SchemaApiOp.class, ScriptOnConnectorApiOp.class,
            ScriptOnResourceApiOp.class, SearchApiOp.class, SyncApiOp.class, TestApiOp.class,
            UpdateApiOp.class);

    protected static final Map<Class<? extends APIOperation>, String> OP_DICTIONARY;

    static {
        Map<Class<? extends APIOperation>, String> dictionary =
                new HashMap<Class<? extends APIOperation>, String>();
        dictionary.put(AuthenticationApiOp.class, "Authenticate");
        dictionary.put(CreateApiOp.class, "Create");
        dictionary.put(DeleteApiOp.class, "Delete");
        dictionary.put(ResolveUsernameApiOp.class, "Resolve username");
        dictionary.put(SchemaApiOp.class, "Schema");
        dictionary.put(ScriptOnConnectorApiOp.class, "Script on connector");
        dictionary.put(ScriptOnResourceApiOp.class, "Script on resource");
        dictionary.put(SearchApiOp.class, "Read/Search");
        dictionary.put(SyncApiOp.class, "Sync");
        dictionary.put(TestApiOp.class, "Test");
        dictionary.put(UpdateApiOp.class, "Update");
        OP_DICTIONARY = CollectionUtil.newReadOnlyMap(dictionary);
    }

    /**
     * Execute the generation of the report.
     *
     * @throws org.apache.maven.reporting.MavenReportException
     *             if any
     */
    protected void executeReport() throws MojoExecutionException {
        List<ConnectorInfo> infoList = null;
        try {
            infoList = listAllConnectorInfo();
        } catch (Exception e) {
            handler.getLog().error("Failed to get the ConnectorInfoManager", e);
            return;
        }

        for (ConnectorInfo info : infoList) {
            handler.getLog().debug("Processing ConnectorInfo: " + info.toString());
            try {
                Context context = new VelocityContext();
                context.put("connectorInfo", info);
                context.put("connectorDisplayName", info.getConnectorDisplayName());

                APIConfiguration config = info.createDefaultAPIConfiguration();
                context.put("APIConfiguration", config);
                String connectorName =
                        info.getConnectorKey().getConnectorName().substring(
                                info.getConnectorKey().getConnectorName().lastIndexOf('.') + 1)
                                .toLowerCase();
                if (connectorName.endsWith("connector")) {
                    connectorName =
                            connectorName.substring(0, connectorName.length() - 9) + "-connector";
                }
                context.put("connectorName", connectorName);

                try {
                    if (config.getSupportedOperations().contains(SchemaApiOp.class)) {
                        Schema schema = null;
                        try {
                            APIConfiguration facadeConfig = info.createDefaultAPIConfiguration();
                            if (null != handler.getConfigurationProperties()) {
                                handler.getConfigurationProperties().mergeConfigurationProperties(
                                        facadeConfig.getConfigurationProperties());
                            }
                            schema =
                                    ConnectorFacadeFactory.getInstance().newInstance(facadeConfig)
                                            .schema();
                        } catch (Throwable t) {
                            handler.getLog().debug("Getting Schema with ConnectorFacade", t);
                        }

                        if (null == schema && info instanceof LocalConnectorInfoImpl) {
                            Class<? extends Connector> connectorClass =
                                    ((LocalConnectorInfoImpl) info).getConnectorClass();
                            try {
                                SchemaOp connector = (SchemaOp) connectorClass.newInstance();
                                schema = connector.schema();
                            } catch (Throwable t) {
                                handler.getLog().debug("Getting Schema with Connector Instance", t);
                            }
                        }

                        if (null != schema) {

                            SortedMap<String, List<Map<String, Object>>> operationOptionsMap =
                                    new TreeMap<String, List<Map<String, Object>>>();

                            for (Class<? extends APIOperation> op : OPERATIONS) {
                                if (SchemaApiOp.class.equals(op) || TestApiOp.class.equals(op)) {
                                    continue;
                                }
                                List<Map<String, Object>> optionList = null;
                                for (OperationOptionInfo optionInfo : schema
                                        .getSupportedOptionsByOperation(op)) {
                                    Map<String, Object> optionInfoMap =
                                            new HashMap<String, Object>();
                                    optionInfoMap.put("name", optionInfo.getName());
                                    optionInfoMap.put("type", optionInfo.getType().getSimpleName());
                                    optionInfoMap.put("description", info.getMessages().format(
                                            optionInfo.getName() + ".help", null));
                                    if (null == optionList) {
                                        optionList = new ArrayList<Map<String, Object>>();
                                    }
                                    optionList.add(optionInfoMap);
                                }
                                if (null != optionList) {
                                    operationOptionsMap.put(OP_DICTIONARY.get(op), optionList);
                                }
                            }

                            List<Map<String, Object>> objectClasses =
                                    new ArrayList<Map<String, Object>>();

                            for (ObjectClassInfo objectClassInfo : schema.getObjectClassInfo()) {
                                Map<String, Object> objectClassInfoMap =
                                        new HashMap<String, Object>();
                                ObjectClass oc = new ObjectClass(objectClassInfo.getType());
                                objectClassInfoMap.put("name", objectClassInfo.getType());
                                objectClassInfoMap.put("displayName", info.getMessages().format(
                                        oc.getDisplayNameKey(), oc.getObjectClassValue()));
                                objectClassInfoMap.put("attributes", objectClassInfo
                                        .getAttributeInfo());

                                boolean limited = false;
                                List<String> operations = new ArrayList<String>();

                                for (Class<? extends APIOperation> op : OBJECTCLASS_OPERATIONS) {
                                    if (schema.getSupportedObjectClassesByOperation(op).contains(
                                            objectClassInfo)) {
                                        operations.add(OP_DICTIONARY.get(op));
                                    } else {
                                        limited = true;
                                    }
                                }

                                objectClassInfoMap.put("operations", limited ? operations : null);
                                objectClasses.add(objectClassInfoMap);
                            }

                            context.put("schema", schema);
                            context.put("operationOptions", operationOptionsMap);
                            context.put("objectClasses", objectClasses);
                        }
                    }

                } catch (Throwable e) {
                    handler.getLog().error("Getting the default Schema.", e);
                }

                try {
                    Set<String> interfaces = new TreeSet<String>();
                    for (Object clazz : config.getSupportedOperations()) {
                        interfaces.add(((Class) clazz).getSimpleName());
                    }
                    context.put("connectorInterfaces", interfaces);
                } catch (Throwable e) {
                    handler.getLog().error("Getting the connector interfaces.", e);
                }

                Map<String, List<Map<String, Object>>> configurationTable =
                        new LinkedHashMap<String, List<Map<String, Object>>>();

                for (String propertyName : config.getConfigurationProperties().getPropertyNames()) {
                    ConfigurationProperty property =
                            config.getConfigurationProperties().getProperty(propertyName);

                    String groupKey = property.getGroup("Configuration");

                    List<Map<String, Object>> configurationGroup = configurationTable.get(groupKey);
                    if (configurationGroup == null) {
                        configurationGroup = new ArrayList<Map<String, Object>>();
                        configurationTable.put(groupKey, configurationGroup);
                    }

                    Map<String, Object> propertyMap = new HashMap<String, Object>();

                    propertyMap.put("name", propertyName);
                    propertyMap.put("type", property.getType().getSimpleName());
                    propertyMap.put("value", property.getValue());
                    propertyMap.put("required", property.isRequired());
                    propertyMap.put("operations", convertOperations(property.getOperations()));
                    propertyMap.put("confidential", property.isConfidential());
                    propertyMap.put("description", convertHTMLtoDocBook(property
                            .getHelpMessage("Description is now available")));

                    configurationGroup.add(propertyMap);
                }

                context.put("configurationProperties", configurationTable);

                context.put("connectorPoolingSupported", config.isConnectorPoolingSupported());

                context.put("PathTool", new PathTool());

                context.put("FileUtils", new FileUtils());

                context.put("StringUtils", new org.codehaus.plexus.util.StringUtils());

                context.put("ConnectorUtils", new ConnectorUtils());

                context.put("i18n", handler.getI18N());

                context.put("project", handler.getMavenProject());

                try {
                    Map<String, Object> value = new HashMap<String, Object>();

                    value.put("operationOptions", context.get("operationOptions"));
                    value.put("objectClasses", context.get("objectClasses"));
                    value.put("connectorInterfaces", context.get("connectorInterfaces"));
                    value.put("configurationProperties", context.get("configurationProperties"));
                    value.put("connectorPoolingSupported", context.get("connectorPoolingSupported"));

                    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                    ow.writeValue(new File(handler.getBuildOutputDirectory(), "configDrop.txt"),
                            value);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                handler.generate(this, context, connectorName);

            } catch (ResourceNotFoundException e) {
                throw new MojoExecutionException("Resource not found.", e);
            } catch (VelocityException e) {
                throw new MojoExecutionException(e.toString(), e);
            }
        }
    }

    private String convertHTMLtoDocBook(String source) {
        return source;
    }

    private Set<String> convertOperations(Set<Class<? extends APIOperation>> operations) {
        if (null != operations) {
            Set<String> operationNames = new TreeSet<String>();
            for (Class<? extends APIOperation> op : operations) {
                operationNames.add(OP_DICTIONARY.get(op));
            }
            return operationNames;
        }
        return null;
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

    public List<ConnectorInfo> listAllConnectorInfo() throws MojoExecutionException {
        List<ConnectorInfo> infos = new ArrayList<ConnectorInfo>();
        PatternIncludesArtifactFilter includesFilter =
                handler.getIncludes() == null ? null : new PatternIncludesArtifactFilter(Arrays
                        .asList(handler.getIncludes()));
        PatternExcludesArtifactFilter excludesFilter =
                handler.getExcludes() == null ? null : new PatternExcludesArtifactFilter(Arrays
                        .asList(handler.getExcludes()));

        for (ConnectorInfo info : getConnectorInfoManager().getConnectorInfos()) {
            Artifact artifact =
                    new DefaultArtifact(info.getConnectorKey().getBundleName(), info
                            .getConnectorKey().getConnectorName(), info.getConnectorKey()
                            .getBundleVersion(), "compile", info.getConnectorKey()
                            .getBundleVersion(), "", null);

            if (includesFilter != null && !includesFilter.include(artifact)) {
                handler.getLog().debug(
                        "Connector " + info.getConnectorKey()
                                + " ignored as it does not match include rules.");
                continue;
            }

            if (excludesFilter != null && !excludesFilter.include(artifact)) {
                handler.getLog().debug(
                        "Connector " + info.getConnectorKey()
                                + " ignored as it does matches exclude rules.");
                continue;
            }

            handler.getLog().info("Processing ConnectorInfo: " + info.getConnectorKey());
            infos.add(info);

        }
        return infos;
    }

    public ConnectorInfoManager getConnectorInfoManager() throws MojoExecutionException {
        if (null != handler.getRemoteFrameworkConnectionInfo()) {
            return ConnectorInfoManagerFactory.getInstance().getRemoteManager(
                    handler.getRemoteFrameworkConnectionInfo());
        } else if (handler.getBuildOutputDirectory().exists()) {
            try {
                return ConnectorInfoManagerFactory.getInstance().getLocalManager(
                        handler.getBuildOutputDirectory().toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else if ("pom".equalsIgnoreCase(handler.getMavenProject().getPackaging())) {
            throw new MojoExecutionException(
                    "The required remoteFrameworkConnectionInfo is missing");
        } else {
            throw new MojoExecutionException("The " + handler.getBuildOutputDirectory()
                    + " is not exist");
        }
    }

}
