/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.misc.scriptedcommon

import org.codehaus.groovy.runtime.InvokerHelper
import org.identityconnectors.common.CollectionUtil
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.common.security.SecurityUtil
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.OperationalAttributes
import org.identityconnectors.framework.common.objects.ResultsHandler
import org.identityconnectors.framework.common.objects.Schema
import org.identityconnectors.framework.common.objects.ScriptContext
import org.identityconnectors.framework.common.objects.SearchResult
import org.identityconnectors.framework.common.objects.SyncDelta
import org.identityconnectors.framework.common.objects.SyncResultsHandler
import org.identityconnectors.framework.common.objects.SyncToken
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.FilterTranslator
import org.identityconnectors.framework.spi.Configuration
import org.identityconnectors.framework.spi.Connector
import org.identityconnectors.framework.spi.SearchResultsHandler
import org.identityconnectors.framework.spi.SyncTokenResultsHandler
import org.identityconnectors.framework.spi.operations.AuthenticateOp
import org.identityconnectors.framework.spi.operations.CreateOp
import org.identityconnectors.framework.spi.operations.DeleteOp
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp
import org.identityconnectors.framework.spi.operations.SchemaOp
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp
import org.identityconnectors.framework.spi.operations.SearchOp
import org.identityconnectors.framework.spi.operations.SyncOp
import org.identityconnectors.framework.spi.operations.TestOp
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp

/**
 * Main implementation of the Scripted Common code.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 * @version 1.4.0.0
 */
public class ScriptedConnectorBase<C extends ScriptedConfiguration> implements AuthenticateOp, CreateOp, DeleteOp, ResolveUsernameOp,
        SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<Filter>, SyncOp, TestOp,
        UpdateAttributeValuesOp {

    protected static final String USERNAME = "username"
    protected static final String PASSWORD = "password"

    protected static final String ACTION = "action"

    protected static final String OBJECT_CLASS = "objectClass"
    protected static final String UID = "uid"
    protected static final String ID = "id"

    protected static final String ATTRIBUTES = "attributes"
    protected static final String OPTIONS = "options"

    protected static final String CONNECTION = "connection"
    protected static final String SCHEMA = "schema"
    protected static final String CONFIGURATION = "configuration"
    protected static final String LOGGER = "log"
    protected static final String TOKEN = "token"
    protected static final String HANDLER = "handler"
    protected static final String QUERY = "query"
    protected static final String BUILDER = "builder"
    protected static final String FILTER = "filter"
    protected static final String GROOVY = "GROOVY"


    public enum Action {
        AUTHENTICATE,
        CREATE,
        DELETE,
        GET_LATEST_SYNC_TOKEN,
        RESOLVE_USERNAME,
        SCHEMA,
        SEARCH,
        SYNC,
        TEST,
        RUNSCRIPTONCONNECTOR,
        RUNSCRIPTONRESOURCE,
        UPDATE,
        ADD_ATTRIBUTE_VALUES,
        REMOVE_ATTRIBUTE_VALUES;
    }

    /**
     * Place holder for the Connector schema created in the Schema() method.
     */
    private Schema schema;

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link ScriptedConnectorBase#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    protected C configuration;

    /**
     * Setup logging for the {@link ScriptedConnectorBase}.
     */
    Logger logger;

    /**
     * Gets the Configuration context for this connector.
     *
     * @return The current {@link Configuration}
     */
    public Configuration getConfiguration() {
        return this.configuration;
    }

    public ScriptedConfiguration getScriptedConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param config
     *            the new {@link Configuration}
     */
    public void init(final Configuration config) {
        this.configuration = (C) config;
        logger = configuration.getLogger(getClass(), 12);
    }

    /**
     * Disposes of the {@link ScriptedConnectorBase}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
        configuration = null;
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
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password,
                            OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getAuthenticateScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_AUTHENTICATE", "Invoke Authenticate ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), username)
            try {
                Object uidAfter = executeAuthenticate(getScriptedConfiguration().getAuthenticateScriptFileName(),
                        objectClass, username, password, options);
                if (uidAfter instanceof String) {
                    logger.debug("{0}:{1} authenticated", objectClass.getObjectClassValue(), uidAfter);
                    return new Uid((String) uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("{0}:{1} authenticated", objectClass.getObjectClassValue(), uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "Authenticate script didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Authenticate script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeAuthenticate(String scriptName, ObjectClass objectClass, String username, GuardedString password,
                                         OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(USERNAME, username);
        arguments.setVariable(PASSWORD, getGuardedStringValue(password));
        return evaluateScript(scriptName, createBinding(arguments, Action.AUTHENTICATE, objectClass, null, null, options));
    }

    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
                      final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getCreateScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_CREATE", "Invoke Create ObjectClass:{0}",
                    objectClass.getObjectClassValue());
            try {
                Object uidAfter = executeCreate(getScriptedConfiguration().getCreateScriptFileName(),
                        objectClass, createAttributes, options)
                if (uidAfter instanceof String) {
                    logger.debug("{0}:{1} created", objectClass.getObjectClassValue(), uidAfter);
                    return new Uid(uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("{0}:{1} created", objectClass.getObjectClassValue(), uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "Create script didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Create script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeCreate(String scriptName, final ObjectClass objectClass, final Set<Attribute> createAttributes,
                                   final OperationOptions options) {
        final Binding arguments = new Binding();
        final Map<String, Attribute> attributes = CollectionUtil.<Attribute> newCaseInsensitiveMap();
        // We give the id (name) as an argument, more friendly than dealing
        // with __NAME__
        arguments.setVariable(ID, null);
        for (Attribute attribute : createAttributes) {
            if (attribute.is(Name.NAME)) {
                arguments.setVariable(ID, AttributeUtil.getSingleValue(attribute))
//            } else if (attribute.is(OperationalAttributes.PASSWORD_NAME) ||
//                    attribute.is(OperationalAttributes.CURRENT_PASSWORD_NAME)) {
//                attributes.put(attribute.getName(),
//                        getGuardedStringValue(AttributeUtil.getGuardedStringValue(attribute)));
            } else {
                attributes.put(attribute.getName(), attribute);
            }
        }
        arguments.setVariable(ATTRIBUTES, attributes);
        return evaluateScript(scriptName, createBinding(arguments, Action.CREATE, objectClass, null, createAttributes, options));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getDeleteScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_DELETE", "Invoke Delete ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            try {
                executeDelete(getScriptedConfiguration().getDeleteScriptFileName(),
                        objectClass, uid, options);
                logger.debug("{0}:{1} deleted", objectClass.getObjectClassValue(), uid);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("DeleteScript error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected void executeDelete(String scriptName, ObjectClass objectClass, Uid uid, OperationOptions options) {
        evaluateScript(scriptName, createBinding(new Binding(), Action.DELETE, objectClass, uid, null, options));
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getResolveUsernameScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_RESOLVE_USERNAME", "Invoke Resolve Username ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), username);
            try {
                Object uidAfter = executeResolveUsername(getScriptedConfiguration().getResolveUsernameScriptFileName(),
                        objectClass, username, options);

                if (uidAfter instanceof String) {
                    logger.debug("Username:{0} resolved to:{1}", username, uidAfter);
                    return new Uid(uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("Username:{0} resolved to:{1}", username, uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "ResolveUsernameScript didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("ResolveUsernameScript error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeResolveUsername(String scriptName, ObjectClass objectClass, String username, OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(USERNAME, username);
        return evaluateScript(scriptName,
                createBinding(arguments, Action.RESOLVE_USERNAME, objectClass, null, null, options));
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSchemaScriptFileName())) {
            if (null == schema) {
                try {
                    logger.debugLocale("DEBUG_INVOKE_SCHEMA", "Invoke Schema");
                    Object result = executeSchema(getScriptedConfiguration().getSchemaScriptFileName(),
                            getClass() as Class<? extends Connector>);
                    if (result instanceof Schema) {
                        schema = result as Schema;
                    }
                } catch (final RuntimeException e) {
                    throw e;
                } catch (final Exception e) {
                    throw new ConnectorException("SchemaScript error", e);
                }
                if (null == schema) {
                    throw new ConnectorException("SchemaScript must return with Schema object")
                }
            }
            return schema;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeSchema(String scriptName, Class<? extends Connector> connectorClass) {
        final Binding arguments = new Binding();
        arguments.setVariable(BUILDER, new ICFObjectBuilder(connectorClass));
        return evaluateScript(scriptName, createBinding(arguments, Action.SCHEMA, null, null, null, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
        if (GROOVY.equalsIgnoreCase(request.getScriptLanguage())
                && StringUtil.isNotBlank(request.getScriptText())) {
            try {
                final GroovyCodeSource codeSource = new GroovyCodeSource(request.scriptText,
                        "Script" + System.currentTimeMillis() + ".groovy", "fix");
                Object result = InvokerHelper.createScript(getScriptedConfiguration().getGroovyScriptEngine().
                        getGroovyClassLoader().parseClass(codeSource, false),
                        new Binding(request.scriptArguments)).run();
                logger.debug("runScriptOnConnector ok");
                return result;
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("RunScriptOnConnector error", e);
            }
        } else {
            throw new InvalidAttributeValueException("Only Groovy is supported");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getScriptOnResourceScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_RUNSCRIPTONRESOURCE", "Invoke RunScriptOnResource")
            try {
                return executeRunScriptOnResource(getScriptedConfiguration().getScriptOnResourceScriptFileName(),
                        request, options);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("ScriptOnResourceScript error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeRunScriptOnResource(String scriptName, ScriptContext request, OperationOptions options) {
        final Binding arguments = new Binding()
        arguments.setVariable("scriptArguments", request.scriptArguments)
        arguments.setVariable("scriptText", request.scriptText)
        arguments.setVariable("scriptLanguage", request.scriptLanguage)

        return evaluateScript(scriptName,
                createBinding(arguments, Action.RUNSCRIPTONRESOURCE, null, null, null, options));
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<Filter> createFilterTranslator(final ObjectClass objectClass,
                                                           final OperationOptions options) {
        return ScriptedFilterTranslator.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(final ObjectClass objectClass, final Filter query, final ResultsHandler handler,
                             final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSearchScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_SEARCH", "Invoke Execute Query ObjectClass:{0}",
                    objectClass.objectClassValue)
            try {
                Object searchResult = executeQuery(getScriptedConfiguration().getSearchScriptFileName(),
                        objectClass, query,
                        { res ->
                            boolean doContinue = false
                            if (res instanceof ConnectorObject) {
                                doContinue = handler.handle(res)
                            } else if (res instanceof Closure) {
                                doContinue = handler.handle(ICFObjectBuilder.co(res));
                            }
                            doContinue
                        }, options);
                if (searchResult instanceof SearchResult) {
                    ((SearchResultsHandler) handler).handleResult((SearchResult) searchResult);
                } else if (searchResult instanceof String) {
                    ((SearchResultsHandler) handler).handleResult(new SearchResult((String) searchResult, -1));
                }
                logger.debug("Search ok");
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("SearchScript error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeQuery(String scriptName, ObjectClass objectClass, Filter query, Closure<Boolean> handler, OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(FILTER, query)
        arguments.setVariable(QUERY, {
            Object result = null;
            if (null != query) {
                result = MapFilterVisitor.INSTANCE.accept(null, query);
            }
            return result;
        });
        arguments.setVariable(HANDLER, handler)

        return evaluateScript(scriptName,
                createBinding(arguments, Action.SEARCH, objectClass, null, null, options));
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler,
                     final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSyncScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_SYNC", "Invoke Sync ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), token);
            try {
                Object newToken = executeSync(getScriptedConfiguration().getSyncScriptFileName(),
                        objectClass, token, { delta ->
                    if (delta instanceof SyncDelta) {
                        handler.handle(((SyncDelta) delta))
                    } else if (delta instanceof Closure) {
                        handler.handle(ICFObjectBuilder.delta(delta));
                    }
                }, options);
                logger.debug("Sync ok");
                if (newToken instanceof SyncToken) {
                    ((SyncTokenResultsHandler) handler).handleResult((SyncToken) newToken);
                } else if (null != newToken) {
                    ((SyncTokenResultsHandler) handler).handleResult(new SyncToken(newToken));
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Sync script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeSync(String scriptName, ObjectClass objectClass, SyncToken token, Closure<Boolean> handler, OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(TOKEN, token != null ? token.getValue() : null);
        arguments.setVariable(HANDLER, handler)
        return evaluateScript(scriptName, createBinding(arguments, Action.SYNC, objectClass, null, null, options));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSyncScriptFileName())) {
            SyncToken syncToken = null;
            debugLocale("DEBUG_INVOKE_GET_LATEST_SYNC_TOKEN", "Invoke GetLatestSyncToken ObjectClass:{0}",
                    objectClass.getObjectClassValue());
            try {
                Object result = executeGetLatestSyncToken(getScriptedConfiguration().getSyncScriptFileName(),
                        objectClass);
                logger.debug("GetLatestSyncToken ok");
                if (result instanceof SyncToken) {
                    syncToken = (SyncToken) result;
                } else if (null != result) {
                    syncToken = new SyncToken(result);
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Sync (GetLatestSyncToken) script error", e);
            }
            return syncToken;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeGetLatestSyncToken(String scriptName, ObjectClass objectClass) {
        return evaluateScript(scriptName,
                createBinding(new Binding(), Action.GET_LATEST_SYNC_TOKEN, objectClass, null, null, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void test() {
        getScriptedConfiguration().validate()
        if (StringUtil.isNotBlank(getScriptedConfiguration().getTestScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_TEST", "Invoke Test")
            try {
                executeTest(getScriptedConfiguration().getTestScriptFileName());
                logger.debug("Test ok");
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Test script error", e);
            }
        }
    }

    protected void executeTest(String scriptName) {
        evaluateScript(scriptName,
                createBinding(new Binding(), Action.TEST, null, null, null, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
                      OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_UPDATE", "Invoke Update ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            try {
                Object uidAfter = executeUpdate(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, replaceAttributes, options)
                if (uidAfter instanceof String) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return new Uid(uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "Update script didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (Update) script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeUpdate(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        return genericUpdate(scriptName, Action.UPDATE, objectClass, uid, replaceAttributes, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd,
                                  OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_ADD_ATTRIBUTE_VALUES", "Invoke AddAttributeValues ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            try {
                Object uidAfter = executeAddAttributeValues(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, valuesToAdd, options)
                if (uidAfter instanceof String) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return new Uid(uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "Update script didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (AddAttributeValues) script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeAddAttributeValues(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
        return genericUpdate(scriptName, Action.ADD_ATTRIBUTE_VALUES, objectClass, uid, valuesToAdd, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid,
                                     Set<Attribute> valuesToRemove, OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.debugLocale("DEBUG_INVOKE_REMOVE_ATTRIBUTE_VALUES", "Invoke RemoveAttributeValues ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            try {
                Object uidAfter = executeRemoveAttributeValues(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, valuesToRemove, options)
                if (uidAfter instanceof String) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return new Uid(uidAfter);
                } else if (uidAfter instanceof Uid) {
                    logger.debug("{0}:{1} updated", objectClass.getObjectClassValue(), uidAfter);
                    return (Uid) uidAfter;
                } else {
                    throw new ConnectorException(
                            "Update script didn't return with the uid(__UID__) value");
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (RemoveAttributeValues) script error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeRemoveAttributeValues(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
        return genericUpdate(scriptName, Action.REMOVE_ATTRIBUTE_VALUES, objectClass, uid, valuesToRemove, options);
    }

    protected Object evaluateScript(String scriptName, Binding arguments) {
        return getScriptedConfiguration().evaluate(scriptName, arguments, null)
    }

    protected Binding createBinding(final Binding arguments, Action action, final ObjectClass objectClass, final Uid uid, final Set<Attribute> attributes,
                                    final OperationOptions options) {
        arguments.setVariable(ACTION, action);
        if (null != objectClass) {
            arguments.setVariable(OBJECT_CLASS, objectClass);
        }
        if (null != uid) {
            arguments.setVariable(UID, uid);
        }
//        if (null != attributes) {
//            AttributesAccessor accessor = new AttributesAccessor(attributes);
//            arguments.setVariable(ATTRIBUTES, accessor);
//            Name name = accessor.getName();
//            arguments.setVariable(ID, null != name ? name : null);
//        }
        if (null != options) {
            arguments.setVariable(OPTIONS, options);
        }

        arguments.setVariable(CONFIGURATION, configuration);

        return arguments;
    }

    protected Object getGuardedStringValue(GuardedString password) {
        // Password - if allowed we provide it in clear
        if (getScriptedConfiguration().getClearTextPasswordToScript()) {
            if (password != null) {
                return SecurityUtil.decrypt(password);
            }
        } else {
            return { clearString ->
                if (password != null) {
                    password.access(new GuardedString.Accessor() {
                        @Override
                        public void access(char[] clearChars) {
                            if (clearString instanceof Closure) {
                                clearString(new String(clearChars));
                            } else {
                                clearString = new String(clearChars);
                            }
                        }
                    });
                    clearString
                }
            };
        }
        return null;
    }

    protected Object getGuardedByteArrayValue(GuardedByteArray password) {
        // Password - if allowed we provide it in clear
        if (getScriptedConfiguration().getClearTextPasswordToScript()) {
            if (password != null) {
                return SecurityUtil.decrypt(password);
            }
        } else {
            return { clearBytesOut ->
                if (password != null) {
                    password.access(new GuardedByteArray.Accessor() {
                        @Override
                        void access(byte[] clearBytes) {
                            if (clearBytesOut instanceof Closure) {
                                clearBytesOut(clearBytes);
                            } else {
                                clearBytesOut = clearBytes;
                            }
                        }
                    });
                    clearBytesOut
                }
            };
        }
        return null;
    }

    protected Object genericUpdate(String scriptName, Action method, ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
                                   OperationOptions options) {
        final Binding arguments = new Binding();
        final Map<String, Object> attributesMap = new HashMap<String, Object>()
        // We give the id (name) as an argument, more friendly than dealing
        // with __NAME__
        arguments.setVariable(ID, null);
        for (Attribute attribute : attributes) {
            if (attribute.is(Name.NAME)) {
                arguments.setVariable(ID, AttributeUtil.getSingleValue(attribute))
            } else if ((attribute.is(OperationalAttributes.PASSWORD_NAME) ||
                    attribute.is(OperationalAttributes.CURRENT_PASSWORD_NAME)) && method.equals(Action.UPDATE)) {
                attributesMap.put(attribute.getName(),
                        getGuardedStringValue(AttributeUtil.getGuardedStringValue(attribute)));
            } else if (OperationalAttributes.isOperationalAttribute(attribute) && method.equals(Action.UPDATE)) {
                attributesMap.put(attribute.getName(), attribute.getValue());
            } else {
                attributesMap.put(attribute.getName(), attribute.getValue());
            }
        }
        arguments.setVariable(ATTRIBUTES, attributesMap);
        return evaluateScript(scriptName, createBinding(arguments, method, objectClass, uid, attributes, options));
    }
}
