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

package org.forgerock.openicf.misc.scriptedcommon

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeInfo
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.ObjectClassInfo
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder
import org.identityconnectors.framework.common.objects.Schema
import org.identityconnectors.framework.common.objects.SchemaBuilder
import org.identityconnectors.framework.common.objects.SyncDelta
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder
import org.identityconnectors.framework.common.objects.SyncDeltaType
import org.identityconnectors.framework.common.objects.SyncToken
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.spi.Connector
import org.identityconnectors.framework.spi.operations.SPIOperation

/**
 * An ICFObjectBuilder supports Groovy DSL Builder for OpenICF.
 *
 * @author Laszlo Hordos
 */
@CompileStatic
class ICFObjectBuilder extends AbstractICFBuilder<Void> {

    private final Class<? extends Connector> connectorClass;

    ICFObjectBuilder(Class<? extends Connector> connectorClass) {
        super(null)
        this.connectorClass = connectorClass
    }

    static private <B> AbstractICFBuilder<B> delegateToTag(Class<? extends AbstractICFBuilder<B>> clazz, Closure body, B builder) {
        AbstractICFBuilder<B> tag = (AbstractICFBuilder<B>) clazz.newInstance(builder)
        def clone = body.rehydrate(tag, this, this)
        clone()
        return tag
    }

    static ConnectorObject co(@DelegatesTo(ConnectorObjectDelegate) Closure attribute) {
        delegateToTag(ConnectorObjectDelegate, attribute, new ConnectorObjectBuilder()).builder.build();
    }

    static SyncDelta delta(@DelegatesTo(SyncDeltaDelegate) Closure attribute) {
        delegateToTag(SyncDeltaDelegate, attribute, new SyncDeltaBuilder()).builder.build();
    }

    Schema schema(@DelegatesTo(SchemaDelegate) Closure attribute) {
        delegateToTag(SchemaDelegate, attribute, new SchemaBuilder(connectorClass)).builder.build();
    }

    private static class ConnectorObjectDelegate extends AbstractICFBuilder<ConnectorObjectBuilder> {

        ConnectorObjectDelegate(ConnectorObjectBuilder builder) {
            super(builder)
        }

        void uid(String uid) {
            ((ConnectorObjectBuilder) builder).setUid(uid);
        }

        void uid(Uid uid) {
            ((ConnectorObjectBuilder) builder).setUid(uid);
        }

        void uid(String uid, String revision) {
            ((ConnectorObjectBuilder) builder).setUid(new Uid(uid, revision));
        }

        void id(String id) {
            ((ConnectorObjectBuilder) builder).setName(id);
        }

        void objectClass(String objectClass) {
            ((ConnectorObjectBuilder) builder).setObjectClass(new ObjectClass(objectClass));
        }

        void objectClass(ObjectClass objectClass) {
            ((ConnectorObjectBuilder) builder).setObjectClass(objectClass);
        }

        void attribute(@DelegatesTo(AttributeDelegate) Closure attribute) {
            delegateToTag(AttributeDelegate, attribute)
        }

        void attribute(String name, Object... args) {
            ((ConnectorObjectBuilder) builder).addAttribute(name, args.toList())
        }

        void attribute(String name) {
            ((ConnectorObjectBuilder) builder).addAttribute(name)
        }

        void attributes(Attribute... attrs) {
            ((ConnectorObjectBuilder) builder).addAttribute(attrs)
        }

        void attributes(Collection<Attribute> attrs) {
            ((ConnectorObjectBuilder) builder).addAttributes(attrs)
        }
    }

    private static class AttributeDelegate extends AbstractICFBuilder<ConnectorObjectBuilder> {
        private String name = null
        private Collection<Object> obj = null

        AttributeDelegate(ConnectorObjectBuilder builder) {
            super(builder)
        }

        void name(String name) {
            this.name = name;
        }

        void value(Object value) {
            if (obj == null) {
                obj = new ArrayList<Object>()
            }
            obj.add(value)
        }

        void values(Object... args) {
            obj = args.toList()
        }

        void values(Collection<Object> args) {
            obj = args
        }

        @Override
        protected void complete() {
            ((ConnectorObjectBuilder) builder).addAttribute(name, obj)
        }
    }

    private static class SchemaDelegate extends AbstractICFBuilder<SchemaBuilder> {
        SchemaDelegate(SchemaBuilder builder) {
            super(builder)
        }

        void objectClass(@DelegatesTo(ObjectClassDelegate) Closure attribute) {
            delegateToTag(ObjectClassDelegate, attribute)
        }

    }


    private static class ObjectClassDelegate extends AbstractICFBuilder<SchemaBuilder> {

        private ObjectClassInfoBuilder infoBuilder = new ObjectClassInfoBuilder()
        private Set<Class<? extends SPIOperation>> unsupported = new HashSet<Class<? extends SPIOperation>>(12)

        ObjectClassDelegate(SchemaBuilder builder) {
            super(builder)
        }

        public void type(String type) {
            infoBuilder.setType(type);
        }

        public void container() {
            infoBuilder.setContainer(true)
        }

        public void attribute(AttributeInfo info) {
            infoBuilder.addAttributeInfo(info)
        }

        public void attribute(String name, Class<?> type) {
            infoBuilder.addAttributeInfo(AttributeInfoBuilder.build(name, type))
        }

        public void attribute(String name, Class<?> type, Set<AttributeInfo.Flags> flags) {
            infoBuilder.addAttributeInfo(AttributeInfoBuilder.build(name, type, flags))
        }

        void attributes(@DelegatesTo(AttributeInfoDelegate) Closure attribute) {
            new AttributeInfoDelegate(infoBuilder).delegateToTag(AttributeInfoDelegate, attribute);
        }

        void disable(Class<? extends SPIOperation>... operation) {
            if (null != operation) {
                for (Class<? extends SPIOperation> clazz in operation) {
                    unsupported.add(clazz)
                }
            }
        }

        @Override
        protected void complete() {
            final ObjectClassInfo info = infoBuilder.build();

            ((SchemaBuilder) builder).defineObjectClass(info);
            for (Class<? extends SPIOperation> clazz in unsupported) {
                ((SchemaBuilder) builder).removeSupportedObjectClass(clazz, info)
            }
        }
    }

    private static class AttributeInfoDelegate extends AbstractICFBuilder<ObjectClassInfoBuilder> {

        AttributeInfoDelegate(ObjectClassInfoBuilder builder) {
            super(builder)
        }

        @Override
        Object invokeMethod(String name, Object args) {
            AttributeInfoBuilder infoBuilder = new AttributeInfoBuilder()
            infoBuilder.setName(name);
            def flags = EnumSet.noneOf(AttributeInfo.Flags.class)
            InvokerHelper.asList(args).each {
                if (it instanceof Class) {
                    infoBuilder.setType(it as Class)
                } else if (it instanceof AttributeInfo.Flags) {
                    flags.add(it as AttributeInfo.Flags)
                }
            }
            infoBuilder.setFlags(flags);
            ((ObjectClassInfoBuilder) builder).addAttributeInfo(infoBuilder.build())
        }
    }

    private static class SyncDeltaDelegate extends AbstractICFBuilder<SyncDeltaBuilder> {

        SyncDeltaDelegate(SyncDeltaBuilder builder) {
            super(builder)
        }

        void syncToken(SyncToken token) {
            ((SyncDeltaBuilder) builder).setToken(token);
        }

        void syncToken(Object token) {
            ((SyncDeltaBuilder) builder).setToken(new SyncToken(token));
        }

        void CREATE_OR_UPDATE() {
            ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.CREATE_OR_UPDATE)
        }

        void DELETE() {
            ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.DELETE)
        }

        void CREATE() {
            ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.CREATE)
        }

        void UPDATE() {
            ((SyncDeltaBuilder) builder).setDeltaType(SyncDeltaType.UPDATE)
        }

        void previousUid(String uid) {
            ((SyncDeltaBuilder) builder).setPreviousUid(new Uid(uid));
        }

        void previousUid(Uid uid) {
            ((SyncDeltaBuilder) builder).setPreviousUid(uid);
        }

        void object(@DelegatesTo(ConnectorObjectDelegate) Closure closure) {
            ((SyncDeltaBuilder) builder).setObject(co(closure));
        }
    }
}
