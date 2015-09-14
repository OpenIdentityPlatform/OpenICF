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
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
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
import org.identityconnectors.framework.spi.operations.UpdateOp

/**
 * Main implementation of the Scripted Common code.
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 * @version 1.4.0.0
 */
public class ScriptedConnectorBase<C extends ScriptedConfiguration> implements AuthenticateOp, CreateOp, DeleteOp, ResolveUsernameOp,
        SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<Filter>, SyncOp, TestOp,
        UpdateOp {

    public static final String USERNAME = "username"
    public static final String PASSWORD = "password"

    public static final String OPERATION = "operation"

    public static final String OBJECT_CLASS = "objectClass"
    public static final String UID = "uid"
    public static final String ID = "id"

    public static final String ATTRIBUTES = "attributes"
    public static final String OPTIONS = "options"

    public static final String CONNECTION = "connection"
    public static final String SCHEMA = "schema"
    public static final String CONFIGURATION = "configuration"
    public static final String LOGGER = "log"
    public static final String TOKEN = "token"
    public static final String HANDLER = "handler"
    public static final String QUERY = "query"
    public static final String BUILDER = "builder"
    public static final String FILTER = "filter"
    public static final String GROOVY = "GROOVY"

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
    private static final Log logger = Log.getLog(ScriptedConnectorBase.class);

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
    @Override
    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password,
                            OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getAuthenticateScriptFileName())) {
            logger.ok("Invoke Authenticate ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), username)

            Object uidAfter = null;
            try {
                uidAfter = executeAuthenticate(getScriptedConfiguration().getAuthenticateScriptFileName(),
                        objectClass, username, password, options);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                //throw ConnectorException.wrap(e);
                throw new ConnectorException("Authenticate script error", e);
            }
            return returnUid(OperationType.AUTHENTICATE, objectClass, uidAfter);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeAuthenticate(String scriptName, ObjectClass objectClass, String username, GuardedString password,
                                         OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(USERNAME, username);
        arguments.setVariable(PASSWORD, password);
        return evaluateScript(scriptName, createBinding(arguments, OperationType.AUTHENTICATE, objectClass, null, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
                      final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getCreateScriptFileName())) {
            logger.ok("Invoke Create ObjectClass:{0}",
                    objectClass.getObjectClassValue());
            Object uidAfter = null;
            try {
                uidAfter = executeCreate(getScriptedConfiguration().getCreateScriptFileName(),
                        objectClass, createAttributes, options)
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Create script error", e);
            }
            return returnUid(OperationType.CREATE, objectClass, uidAfter);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Uid executeCreate(String scriptName, final ObjectClass objectClass, final Set<Attribute> createAttributes,
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
        return returnUid(OperationType.CREATE, objectClass,evaluateScript(scriptName, createBinding(arguments,
                OperationType.CREATE, objectClass, null, createAttributes, options), getScriptEvaluator()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getDeleteScriptFileName())) {
            logger.ok("Invoke Delete ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid.uidValue);
            try {
                executeDelete(getScriptedConfiguration().getDeleteScriptFileName(),
                        objectClass, uid, options);
                logger.ok("{0}:{1} deleted", objectClass.getObjectClassValue(), uid);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("DeleteScript error", e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected void executeDelete(String scriptName, ObjectClass objectClass, Uid uid, OperationOptions options) throws Exception {
        evaluateScript(scriptName, createBinding(new Binding(), OperationType.DELETE, objectClass, uid, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getResolveUsernameScriptFileName())) {
            logger.ok("Invoke Resolve Username ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), username);
            Object uidAfter = null;
            try {
                uidAfter = executeResolveUsername(getScriptedConfiguration().getResolveUsernameScriptFileName(),
                        objectClass, username, options);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("ResolveUsernameScript error", e);
            }

            return returnUid(OperationType.RESOLVE_USERNAME, objectClass, uidAfter);

        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeResolveUsername(String scriptName, ObjectClass objectClass, String username, OperationOptions options) {
        final Binding arguments = new Binding();
        arguments.setVariable(USERNAME, username);
        return evaluateScript(scriptName,
                createBinding(arguments, OperationType.RESOLVE_USERNAME, objectClass, null, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema schema() {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSchemaScriptFileName())) {
            if (null == schema) {
                try {
                    logger.ok("Invoke Schema");
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
        return evaluateScript(scriptName, createBinding(arguments, OperationType.SCHEMA, null, null, null, null),
                getScriptEvaluator());
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
                Binding binding = new Binding();
                for (Map.Entry<String, Object> entry : request.scriptArguments) {
                    binding.setVariable(entry.key, entry.value)
                }
                return evaluateScript(null,
                        createBinding(binding, OperationType.RUNSCRIPTONCONNECTOR, null, null, null, options),
                        { String scriptName, Binding arguments ->
                            arguments.setVariable(LOGGER, logger)
                            return InvokerHelper.createScript(getScriptedConfiguration().getGroovyScriptEngine().
                                    getGroovyClassLoader().parseClass(codeSource, false),
                                    arguments).run();
                        });                       
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException(e.getMessage(), e);
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
            logger.ok("Invoke RunScriptOnResource")
            try {
                return executeRunScriptOnResource(getScriptedConfiguration().getScriptOnResourceScriptFileName(),
                        request, options);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException(e.getMessage(), e);
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
                createBinding(arguments, OperationType.RUNSCRIPTONRESOURCE, null, null, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FilterTranslator<Filter> createFilterTranslator(final ObjectClass objectClass,
                                                           final OperationOptions options) {
        return ScriptedFilterTranslator.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeQuery(final ObjectClass objectClass, final Filter query, final ResultsHandler handler,
                             final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSearchScriptFileName())) {
            logger.ok("Invoke Execute Query ObjectClass:{0}",
                    objectClass.objectClassValue)
            try {
                Object searchResult = executeQuery(getScriptedConfiguration().getSearchScriptFileName(),
                        objectClass, query,
                        { res ->
                            if (res instanceof ConnectorObject) {
                                return handler.handle(res)
                            } else if (res instanceof Closure) {
                                return handler.handle(ICFObjectBuilder.co(res));
                            } else {
                                throw new ConnectorException("Can not handle type of " + null != res ? res.class.name : "null")
                            }
                        }, options);
                if (searchResult instanceof SearchResult) {
                    ((SearchResultsHandler) handler).handleResult((SearchResult) searchResult);
                } else if (searchResult instanceof String) {
                    ((SearchResultsHandler) handler).handleResult(new SearchResult((String) searchResult, -1));
                }
                logger.ok("Search ok");
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
                result = query.accept(MapFilterVisitor.INSTANCE, null);
            }
            return result;
        });
        arguments.setVariable(HANDLER, handler)

        return evaluateScript(scriptName,
                createBinding(arguments, OperationType.SEARCH, objectClass, null, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler,
                     final OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSyncScriptFileName())) {
            logger.ok("Invoke Sync ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), token);
            try {
                Object newToken = executeSync(getScriptedConfiguration().getSyncScriptFileName(),
                        objectClass, token, { delta ->
                    if (delta instanceof SyncDelta) {
                        return handler.handle(((SyncDelta) delta))
                    } else if (delta instanceof Closure) {
                        return handler.handle(ICFObjectBuilder.delta(delta));
                    } else {
                        throw new ConnectorException("Can not handle type of " + null != delta ? delta.class.name : "null")
                    }
                }, options);
                logger.ok("Sync ok");
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
        return evaluateScript(scriptName, createBinding(arguments, OperationType.SYNC, objectClass, null, null, options),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getSyncScriptFileName())) {
            SyncToken syncToken = null;
            logger.ok("Invoke GetLatestSyncToken ObjectClass:{0}",
                    objectClass.getObjectClassValue());
            try {
                Object result = executeGetLatestSyncToken(getScriptedConfiguration().getSyncScriptFileName(),
                        objectClass);
                logger.ok("GetLatestSyncToken ok");
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
                createBinding(new Binding(), OperationType.GET_LATEST_SYNC_TOKEN, objectClass, null, null, null),
                getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void test() {
        getScriptedConfiguration().validate()
        if (StringUtil.isNotBlank(getScriptedConfiguration().getTestScriptFileName())) {
            logger.ok("Invoke Test")
            try {
                executeTest(getScriptedConfiguration().getTestScriptFileName());
                logger.ok("Test ok");
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Test script error", e);
            }
        }
    }

    protected void executeTest(String scriptName) {
        evaluateScript(scriptName,
                createBinding(new Binding(), OperationType.TEST, null, null, null, null), getScriptEvaluator());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
                      OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.ok("Invoke Update ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid.uidValue);
            Object uidAfter = null;
            try {
                uidAfter = executeUpdate(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, replaceAttributes, options)
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (Update) script error", e);
            }
            return returnUid(OperationType.UPDATE, objectClass, uidAfter);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeUpdate(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        return genericUpdate(scriptName, OperationType.UPDATE, objectClass, uid, replaceAttributes, options);
    }

    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd,
                                  OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.ok("Invoke AddAttributeValues ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            Object uidAfter = null;
            try {
                uidAfter = executeAddAttributeValues(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, valuesToAdd, options)
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (AddAttributeValues) script error", e);
            }
            return returnUid(OperationType.ADD_ATTRIBUTE_VALUES, objectClass, uidAfter);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeAddAttributeValues(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd, OperationOptions options) {
        return genericUpdate(scriptName, OperationType.ADD_ATTRIBUTE_VALUES, objectClass, uid, valuesToAdd, options);
    }

    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid,
                                     Set<Attribute> valuesToRemove, OperationOptions options) {
        if (StringUtil.isNotBlank(getScriptedConfiguration().getUpdateScriptFileName())) {
            logger.ok("Invoke RemoveAttributeValues ObjectClass:{0}->{1}",
                    objectClass.getObjectClassValue(), uid);
            Object uidAfter = null;
            try {
                uidAfter = executeRemoveAttributeValues(getScriptedConfiguration().getUpdateScriptFileName(),
                        objectClass, uid, valuesToRemove, options)
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new ConnectorException("Update (RemoveAttributeValues) script error", e);
            }
            return returnUid(OperationType.REMOVE_ATTRIBUTE_VALUES, objectClass, uidAfter);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected Object executeRemoveAttributeValues(String scriptName, ObjectClass objectClass, Uid uid, Set<Attribute> valuesToRemove, OperationOptions options) {
        return genericUpdate(scriptName, OperationType.REMOVE_ATTRIBUTE_VALUES, objectClass, uid, valuesToRemove, options);
    }

    protected Closure<Object> getScriptEvaluator() {
        return { String scriptName, Binding arguments ->
            return getScriptedConfiguration().evaluate(scriptName, arguments, null)
        }
    }

    protected Object evaluateScript(String scriptName, Binding arguments, Closure<Object> scriptEvaluator) throws Exception {
        return scriptEvaluator.call(scriptName, arguments)
    }

    protected Binding createBinding(
            final Binding arguments, OperationType action,
            final ObjectClass objectClass, final Uid uid, final Set<Attribute> attributes,
            final OperationOptions options) {
        arguments.setVariable(OPERATION, action);
        if (null != objectClass) {
            arguments.setVariable(OBJECT_CLASS, objectClass);
        }
        if (null != uid) {
            arguments.setVariable(UID, uid);
        }
        if (null != attributes) {
            //Filter out the __NAME__ here. The __UID__ is filtered out by the Framework
            Set<Attribute> attributeSet = new HashSet<Attribute>()
            Attribute name = null;
            for (Attribute a : attributes) {
                if (a.is(Name.NAME)) {
                    name = a;
                } else {
                    attributeSet.add(a)
                }
            }
            arguments.setVariable(ATTRIBUTES, attributeSet);
            arguments.setVariable(ID, null != name ? AttributeUtil.getStringValue(name) : null);
        }
        if (null != options) {
            arguments.setVariable(OPTIONS, options);
        }

        arguments.setVariable(CONFIGURATION, configuration);

        return arguments;
    }

    protected Uid returnUid(OperationType action, ObjectClass objectClass, Object uidAfter) {
        if (uidAfter instanceof String) {
            logger.ok(action.debugTrace(), objectClass.getObjectClassValue(), uidAfter);
            return new Uid((String) uidAfter);
        } else if (uidAfter instanceof Uid) {
            logger.ok(action.debugTrace(), objectClass.getObjectClassValue(), uidAfter.uidValue);
            return (Uid) uidAfter;
        } else if (null != uidAfter) {
            throw new ConnectorException(action.fail2(uidAfter.getClass()));
        } else {
            throw new ConnectorException(action.fail1());
        }
    }

    protected Object genericUpdate(String scriptName, OperationType method, ObjectClass objectClass, Uid uid, Set<Attribute> attributes,
                                   OperationOptions options) {
        final Binding arguments = new Binding();
        final Map<String, Object> attributesMap = new HashMap<String, Object>()
        // We give the id (name) as an argument, more friendly than dealing
        // with __NAME__
        arguments.setVariable(ID, null);
        for (Attribute attribute : attributes) {
            if (attribute.is(Name.NAME)) {
                arguments.setVariable(ID, AttributeUtil.getSingleValue(attribute))
            } else {
                attributesMap.put(attribute.getName(), attribute.getValue());
            }
        }
        arguments.setVariable(ATTRIBUTES, attributesMap);
        return evaluateScript(scriptName, createBinding(arguments, method, objectClass, uid, attributes, options),
                getScriptEvaluator());
    }
}
