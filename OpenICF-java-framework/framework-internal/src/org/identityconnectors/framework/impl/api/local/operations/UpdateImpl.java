/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.impl.api.local.operations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.AdvancedUpdateOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;


/**
 * Handles both version of update this include simple replace and the advance
 * update.
 */
public class UpdateImpl extends ConnectorAPIOperationRunner implements
        org.identityconnectors.framework.api.operations.UpdateApiOp {
    /**
     * Static map between API/SPI update types.
     */
    private static final Map<Type, AdvancedUpdateOp.Type> CONV_TYPE = new HashMap<Type, AdvancedUpdateOp.Type>();
    static {
        CONV_TYPE.put(Type.ADD, AdvancedUpdateOp.Type.ADD);
        CONV_TYPE.put(Type.DELETE, AdvancedUpdateOp.Type.DELETE);
        CONV_TYPE.put(Type.REPLACE, AdvancedUpdateOp.Type.REPLACE);
    }

    /**
     * Determines which type of update a connector supports and then uses that
     * handler.
     */
    public UpdateImpl(final ConnectorOperationalContext context,
            final Connector connector) {
        super(context, connector);
    }

    /**
     * All the operational attributes that can not be added or deleted.
     */
    static Set<String> OPERATIONAL_ATTRIBUTE_NAMES = new HashSet<String>();
    static {
        OPERATIONAL_ATTRIBUTE_NAMES.addAll(OperationalAttributes
                .getOperationalAttributeNames());
        OPERATIONAL_ATTRIBUTE_NAMES.add(Name.NAME);
    };

    /**
     * Create a new instance of the handler for the type of update the connector
     * can support and run it.
     */
    public Uid update(final Type type, final ObjectClass objclass,
            final Set<Attribute> attributes,
            OperationOptions options) {
        // validate all the parameters..
        validateInput(type, objclass, attributes);
        //cast null as empty
        if ( options == null ) {
            options = new OperationOptionsBuilder().build();
        }

        Uid ret = null;
        Connector c = getConnector();
        final ObjectNormalizerFacade normalizer =
            getNormalizer(objclass);
        final Set<Attribute> normalizedAttributes =
            normalizer.normalizeAttributes(attributes);
        if (c instanceof AdvancedUpdateOp) {
            // easy way its an advance update
            ret = ((AdvancedUpdateOp) c).update(CONV_TYPE.get(type), objclass, normalizedAttributes,options);
        } else if (c instanceof UpdateOp) {
            // check that this connector supports Search..
            if (!(c instanceof SearchOp)) {
                final String MSG = "Connector must support: " + SearchOp.class;
                throw new UnsupportedOperationException(MSG);
            }
            // get the connector object from the resource...
            Uid uid = AttributeUtil.getUidAttribute(normalizedAttributes);
            ConnectorObject o = getConnectorObject(objclass, uid, options);
            if (o == null) {
                throw new UnknownUidException(uid, objclass);
            }
            // merge the update data..
            Set<Attribute> mergeAttrs = merge(type, normalizedAttributes, o.getAttributes());
            // update the object..
            ret = ((UpdateOp) c).update(objclass, mergeAttrs, options);
        }
        return (Uid)normalizer.normalizeAttribute(ret);
    }

    /**
     * Merges two connector objects into a single updated object.
     */
    public Set<Attribute> merge(Type type, Set<Attribute> updateAttrs,
            Set<Attribute> baseAttrs) {
        // return the merged attributes
        Set<Attribute> ret = new HashSet<Attribute>();
        // create map that can be modified to get the subset of changes 
        Map<String, Attribute> baseAttrMap = AttributeUtil.toMap(baseAttrs);
        // run through attributes of the current object..
        for (final Attribute updateAttr : updateAttrs) {
            // ignore uid because its immutable..
            if (updateAttr instanceof Uid) {
                continue;
            }
            // get the name of the update attributes
            String name = updateAttr.getName();
            // remove each attribute that is an update attribute..
            Attribute baseAttr = baseAttrMap.get(name);
            List<Object> values;
            final Attribute modifiedAttr; 
            if (Type.ADD.equals(type)) {
                if (baseAttr == null) {
                    modifiedAttr = updateAttr;
                } else {
                    // create a new list with the base attribute to add to..
                    values = CollectionUtil.newList(baseAttr.getValue());
                    values.addAll(updateAttr.getValue());
                    modifiedAttr = AttributeBuilder.build(name, values);
                }
            } else if (Type.DELETE.equals(type)) {
                if (baseAttr == null) {
                    // nothing to actually do the attribute do not exist
                    continue;                    
                } else {
                    // create a list with the base attribute to remove from..
                    values = CollectionUtil.newList(baseAttr.getValue());
                    for (Object val : updateAttr.getValue()) {
                        values.remove(val);
                    }
                    // if the values are empty send a null to the connector..
                    if (values.isEmpty()) {
                        modifiedAttr = AttributeBuilder.build(name);
                    } else {
                        modifiedAttr = AttributeBuilder.build(name, values);
                    }
                }
            } else if (Type.REPLACE.equals(type)){
                modifiedAttr = updateAttr;
            } else {
                throw new IllegalStateException("Unknown Type: " + type);
            }
            ret.add(modifiedAttr);
        }
        // add the rest of the base attribute that were not update attrs
        Map<String, Attribute> updateAttrMap = AttributeUtil.toMap(updateAttrs);
        for (Attribute a : baseAttrs) {
            if (!updateAttrMap.containsKey(a.getName())) {
                ret.add(a);
            }
        }
        // always add the UID..
        ret.add(updateAttrMap.get(Uid.NAME));
        return ret;
    }

    /**
     * Get the {@link ConnectorObject} to modify.
     */
    ConnectorObject getConnectorObject(ObjectClass oclass, Uid uid, OperationOptions options) {
        // attempt to get the connector object..
        GetApiOp get = new GetImpl(new SearchImpl(getOperationalContext(),
                getConnector()));
        return get.getObject(oclass, uid, options);
    }

    /**
     * Makes things easier if you can trust the input.
     */
    public static void validateInput(final Type type, final ObjectClass objclass,
            final Set<Attribute> attrs) {
        final String OPERATIONAL_ATTRIBUTE_ERR = 
            "Operational attribute '%s' can not be added or deleted only replaced.";
        Assertions.nullCheck(type, "type");
        Assertions.nullCheck(objclass, "objclass");
        Assertions.nullCheck(attrs, "attrs");
        // check to make sure there's a uid..
        if (AttributeUtil.getUidAttribute(attrs) == null) {
            throw new IllegalArgumentException(
                    "Parameter 'attrs' must contain a 'Uid'!");
        }
        // check for things only valid during ADD/DELETE
        if (Type.ADD.equals(type) || Type.DELETE.equals(type)) {
            for (Attribute attr : attrs) {
                Assertions.nullCheck(attr, "attr");
                // make sure that none of the values are null..
                if (attr.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Can not ADD or DELETE 'null' value.");
                }
                // make sure that if this an delete/add that it doesn't include
                // certain attributes because it doesn't make any sense..
                String name = attr.getName();
                if (OPERATIONAL_ATTRIBUTE_NAMES.contains(name)) {
                    String msg = String.format(OPERATIONAL_ATTRIBUTE_ERR, name);
                    throw new IllegalArgumentException(msg);
                }
            }
        }
    }
}
