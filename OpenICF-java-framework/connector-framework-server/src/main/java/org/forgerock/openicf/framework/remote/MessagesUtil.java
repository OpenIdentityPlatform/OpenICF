/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.forgerock.openicf.framework.remote;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.ConnectorObjects;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.Encryptor;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.serializer.SerializerUtil;
import org.identityconnectors.framework.impl.api.remote.RemoteWrappedException;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

/**
 * The MessagesUtil contains a collection of methods to convert between OpenICF
 * object and Protocol Buffer Message classes. This is a partial replacement of
 * {@link org.identityconnectors.framework.common.serializer.SerializerUtil}
 */
public class MessagesUtil {

    /** Prevent instantiation. */
    private MessagesUtil() {
        // No implementation required.
    }

    public static RPCMessages.RemoteMessage.Builder createErrorResponse(long messageId,
            Throwable error) {
        RPCMessages.RPCResponse.Builder builder =
                RPCMessages.RPCResponse.newBuilder().setError(fromException(error, 4));
        return createRemoteMessage(messageId).setResponse(builder);
    }

    public static RPCMessages.ExceptionMessage.Builder fromException(Throwable error, int depth) {
        RPCMessages.ExceptionMessage.Builder builder =
                RPCMessages.ExceptionMessage.newBuilder().setExceptionClass(
                        error.getClass().getName()).setStackTrace(
                        RemoteWrappedException.getStackTrace(error));
        if (null != error.getMessage()) {
            builder.setMessage(error.getMessage());
        }

        RPCMessages.ExceptionMessage.InnerCause.Builder cause = fromCause(error.getCause(), depth);
        if (null != cause) {
            builder.setInnerCause(cause);
        }

        return builder;
    }

    private static RPCMessages.ExceptionMessage.InnerCause.Builder fromCause(Throwable error, int depth) {
        if (null != error && depth > 0) {
            RPCMessages.ExceptionMessage.InnerCause.Builder builder =
                    RPCMessages.ExceptionMessage.InnerCause.newBuilder().setExceptionClass(
                            error.getClass().getName());
            if (null != error.getMessage()) {
                builder.setMessage(error.getMessage());
            }
            RPCMessages.ExceptionMessage.InnerCause.Builder cause = fromCause(error.getCause(), --depth);
            if (null != cause) {
                builder.setCause(cause);
            }
            return builder;
        }
        return null;
    }

    public static RuntimeException fromExceptionMessage(
            RPCMessages.ExceptionMessage exceptionMessage) {

        String message = null;
        try {
            String throwableClass =
                    exceptionMessage.hasExceptionClass() ? exceptionMessage.getExceptionClass()
                            : ConnectorException.class.getName();
            message = exceptionMessage.hasMessage() ? exceptionMessage.getMessage() : "";
            String stackTrace =
                    exceptionMessage.hasStackTrace() ? exceptionMessage.getStackTrace() : null;

            return new RemoteWrappedException(throwableClass, message, getCause(exceptionMessage
                    .getInnerCause()), stackTrace);

        } catch (Throwable t) {
            return new ConnectorException(StringUtil.isNotBlank(message) ? message
                    : "Failed to process ExceptionMessage response", t);
        }
    }

    private static RemoteWrappedException getCause(RPCMessages.ExceptionMessage.InnerCause cause) {
        if (null != cause) {
            String throwableClass =
                    cause.hasExceptionClass() ? cause.getExceptionClass()
                            : ConnectorException.class.getName();
            String message = cause.hasMessage() ? cause.getMessage() : "";
            RemoteWrappedException originalCause =
                    cause.hasCause() ? getCause(cause.getCause()) : null;
            return new RemoteWrappedException(throwableClass, message, originalCause, null);
        }
        return null;
    }

    public static CommonObjectMessages.Uid.Builder fromUid(Uid uid) {
        CommonObjectMessages.Uid.Builder builder =
                CommonObjectMessages.Uid.newBuilder().setValue(uid.getUidValue());
        if (null != uid.getRevision()) {
            builder.setRevision(uid.getUidValue());
        }
        return builder;
    }

    public static RPCMessages.RemoteMessage.Builder createResponse(long messageId,
            RPCMessages.RPCResponse.Builder builderForValue) {
        return createRemoteMessage(messageId).setResponse(builderForValue);
    }

    public static RPCMessages.RemoteMessage.Builder createRequest(int messageId,
            RPCMessages.RPCRequest.Builder builderForValue) {
        return createRemoteMessage(messageId).setRequest(builderForValue);
    }

    public static RPCMessages.RemoteMessage.Builder createRemoteMessage(long messageId) {
        RPCMessages.RemoteMessage.Builder builder = RPCMessages.RemoteMessage.newBuilder();
        if (0 != messageId) {
            builder.setMessageId(messageId);
        }
        return builder;
    }

    public static RPCMessages.HandshakeMessage.Builder createHandshakeMessage(PublicKey publicKey) {
        RPCMessages.HandshakeMessage.Builder messageBuilder =
                RPCMessages.HandshakeMessage.newBuilder();
        messageBuilder.setPublicKey(ByteString.copyFrom(publicKey.getEncoded()));
        messageBuilder.setServerType(RPCMessages.HandshakeMessage.ServerType.JAVA);
        // Make the fingerprint unique sessionId
        messageBuilder.setSessionId(org.identityconnectors.common.security.SecurityUtil
                .computeHexSHA1Hash(publicKey.getEncoded(), false));
        return messageBuilder;
    }

    /*
    This methods meant to help to replace {@link org.identityconnectors.framework.common.serializer.SerializerUtil}.
    Not used yet.
     */
    public static ConnectorObjects.AttributeMessage.Builder fromAttribute(
            final Attribute attribute, final Encryptor encryptor) {
        final ConnectorObjects.AttributeMessage.Builder builder =
                ConnectorObjects.AttributeMessage.newBuilder().setName(attribute.getName());
        for (Object source : attribute.getValue()) {
            Class sourceClass = (source == null ? null : source.getClass());
            if (sourceClass == String.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setStringValue((String) source));
            } else if (sourceClass == Long.class || sourceClass == long.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setLongValue((Long) source));
            } else if (sourceClass == Character.class || sourceClass == char.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setCharacterValue(source.toString()));
            } else if (sourceClass == Double.class || sourceClass == double.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setDoubleValue((Double) source));
            } else if (sourceClass == Float.class || sourceClass == float.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setFloatValue((Float) source));
            } else if (sourceClass == Integer.class || sourceClass == int.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setIntegerValue((Integer) source));
            } else if (sourceClass == Boolean.class || sourceClass == boolean.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setBooleanValue((Boolean) source));
            } else if (sourceClass == Byte.class || sourceClass == byte.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setByteValue(ByteString.copyFrom(new byte[] { (Byte) source })));
            } else if (sourceClass == byte[].class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setByteArrayValue(ByteString.copyFrom((byte[]) source)));
            } else if (sourceClass == java.math.BigDecimal.class) {
                BigDecimal sourceValue = (BigDecimal) source;
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setBigDecimalValue(
                                CommonObjectMessages.BigDecimal.newBuilder().setScale(
                                        sourceValue.scale()).setUnscaled(
                                        sourceValue.unscaledValue().toString())));
            } else if (sourceClass == BigInteger.class) {
                builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                        .setBigIntegerValue(source.toString()));
            } else if (sourceClass == GuardedByteArray.class) {
                GuardedByteArray sourceValue = (GuardedByteArray) source;

                sourceValue.access(new GuardedByteArray.Accessor() {
                    public void access(byte[] clearBytes) {
                        builder.addValue(ConnectorObjects.AttributeUnionValue.newBuilder()
                                .setGuardedByteArrayValue(
                                        ByteString.copyFrom(encryptor.encrypt(clearBytes))));
                    }
                });

            } else if (sourceClass == GuardedString.class) {
                GuardedString sourceValue = (GuardedString) source;

                sourceValue.access(new GuardedString.Accessor() {
                    public void access(char[] clearBytes) {
                        builder.addValue(ConnectorObjects.AttributeUnionValue
                                .newBuilder()
                                .setGuardedByteArrayValue(
                                        ByteString
                                                .copyFrom(encryptor
                                                        .encrypt(org.identityconnectors.common.security.SecurityUtil
                                                                .charsToBytes(clearBytes)))));
                    }
                });

            } else if (null != sourceClass && Map.class.isAssignableFrom(sourceClass)) {
                throw new UnsupportedOperationException("Map serialisation is not yet supported");
            } else if (source == null) {
                builder.addValueBuilder();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        return builder;
    }

    public static ByteBuffer writeToByteBuffer(MessageLite source) throws java.io.IOException {
        ByteBuffer buffer = ByteBuffer.allocate(source.getSerializedSize());
        CodedOutputStream outputStream = CodedOutputStream.newInstance(buffer);
        source.writeTo(outputStream);
        outputStream.flush();
        return buffer;
    }

    @SuppressWarnings("unchecked")
    public static <T, M extends MessageLite> T deserializeMessage(M source, Class<T> target) {
        if (source != null) {
            Pair<Class<T>, ? extends Class<? extends MessageLite>> key =
                    Pair.of(target, source.getClass());
            ObjectHandler<T, M, ?> handler = (ObjectHandler<T, M, ?>) HANDLERS.get(key);
            if (null != handler) {
                return handler.deserialize(source);
            } else {
                throw new UnsupportedOperationException("Not supported pair:" + key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T, M extends MessageLite> M serializeMessage(T source, Class<M> target) {
        if (source != null) {
            Pair<? extends Class<?>, Class<M>> key = Pair.of(source.getClass(), target);
            ObjectHandler<T, M, ?> handler = (ObjectHandler<T, M, ?>) HANDLERS.get(key);
            if (null != handler) {
                return handler.serialize(source);
            } else {
                throw new UnsupportedOperationException("Not supported pair:" + key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T, M extends MessageLite, B extends MessageLite.Builder> B serializeBuilder(
            T source, Class<M> target) {
        if (source != null) {
            Pair<? extends Class<?>, Class<M>> key = Pair.of(source.getClass(), target);
            ObjectHandler<T, M, B> handler = (ObjectHandler<T, M, B>) HANDLERS.get(key);
            if (null != handler) {
                return handler.serializeBuilder(source);
            } else {
                throw new UnsupportedOperationException("Not supported pair:" + key);
            }
        }
        return null;
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> T deserializeLegacy(ByteString byteString) {
        if (byteString.isEmpty()) {
            return null;
        } else {
            return (T) SerializerUtil.deserializeBinaryObject(byteString.toByteArray());
        }
    }

    @Deprecated
    public static ByteString serializeLegacy(Object source) {
        if (null != source) {
            return ByteString.copyFrom(SerializerUtil.serializeBinaryObject(source));
        } else {
            return ByteString.EMPTY;
        }
    }

    private final static Map<Pair<Class<?>, Class<? extends MessageLite>>, ObjectHandler<?, ? extends MessageLite, ? extends MessageLite.Builder>> HANDLERS =
            new HashMap<Pair<Class<?>, Class<? extends MessageLite>>, ObjectHandler<?, ? extends MessageLite, ? extends MessageLite.Builder>>();

    static {
        addHandler(new UidHandler());
        addHandler(new ConnectorKeyHandler());
        addHandler(new ScriptContextHandler());
        addHandler(new SearchResultHandler());
        addHandler(new ConnectorObjectHandler());
        addHandler(new LocaleHandler());
        addHandler(new SyncTokenHandler());
        addHandler(new SyncDeltaHandler());
    }

    @SuppressWarnings("unchecked")
    private static <T, P extends MessageLite, B extends MessageLite.Builder> void addHandler(
            ObjectHandler<T, P, B> handler) {
        Type[] types =
                ((ParameterizedType) handler.getClass().getGenericInterfaces()[0])
                        .getActualTypeArguments();
        Class<?> domainClass = (Class<?>) types[0];
        Class<? extends MessageLite> messageClass = (Class<? extends MessageLite>) types[1];

        HANDLERS.put(Pair.<Class<?>, Class<? extends MessageLite>> of(domainClass, messageClass),
                handler);
    }

    private interface ObjectHandler<T, P extends MessageLite, B extends MessageLite.Builder> {

        T deserialize(P message);

        P serialize(T source);

        B serializeBuilder(T source);
    }

    private final static class UidHandler implements
            ObjectHandler<Uid, CommonObjectMessages.Uid, CommonObjectMessages.Uid.Builder> {

        public Uid deserialize(CommonObjectMessages.Uid message) {
            if (message.hasRevision()) {
                return new Uid(message.getValue(), message.getRevision());
            } else {
                return new Uid(message.getValue());
            }
        }

        public CommonObjectMessages.Uid serialize(Uid source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.Uid.Builder serializeBuilder(Uid source) {
            CommonObjectMessages.Uid.Builder builder =
                    CommonObjectMessages.Uid.newBuilder().setValue(source.getUidValue());
            if (null != source.getRevision()) {
                builder.setRevision(source.getRevision());
            }
            return builder;
        }
    }

    private final static class ConnectorKeyHandler
            implements
            ObjectHandler<ConnectorKey, CommonObjectMessages.ConnectorKey, CommonObjectMessages.ConnectorKey.Builder> {

        public ConnectorKey deserialize(CommonObjectMessages.ConnectorKey message) {
            return new ConnectorKey(message.getBundleName(), message.getBundleVersion(), message
                    .getConnectorName());
        }

        public CommonObjectMessages.ConnectorKey serialize(ConnectorKey source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.ConnectorKey.Builder serializeBuilder(ConnectorKey source) {
            return CommonObjectMessages.ConnectorKey.newBuilder().setBundleName(
                    source.getBundleName()).setBundleVersion(source.getBundleVersion())
                    .setConnectorName(source.getConnectorName());
        }
    }

    private final static class ScriptContextHandler
            implements
            ObjectHandler<ScriptContext, CommonObjectMessages.ScriptContext, CommonObjectMessages.ScriptContext.Builder> {
        public ScriptContext deserialize(CommonObjectMessages.ScriptContext message) {
            Map<String, Object> arguments = deserializeLegacy(message.getScriptArguments());
            return new ScriptContext(message.getScript().getScriptLanguage(), message.getScript()
                    .getScriptText(), null != arguments ? arguments : new HashMap<String, Object>());
        }

        public CommonObjectMessages.ScriptContext serialize(ScriptContext source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.ScriptContext.Builder serializeBuilder(ScriptContext source) {
            return CommonObjectMessages.ScriptContext.newBuilder().setScript(
                    CommonObjectMessages.Script.newBuilder().setScriptLanguage(
                            source.getScriptLanguage()).setScriptText(source.getScriptText()))
                    .setScriptArguments(serializeLegacy(source.getScriptArguments()));
        }
    }

    private final static class SearchResultHandler
            implements
            ObjectHandler<SearchResult, CommonObjectMessages.SearchResult, CommonObjectMessages.SearchResult.Builder> {

        public SearchResult deserialize(CommonObjectMessages.SearchResult message) {
            return new SearchResult(message.hasPagedResultsCookie() ? message
                    .getPagedResultsCookie() : null, message.getRemainingPagedResults());
        }

        public CommonObjectMessages.SearchResult serialize(SearchResult source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.SearchResult.Builder serializeBuilder(SearchResult source) {
            CommonObjectMessages.SearchResult.Builder builder =
                    CommonObjectMessages.SearchResult.newBuilder().setRemainingPagedResults(
                            source.getRemainingPagedResults());
            if (null != source.getPagedResultsCookie()) {
                builder.setPagedResultsCookie(source.getPagedResultsCookie());
            }
            return builder;
        }
    }

    private final static class ConnectorObjectHandler
            implements
            ObjectHandler<ConnectorObject, CommonObjectMessages.ConnectorObject, CommonObjectMessages.ConnectorObject.Builder> {

        public ConnectorObject deserialize(CommonObjectMessages.ConnectorObject message) {
            Set<? extends Attribute> attributes = deserializeLegacy(message.getAttriutes());
            return new ConnectorObject(new ObjectClass(message.getObjectClass()), attributes);
        }

        public CommonObjectMessages.ConnectorObject serialize(ConnectorObject source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.ConnectorObject.Builder serializeBuilder(ConnectorObject source) {
            return CommonObjectMessages.ConnectorObject.newBuilder().setObjectClass(
                    source.getObjectClass().getObjectClassValue()).setAttriutes(
                    serializeLegacy(source.getAttributes()));
        }
    }

    private final static class LocaleHandler implements
            ObjectHandler<Locale, CommonObjectMessages.Locale, CommonObjectMessages.Locale.Builder> {

        public Locale deserialize(CommonObjectMessages.Locale message) {
            return new Locale(message.getLanguage(), message.getCountry(), message.getVariant());
        }

        public CommonObjectMessages.Locale serialize(Locale source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.Locale.Builder serializeBuilder(Locale source) {
            return CommonObjectMessages.Locale.newBuilder().setCountry(source.getCountry())
                    .setLanguage(source.getLanguage()).setVariant(source.getVariant());
        }
    }

    private final static class SyncDeltaHandler
            implements
            ObjectHandler<SyncDelta, CommonObjectMessages.SyncDelta, CommonObjectMessages.SyncDelta.Builder> {

        public SyncDelta deserialize(CommonObjectMessages.SyncDelta message) {
            SyncDeltaBuilder builder = new SyncDeltaBuilder();
            builder.setToken(deserializeMessage(message.getToken(), SyncToken.class));
            switch (message.getDeltaType()) {
            case CREATE:
                builder.setDeltaType(SyncDeltaType.CREATE);
                break;
            case CREATE_OR_UPDATE:
                builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                break;
            case UPDATE:
                builder.setDeltaType(SyncDeltaType.UPDATE);
                break;
            case DELETE:
                builder.setDeltaType(SyncDeltaType.DELETE);
                break;
            }
            if (message.hasPreviousUid()) {
                builder.setPreviousUid(deserializeMessage(message.getPreviousUid(), Uid.class));
            }
            if (message.hasObjectClass()) {
                builder.setObjectClass(new ObjectClass(message.getObjectClass()));
            }
            if (message.hasUid()) {
                builder.setUid(deserializeMessage(message.getUid(), Uid.class));
            }
            if (message.hasConnectorObject()) {
                Set<Attribute> object = deserializeLegacy(message.getConnectorObject());
                builder.setObject(new ConnectorObject(builder.getObjectClass(), object));
            }
            return builder.build();
        }

        public CommonObjectMessages.SyncDelta serialize(SyncDelta source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.SyncDelta.Builder serializeBuilder(SyncDelta source) {
            CommonObjectMessages.SyncDelta.Builder builder =
                    CommonObjectMessages.SyncDelta.newBuilder();
            builder.setToken(serializeMessage(source.getToken(),
                    CommonObjectMessages.SyncToken.class));
            switch (source.getDeltaType()) {
            case CREATE:
                builder.setDeltaType(CommonObjectMessages.SyncDelta.SyncDeltaType.CREATE);
                break;
            case CREATE_OR_UPDATE:
                builder.setDeltaType(CommonObjectMessages.SyncDelta.SyncDeltaType.CREATE_OR_UPDATE);
                break;
            case UPDATE:
                builder.setDeltaType(CommonObjectMessages.SyncDelta.SyncDeltaType.UPDATE);
                break;
            case DELETE:
                builder.setDeltaType(CommonObjectMessages.SyncDelta.SyncDeltaType.DELETE);
                break;
            }
            if (null != source.getUid()) {
                builder.setUid(serializeMessage(source.getUid(), CommonObjectMessages.Uid.class));
            }
            if (null != source.getObjectClass()) {
                builder.setObjectClass(source.getObjectClass().getObjectClassValue());
            }
            if (null != source.getObject()) {
                builder.setConnectorObject(serializeLegacy(source.getObject().getAttributes()));
            }
            if (null != source.getPreviousUid()) {
                builder.setPreviousUid(serializeMessage(source.getPreviousUid(),
                        CommonObjectMessages.Uid.class));
            }

            return builder;
        }
    }

    private final static class SyncTokenHandler
            implements
            ObjectHandler<SyncToken, CommonObjectMessages.SyncToken, CommonObjectMessages.SyncToken.Builder> {

        public SyncToken deserialize(CommonObjectMessages.SyncToken message) {
            return new SyncToken(deserializeLegacy(message.getValue()));
        }

        public CommonObjectMessages.SyncToken serialize(SyncToken source) {
            return serializeBuilder(source).build();
        }

        public CommonObjectMessages.SyncToken.Builder serializeBuilder(SyncToken source) {
            return CommonObjectMessages.SyncToken.newBuilder().setValue(
                    serializeLegacy(source.getValue()));
        }
    }

}
