/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock Inc. All rights reserved.
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

package org.forgerock.openicf.connectors.basic;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.script.Script;
import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
import org.identityconnectors.framework.common.objects.QualifiedUid;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.AttributeNormalizer;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateAttributeValuesOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

/**
 * Main implementation of the Basic Connector
 * 
 * @author $author$
 * @version $Revision$ $Date$
 */
@ConnectorClass(displayNameKey = "Basic.connector.display",
        configurationClass = BasicConfiguration.class)
public class BasicConnector implements PoolableConnector, AttributeNormalizer, AuthenticateOp,
        CreateOp, DeleteOp, ResolveUsernameOp, SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp,
        SearchOp<String>, SyncOp, TestOp, UpdateAttributeValuesOp, UpdateOp {
    /**
     * Setup logging for the {@link BasicConnector}.
     */
    private static final Log logger = Log.getLog(BasicConnector.class);

    /**
     * Place holder for the Connection created in the init method
     */
    private BasicConnection connection;

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link BasicConnector#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    private BasicConfiguration configuration;

    private Schema schema = null;

    /**
     * Gets the Configuration context for this connector.
     */
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     * 
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    public void init(Configuration configuration1) {
        this.configuration = (BasicConfiguration) configuration1;
        this.connection = new BasicConnection(this.configuration);
    }

    /**
     * Disposes of the {@link BasicConnector}'s resources.
     * 
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
        configuration = null;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void checkAlive() {
        connection.test();
    }

    /**
     * {@inheritDoc}
     */
    public Attribute normalizeAttribute(ObjectClass oclass, Attribute attribute) {
        return attribute;
    }

    /******************
     * SPI Operations
     * 
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     ******************/

    /**
     * {@inheritDoc}
     */
    public Uid authenticate(final ObjectClass objectClass, final String userName,
            final GuardedString password, final OperationOptions options) {
        Uid uid = resolveUsername(objectClass, userName, options);
        if (null == uid) {
            throw new InvalidCredentialException("Unknown userName:" + userName);
        }
        ConnectorObject connectorObject = connection.getStorage(objectClass).get(uid);
        GuardedString currentPassword =
                AttributeUtil.getCurrentPasswordValue(connectorObject.getAttributes());
        if (!currentPassword.equals(password)) {
            throw new InvalidPasswordException();
        }
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid resolveUsername(final ObjectClass objectClass, final String userName,
            final OperationOptions options) {

        for (Map.Entry<Uid, ConnectorObject> entry : connection.getStorage(objectClass).entrySet()) {
            Name name = AttributeUtil.getNameFromAttributes(entry.getValue().getAttributes());
            if (userName.equals(name.getNameValue())) {
                return entry.getKey();
            }
        }
        // TODO What to do if not found?
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
            final OperationOptions options) {

        ConcurrentMap<Uid, ConnectorObject> storage = connection.getStorage(objectClass);

        Name name = AttributeUtil.getNameFromAttributes(createAttributes);
        if (null == name) {
            // TODO SchemaViolationException
            throw new ConnectorException("Missing required __NAME__ attribute");
        }

        Uid uid = resolveUsername(objectClass, name.getNameValue(), options);
        if (null != uid) {
            throw new AlreadyExistsException("Object " + name + " already exists");
        }

        uid = new Uid(name.getName());
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder().setObjectClass(objectClass);
        builder.addAttributes(createAttributes).setUid(uid);
        storage.put(uid, builder.build());
        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        getConnectorObject(objectClass, uid);
        connection.getStorage(objectClass).remove(uid);
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        if (null == schema) {
            synchronized (this) {
                if (null == schema) {

                    SchemaBuilder schemaBuilder = new SchemaBuilder(BasicConnector.class);

                    schemaBuilder.defineOperationOption("_OperationOption-primitive-boolean",
                            boolean.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Boolean", Boolean.class);
                    schemaBuilder.defineOperationOption("_OperationOption-primitive-char",
                            char.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Character",
                            Character.class);
                    schemaBuilder.defineOperationOption("_OperationOption-primitive-double",
                            double.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Double", Double.class);
                    schemaBuilder.defineOperationOption("_OperationOption-File", File.class);
                    schemaBuilder.defineOperationOption("_OperationOption-FileArray", File[].class);
                    schemaBuilder.defineOperationOption("_OperationOption-primitive-float",
                            float.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Float", Float.class);
                    schemaBuilder.defineOperationOption("_OperationOption-GuardedByteArray",
                            GuardedByteArray.class);
                    schemaBuilder.defineOperationOption("_OperationOption-GuardedString",
                            GuardedString.class);
                    schemaBuilder
                            .defineOperationOption("_OperationOption-primitive-int", int.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Integer", Integer.class);
                    schemaBuilder.defineOperationOption("_OperationOption-primitive-long",
                            long.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Long", Long.class);
                    schemaBuilder.defineOperationOption("_OperationOption-ObjectClass",
                            ObjectClass.class);
                    schemaBuilder.defineOperationOption("_OperationOption-QualifiedUid",
                            QualifiedUid.class);
                    schemaBuilder.defineOperationOption("_OperationOption-Script", Script.class);
                    schemaBuilder.defineOperationOption("_OperationOption-String", String.class);
                    schemaBuilder.defineOperationOption("_OperationOption-StringArray",
                            String[].class);
                    schemaBuilder.defineOperationOption("_OperationOption-Uid ", Uid.class);
                    schemaBuilder.defineOperationOption("_OperationOption-URI", URI.class);

                    // __ACCOUNT__
                    ObjectClassInfoBuilder ocBuilder = new ObjectClassInfoBuilder();
                    ocBuilder.setType(ObjectClass.ACCOUNT_NAME);
                    // The name of the object
                    ocBuilder.addAttributeInfo(Name.INFO);

                    // All Predefined Attribute Info
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.GROUPS);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.LAST_LOGIN_DATE);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.LAST_PASSWORD_CHANGE_DATE);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.PASSWORD_CHANGE_INTERVAL);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.SHORT_NAME);

                    // All Operational Attribute Info
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.CURRENT_PASSWORD);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.DISABLE_DATE);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.ENABLE);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.ENABLE_DATE);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.LOCK_OUT);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.PASSWORD);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.PASSWORD_EXPIRATION_DATE);
                    ocBuilder.addAttributeInfo(OperationalAttributeInfos.PASSWORD_EXPIRED);

                    // All possible attribute types and flags
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-BigDecimal",
                            BigDecimal.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-BigInteger",
                            BigInteger.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-boolean", boolean.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Boolean",
                            Boolean.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-byte[]",
                            byte[].class, EnumSet.of(AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-char", char.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Character",
                            Character.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-double", double.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Double",
                            Double.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-float", float.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Float",
                            Float.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-GuardedByteArray", GuardedByteArray.class, EnumSet.of(
                                    AttributeInfo.Flags.REQUIRED,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-GuardedString", GuardedString.class, EnumSet.of(
                                    AttributeInfo.Flags.REQUIRED,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-int", int.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Integer",
                            Integer.class));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                            "_Attribute-primitive-long", long.class, EnumSet.of(
                                    AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT,
                                    AttributeInfo.Flags.NOT_READABLE,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-Long",
                            Long.class, EnumSet.of(AttributeInfo.Flags.NOT_CREATABLE,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("_Attribute-String",
                            String.class, EnumSet.of(AttributeInfo.Flags.MULTIVALUED,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    schemaBuilder.defineObjectClass(ocBuilder.build());

                    // __GROUP__
                    ocBuilder = new ObjectClassInfoBuilder();
                    ocBuilder.setType(ObjectClass.GROUP_NAME);
                    ocBuilder.addAttributeInfo(Name.INFO);
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);
                    ObjectClassInfo objectClassInfo = ocBuilder.build();
                    schemaBuilder.defineObjectClass(objectClassInfo);

                    // Remove operations
                    schemaBuilder.removeSupportedObjectClass(AuthenticateOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(CreateOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(DeleteOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(ResolveUsernameOp.class,
                            objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(SyncOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(UpdateAttributeValuesOp.class,
                            objectClassInfo);

                    // organization
                    ocBuilder = new ObjectClassInfoBuilder();
                    ocBuilder.setType("organization");
                    ocBuilder.setContainer(true);
                    ocBuilder.addAttributeInfo(Name.INFO);
                    ocBuilder.addAttributeInfo(AttributeInfoBuilder.build("members", String.class,
                            EnumSet.of(AttributeInfo.Flags.MULTIVALUED,
                                    AttributeInfo.Flags.NOT_UPDATEABLE)));
                    ocBuilder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);

                    objectClassInfo = ocBuilder.build();
                    schemaBuilder.defineObjectClass(objectClassInfo);

                    // Remove operations
                    schemaBuilder.removeSupportedObjectClass(AuthenticateOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(CreateOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(DeleteOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(ResolveUsernameOp.class,
                            objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(SyncOp.class, objectClassInfo);
                    schemaBuilder.removeSupportedObjectClass(UpdateAttributeValuesOp.class,
                            objectClassInfo);

                    schema = schemaBuilder.build();

                }
            }
        }
        return schema;
    }

    /**
     * {@inheritDoc}
     */
    public Object runScriptOnConnector(ScriptContext request, OperationOptions options) {
        ScriptExecutorFactory factory =
                ScriptExecutorFactory.newInstance(request.getScriptLanguage());
        if (null == factory) {
            throw new ConnectorException("Unsupported script language: "
                    + request.getScriptLanguage());
        }
        ScriptExecutor executor =
                factory.newScriptExecutor(BasicConnector.class.getClassLoader(), request
                        .getScriptLanguage(), true);
        if (null == executor) {
            throw new ConnectorException("Failed to create Executor");
        }
        try {
            return executor.execute(request.getScriptArguments());
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        return runScriptOnConnector(request, options);
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<String> createFilterTranslator(ObjectClass objectClass,
            OperationOptions options) {
        return new BasicFilterTranslator();
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, String query, ResultsHandler handler,
                             OperationOptions options) {
        ConcurrentMap<Uid, ConnectorObject> storage = connection.getStorage(objectClass);
        Set<AttributeInfo> attributeInfos = schema().findObjectClassInfo(objectClass.getObjectClassValue())
                .getAttributeInfo();

        for (ConnectorObject connectorObject : storage.values()) {
            // Let the framework to filter
            ConnectorObjectBuilder builder = new ConnectorObjectBuilder().setObjectClass(objectClass);
            builder.setUid(connectorObject.getUid());
            builder.setName(connectorObject.getName());
            AttributesAccessor accessor = new AttributesAccessor(connectorObject.getAttributes());
            for (AttributeInfo attributeInfo : attributeInfos) {
                if (attributeInfo.is(Name.NAME)) {
                    continue;
                } else if (attributeInfo.is(Uid.NAME)) {
                    continue;
                }
                if (!attributeInfo.isReturnedByDefault()) {
                    continue;
                } else {
                    Attribute attribute = accessor.find(attributeInfo.getName());
                    if (null != attribute) {
                        builder.addAttribute(attribute);
                    }
                }
            }
            handler.handle(connectorObject);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler,
            final OperationOptions options) {

        // throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        return new SyncToken(0);
    }

    /**
     * {@inheritDoc}
     */
    public void test() {
        // No exception, good instance :)
    }

    /**
     * {@inheritDoc}
     */
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
            OperationOptions options) {
        ConnectorObject connectorObject = getConnectorObject(objectClass, uid);

        connectorObject.getUid();

        return uid;
    }

    /**
     * {@inheritDoc}
     */
    public Uid addAttributeValues(ObjectClass objectClass, Uid uid, Set<Attribute> valuesToAdd,
            OperationOptions options) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Uid removeAttributeValues(ObjectClass objectClass, Uid uid,
            Set<Attribute> valuesToRemove, OperationOptions options) {
        throw new UnsupportedOperationException();
    }

    private ConnectorObject getConnectorObject(ObjectClass objectClass, Uid uid) {
        ConcurrentMap<Uid, ConnectorObject> storage = connection.getStorage(objectClass);

        ConnectorObject connectorObject = storage.get(uid);
        if (null == connectorObject) {
            throw new UnknownUidException(uid, objectClass);
        }
        return connectorObject;
    }
}
