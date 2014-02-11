/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock Inc. All rights reserved.
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
package org.forgerock.openicf.misc.scriptedcommon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;

/**
 * Main implementation of the Scripted Common code.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 * @version 1.1.0.0
 * @since 1.0
 */
public class ScriptedConnector implements AuthenticateOp, CreateOp, Connector, DeleteOp, ScriptOnConnectorOp, SchemaOp, SearchOp<Map>, SyncOp, TestOp, UpdateAttributeValuesOp {

    /**
     * Setup logging for the {@link ScriptedConnector}.
     */
    private static final Log log = Log.getLog(ScriptedConnector.class);
    /**
     * Place holder for the Connection created in the init method.
     */
    protected ScriptedConnection connection;
    /**
     * Place holder for the Connector schema created in the Schema() method.
     */
    private Schema schema;
    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link ScriptedConnector#init(org.identityconnectors.framework.spi.Configuration)}.
     */
    protected ScriptedConfiguration configuration;
    /**
     * Scripts executors for each SPI operations.
     */
    private ScriptExecutorFactory factory = null;
    private ScriptExecutor authenticateExecutor = null;
    private ScriptExecutor createExecutor = null;
    private ScriptExecutor updateExecutor = null;
    private ScriptExecutor deleteExecutor = null;
    private ScriptExecutor searchExecutor = null;
    private ScriptExecutor syncExecutor = null;
    private ScriptExecutor schemaExecutor = null;
    private ScriptExecutor testExecutor = null;

    /**
     * Gets the Configuration context for this connector.
     *
     * @return The current {@link Configuration}
     */
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param configuration the new {@link Configuration}
     *
     */
    @Override
    public void init(final Configuration config) {
        this.configuration = (ScriptedConfiguration) config;
        this.factory = ScriptExecutorFactory.newInstance("GROOVY");

        // Evaluate and compile every executors
        authenticateExecutor = getScriptExecutor(configuration.getAuthenticateScriptFileName());
        createExecutor = getScriptExecutor(configuration.getCreateScriptFileName());
        updateExecutor = getScriptExecutor(configuration.getUpdateScriptFileName());
        deleteExecutor = getScriptExecutor(configuration.getDeleteScriptFileName());
        searchExecutor = getScriptExecutor(configuration.getSearchScriptFileName());
        syncExecutor = getScriptExecutor(configuration.getSyncScriptFileName());
        schemaExecutor = getScriptExecutor(configuration.getSchemaScriptFileName());
        testExecutor = getScriptExecutor(configuration.getTestScriptFileName());
        log.info("Scripts loaded");
    }

    /**
     * Disposes of the {@link ScriptedConnector}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    @Override
    public void dispose() {
        configuration = null;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    /**
     * SPI Operations
     *
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     */
    /**
     * {@inheritDoc}
     */
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password, OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            authenticateExecutor = getScriptExecutor(configuration.getAuthenticateScriptFileName());
            log.ok("Authenticate script reloaded");
        }
        if (authenticateExecutor != null) {
            log.ok("Object class: {0}", objectClass.getObjectClassValue());

            final Map<String, Object> arguments = new HashMap<String, Object>();

            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "AUTHENTICATE");
            arguments.put("log", log);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("options", options.getOptions());
            arguments.put("username", username);

            // Password - if allowed we provide it in clear
            if (configuration.getClearTextPasswordToScript()) {
                if (password != null) {
                    password.access(new GuardedString.Accessor() {
                        @Override
                        public void access(char[] clearChars) {
                            arguments.put("password", new String(clearChars));
                        }
                    });
                } else {
                    arguments.put("password", null);
                }
            } else {
                arguments.put("password", password);
            }

            try {
                Object uidAfter = authenticateExecutor.execute(arguments);
                if (uidAfter instanceof String) {
                    log.ok("{0} authenticated", uidAfter);
                    return new Uid((String) uidAfter);
                } else {
                    throw new ConnectorException("Authenticate script didn't return with the __UID__ value");
                }
            } catch (Exception e) {
                throw new ConnectorException("Authenticate script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }

    }

    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes, final OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            createExecutor = getScriptExecutor(configuration.getCreateScriptFileName());
            log.ok("Create script reloaded");
        }
        if (createExecutor != null) {
            log.ok("Object class: {0}", objectClass.getObjectClassValue());

            final Map<String, Object> arguments = new HashMap<String, Object>();

            Map<String, List> attrMap = new HashMap<String, List>();
            for (Attribute attr : createAttributes) {
                attrMap.put(attr.getName(), attr.getValue());
            }
            arguments.put("attributes", attrMap);
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "CREATE");
            arguments.put("log", log);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("options", options.getOptions());
            // We give the id (name) as an argument, more friendly than dealing with __NAME__
            arguments.put("id", null);
            if (AttributeUtil.getNameFromAttributes(createAttributes) != null) {
                arguments.put("id", AttributeUtil.getNameFromAttributes(createAttributes).getNameValue());
                attrMap.remove("__NAME__");
            }
            // Password - if allowed we provide it in clear
            if (configuration.getClearTextPasswordToScript()) {
                GuardedString gpasswd = AttributeUtil.getPasswordValue(createAttributes);
                if (gpasswd != null) {
                    gpasswd.access(new GuardedString.Accessor() {
                        @Override
                        public void access(char[] clearChars) {
                            arguments.put("password", new String(clearChars));
                        }
                    });
                } else {
                    arguments.put("password", null);
                }
            }

            try {
                Object uidAfter = createExecutor.execute(arguments);
                if (uidAfter instanceof String) {
                    log.ok("{0} created", uidAfter);
                    return new Uid((String) uidAfter);
                } else {
                    throw new ConnectorException("Create script didn't return with the __UID__ value");
                }
            } catch (Exception e) {
                throw new ConnectorException("Create script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            deleteExecutor = getScriptExecutor(configuration.getDeleteScriptFileName());
            log.ok("Delete script reloaded");
        }
        if (deleteExecutor != null) {
            log.ok("Object class: {0}", objectClass.getObjectClassValue());

            final String id = uid.getUidValue();
            final Map<String, Object> arguments = new HashMap<String, Object>();

            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "DELETE");
            arguments.put("log", log);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("uid", id);
            arguments.put("options", options.getOptions());

            try {
                deleteExecutor.execute(arguments);
                log.ok("{0} deleted", id);
            } catch (Exception e) {
                throw new ConnectorException("Delete script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        SchemaBuilder scmb = new SchemaBuilder(ScriptedConnector.class);
        if (configuration.isReloadScriptOnExecution()) {
            schemaExecutor = getScriptExecutor(configuration.getSchemaScriptFileName());
            log.ok("Schema script reloaded");
        }
        if (schemaExecutor != null) {
            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "SCHEMA");
            arguments.put("log", log);
            arguments.put("builder", scmb);
            try {
                schemaExecutor.execute(arguments);
            } catch (Exception e) {
                throw new ConnectorException("Schema script error", e);
            }
        } else {
            throw new UnsupportedOperationException("SCHEMA script executor is null. Problem loading Schema script");
        }
        schema = scmb.build();
        return schema;
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<Map> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        log.ok("ObjectClass: {0}", objectClass.getObjectClassValue());
        return new ScriptedFilterTranslator();
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, Map query, ResultsHandler handler, OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            searchExecutor = getScriptExecutor(configuration.getSearchScriptFileName());
            log.ok("Search script reloaded");
        }

        if (searchExecutor != null) {
            log.ok("ObjectClass: {0}", objectClass.getObjectClassValue());

            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("action", "SEARCH");
            arguments.put("log", log);
            arguments.put("options", options.getOptions());
            arguments.put("query", query);
            try {
                List<Map<String, Object>> results = (List<Map<String, Object>>) searchExecutor.execute(arguments);
                log.ok("Search ok");
                processResults(objectClass, results, handler);
            } catch (Exception e) {
                throw new ConnectorException("Search script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler, final OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            syncExecutor = getScriptExecutor(configuration.getSyncScriptFileName());
            log.ok("Sync script reloaded");
        }

        if (syncExecutor != null) {
            log.ok("ObjectClass: {0}", objectClass.getObjectClassValue());

            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("action", "SYNC");
            arguments.put("log", log);
            arguments.put("options", options.getOptions());
            arguments.put("token", token != null ? token.getValue() : null);
            try {
                List<Map<String, Object>> results = (List<Map<String, Object>>) syncExecutor.execute(arguments);
                log.ok("Sync ok");
                processDeltas(objectClass, results, handler);
            } catch (Exception e) {
                throw new ConnectorException("Sync script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        if (configuration.isReloadScriptOnExecution()) {
            syncExecutor = getScriptExecutor(configuration.getSyncScriptFileName());
            log.ok("Sync script reloaded");
        }
        if (syncExecutor != null) {
            SyncToken st = null;
            log.ok("ObjectClass: {0}", objectClass.getObjectClassValue());

            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("objectClass", objectClass.getObjectClassValue());
            arguments.put("action", "GET_LATEST_SYNC_TOKEN");
            arguments.put("log", log);
            try {
                // We expect the script to return a value (or null) that makes the sync token
                // !! result has to be one of the framework known types...
                Object result = syncExecutor.execute(arguments);
                log.ok("GetLatestSyncToken ok");
                FrameworkUtil.checkAttributeType(result.getClass());
                st = new SyncToken(result);
            } catch (java.lang.IllegalArgumentException ae) {
                throw new ConnectorException("Unknown Token type", ae);
            } catch (Exception e) {
                throw new ConnectorException("Sync (GetLatestSyncToken) script error", e);
            }
            return st;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void test() {
        configuration.validate();

        if (configuration.isReloadScriptOnExecution()) {
            testExecutor = getScriptExecutor(configuration.getTestScriptFileName());
            log.ok("Test script reloaded");
        }

        if (testExecutor != null) {
            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "TEST");
            arguments.put("log", log);
            try {
                testExecutor.execute(arguments);
                log.ok("Test ok");
            } catch (Exception e) {
                throw new ConnectorException("Test script error", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
        if ("GROOVY".equalsIgnoreCase(request.getScriptLanguage()) && request.getScriptText() != null) {
            final Map<String, Object> arguments = new HashMap<String, Object>();
            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", "RUNSCRIPTONCONNECTOR");
            arguments.put("log", log);
            arguments.put("options", options.getOptions());
            arguments.put("scriptArguments", request.getScriptArguments());

            ScriptExecutor se = factory.newScriptExecutor(getClass().getClassLoader(), request.getScriptText(), true);
            try {
                Object res = se.execute(arguments);
                log.ok("runScriptOnConnector ok");
                return res;
            } catch (Exception e) {
                throw new ConnectorException("runScriptOnConnector script error", e);
            }
        } else {
            throw new ConfigurationException("Only Groovy is supported");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        return genericUpdate("UPDATE", objectClass, uid, replaceAttributes, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
        return genericUpdate("ADD_ATTRIBUTE_VALUES", objectClass, uid, valuesToAdd, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
        return genericUpdate("REMOVE_ATTRIBUTE_VALUES", objectClass, uid, valuesToRemove, options);
    }

    // Private
    private Uid genericUpdate(String method, ObjectClass objClass, Uid uid, Set<Attribute> attrs, OperationOptions options) {
        if (configuration.isReloadScriptOnExecution()) {
            updateExecutor = getScriptExecutor(configuration.getUpdateScriptFileName());
            log.ok("Update ({0}) script reloaded", method);
        }
        if (updateExecutor != null) {
            log.ok("Object class: {0}", objClass.getObjectClassValue());

            final String id = uid.getUidValue();
            final Map<String, Object> arguments = new HashMap<String, Object>();

            arguments.put("connection", connection.getConnectionHandler());
            arguments.put("configuration", configuration);
            arguments.put("action", method);
            arguments.put("log", log);
            arguments.put("objectClass", objClass.getObjectClassValue());
            arguments.put("uid", id);
            arguments.put("options", options.getOptions());

            Map<String, List> attrMap = new HashMap<String, List>();
            for (Attribute attr : attrs) {
                if (OperationalAttributes.isOperationalAttribute(attr)) {
                    if (method.equalsIgnoreCase("UPDATE")) {
                        attrMap.put(attr.getName(), attr.getValue());
                    }
                } else {
                    attrMap.put(attr.getName(), attr.getValue());
                }
            }
            arguments.put("attributes", attrMap);

            // Do we need to update the password?
            if (configuration.getClearTextPasswordToScript() && method.equalsIgnoreCase("UPDATE")) {
                GuardedString gpasswd = AttributeUtil.getPasswordValue(attrs);
                if (gpasswd != null) {
                    gpasswd.access(new GuardedString.Accessor() {
                        public void access(char[] clearChars) {
                            arguments.put("password", new String(clearChars));
                        }
                    });
                } else {
                    arguments.put("password", null);
                }
            }
            try {
                Object uidAfter = updateExecutor.execute(arguments);
                if (uidAfter instanceof String) {
                    log.ok("{0} updated ({1})", uidAfter, method);
                    return new Uid((String) uidAfter);
                }
            } catch (Exception e) {
                throw new ConnectorException("Update(" + method + ") script error", e);
            }
            throw new ConnectorException("Update script didn't return with the __UID__ value");
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void processResults(ObjectClass objClass, List<Map<String, Object>> results, ResultsHandler handler) {

        // Let's iterate over the results:
        for (Map<String, Object> result : results) {
            ConnectorObjectBuilder cobld = new ConnectorObjectBuilder();
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                final String attrName = entry.getKey();
                final Object attrValue = entry.getValue();
                // Special first
                if (attrName.equalsIgnoreCase("__UID__")) {
                    if (attrValue == null) {
                        throw new IllegalArgumentException("Uid cannot be null");
                    }
                    cobld.setUid(attrValue.toString());
                } else if (attrName.equalsIgnoreCase("__NAME__")) {
                    if (attrValue == null) {
                        throw new IllegalArgumentException("Name cannot be null");
                    }
                    cobld.setName(attrValue.toString());
                } else if (attrName.equalsIgnoreCase("password")) {
                    // is there a chance we fetch password from search?
                } else {
                    if (attrValue instanceof Collection) {
                        cobld.addAttribute(AttributeBuilder.build(attrName, (Collection) attrValue));
                    } else if (attrValue != null) {
                        cobld.addAttribute(AttributeBuilder.build(attrName, attrValue));
                    } else {
                        cobld.addAttribute(AttributeBuilder.build(attrName));
                    }
                }
            }
            cobld.setObjectClass(objClass);
            handler.handle(cobld.build());
            log.ok("ConnectorObject is built");
        }
    }

    private void processDeltas(ObjectClass objClass, List<Map<String, Object>> results, SyncResultsHandler handler) {

        // Let's iterate over the results:
        for (Map<String, Object> result : results) {
            // The Map should look like:
            // token: <Object> token
            // operation: <String> CREATE_OR_UPDATE|DELETE (defaults to CREATE_OR_UPDATE)
            // uid: <String> uid
            // previousUid: <String> prevuid (This is for rename ops)
            // password: <String> password
            // attributes: <Map> of attributes <String>name/<List>values
            SyncDeltaBuilder syncbld = new SyncDeltaBuilder();
            String uid = (String) result.get("uid");
            if (uid != null && !uid.isEmpty()) {
                syncbld.setUid(new Uid(uid));
                Object token = result.get("token");
                // Null token, set some acceptable value
                if (token == null) {
                    log.ok("token value is null, replacing to 0L");
                    token = 0L;
                }
                syncbld.setToken(new SyncToken(token));

                // Start building the connector object
                ConnectorObjectBuilder cobld = new ConnectorObjectBuilder();
                cobld.setName(uid);
                cobld.setUid(uid);
                cobld.setObjectClass(objClass);

                // operation
                // We assume that if DELETE, then we don't need to care about the rest
                String op = (String) result.get("operation");
                if (op != null && op.equalsIgnoreCase("DELETE")) {
                    syncbld.setDeltaType(SyncDeltaType.DELETE);

                } else {
                    // we assume this is CREATE_OR_UPDATE
                    syncbld.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);

                    // previous UID
                    String prevUid = (String) result.get("previousUid");
                    if (prevUid != null && !prevUid.isEmpty()) {
                        syncbld.setPreviousUid(new Uid(prevUid));
                    }

                    // password? is password valid if empty string? let's assume yes...
                    if (result.get("password") != null) {
                        cobld.addAttribute(AttributeBuilder.buildCurrentPassword(((String) result.get("password")).toCharArray()));
                    }

                    // Remaining attributes
                    for (Map.Entry<String, List> attr : ((Map<String, List>) result.get("attributes")).entrySet()) {
                        final String attrName = attr.getKey();
                        final Object attrValue = attr.getValue();
                        if (attrValue instanceof Collection) {
                            cobld.addAttribute(AttributeBuilder.build(attrName, (Collection) attrValue));
                        } else if (attrValue != null) {
                            cobld.addAttribute(AttributeBuilder.build(attrName, attrValue));
                        } else {
                            cobld.addAttribute(AttributeBuilder.build(attrName));
                        }
                    }
                }
                syncbld.setObject(cobld.build());
                if (!handler.handle(syncbld.build())) {
                    log.ok("Stop processing of the sync result set");
                    break;
                }
            } else {
                // we have a null uid... mmmm....
            }
        }
    }

    private String readFile(String filename) {
        File file = new File(filename);
        StringBuffer contents = new StringBuffer();
        BufferedReader reader = null;
        String text;

        try {
            reader = new BufferedReader(new FileReader(file));
            while ((text = reader.readLine()) != null) {
                contents.append(text).append(System.getProperty(
                        "line.separator"));
            }
        } catch (FileNotFoundException e) {
            throw new ConnectorException(filename + " not found", e);
        } catch (IOException e) {
            throw new ConnectorException(filename, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                throw new ConnectorException(filename, e);
            }
        }
        return contents.toString();
    }

    private ScriptExecutor getScriptExecutor(String scriptFileName) {
        String scriptCode;
        ScriptExecutor scriptExec = null;

        try {
            if (scriptFileName != null) {
                scriptCode = readFile(scriptFileName);
                if (scriptCode.length() > 0) {
                    scriptExec = factory.newScriptExecutor(getClass().getClassLoader(), scriptCode, true);
                }
            }
        } catch (Exception e) {
            throw new ConnectorException("Script error", e);
        }
        return scriptExec;
    }
}
