/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */

package com.forgerock.openicf.xml.xsdparser;

import com.forgerock.openicf.xml.util.SchemaParserUtil;
import com.sun.xml.xsom.XSAnnotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.*;

import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.FrameworkUtil;

public class SchemaParser {

    /**
     * Setup logging for the {@link SchemaParser}.
     */
    private static final Log log = Log.getLog(SchemaParser.class);
    
    private Class< ? extends Connector> connectorClass;
    private String filePath;
    private XSSchemaSet schemaSet;
    
    public SchemaParser(Class< ? extends Connector> connectorClass, String filePath){

        Assertions.nullCheck(connectorClass, "connectorClass");
        Assertions.blankCheck(filePath, "filePath");
        
        this.connectorClass = connectorClass;
        this.filePath = filePath;
        
        this.schemaSet = SchemaParserUtil.parseXSDSchema(filePath);
    }

    //Takes the xsd-schema and parses it to icf-schema
    public Schema parseSchema() {
        final String method = "parseSchema";
        log.info("Entry {0}", method);

        SchemaBuilder schemaBuilder = new SchemaBuilder(connectorClass);

        XSSchema schema = schemaSet.getSchema(1);

        Map<String, XSElementDecl> types = schema.getElementDecls();
        Set<String> typesKeys = types.keySet();
        Iterator<String> typesIterator = typesKeys.iterator();

        while (typesIterator.hasNext()) {
            XSElementDecl type = schema.getElementDecl(typesIterator.next());

            Set<AttributeInfo> attributes = new HashSet<AttributeInfo>();
            List<Class<? extends SPIOperation>> supportedOp = new LinkedList<Class<? extends SPIOperation>>();

            ObjectClassInfoBuilder objectClassBuilder = new ObjectClassInfoBuilder();
            objectClassBuilder.setType(type.getName());

            if (type != null) {
                XSComplexType xsCompType = type.getType().asComplexType();

                if (xsCompType.getAnnotation() != null) {
                    String supportedOpString = xsCompType.getAnnotation().getAnnotation().toString();
                    String[] supportedOpStringSplit = supportedOpString.split(" |\n");
                    List<String> supportedOpListString = Arrays.asList(supportedOpStringSplit);

                    supportedOp = SchemaParserUtil.getSupportedOpClasses(supportedOpListString);
                }

                XSContentType xsContType = xsCompType.getContentType();
                XSParticle particle = xsContType.asParticle();

                if (particle != null) {
                    XSTerm xsTerm = particle.getTerm();

                    if (xsTerm.isModelGroup()) {
                        XSModelGroup xsModelGroup = xsTerm.asModelGroup();
                        XSParticle[] particles = xsModelGroup.getChildren();

                        for (XSParticle childParticle : particles) {
                            XSTerm childParticleTerm = childParticle.getTerm();

                            if (childParticleTerm.isElementDecl()) {
                                XSElementDecl childElementTerm = childParticleTerm.asElementDecl();
                                Set<Flags> flags = new HashSet<Flags>();
                                Class<?> attributeClassType = null;

                                if (childParticle.getMinOccurs() == 1) {
                                    flags.add(Flags.REQUIRED);
                                }

                                if (childParticle.getMaxOccurs() > 1 || childParticle.getMaxOccurs() == -1) {
                                    flags.add(Flags.MULTIVALUED);
                                }

                                if (childElementTerm.getAnnotation() != null) {
                                    XSAnnotation childElementTermAnnotaion = childElementTerm.getAnnotation();
                                    String annotations = childElementTermAnnotaion.getAnnotation().toString();

                                    String[] annotationsSplit = annotations.split(" |\n");
                                    List<String> annotationList = Arrays.asList(annotationsSplit);

                                    Set<Flags> flagList = SchemaParserUtil.getFlags(annotationList);

                                    if (flagList != null) {
                                        flags.addAll(flagList);
                                    }
                                    
                                    attributeClassType = SchemaParserUtil.getJavaClassType(annotationList);

                                }

                                if (attributeClassType == null) {
                                    XSType typeNotFlagedJavaclass = childElementTerm.getType();
                                    
                                    if(typeNotFlagedJavaclass.getName() != null){
                                        attributeClassType = SchemaParserUtil.findJavaClassType(typeNotFlagedJavaclass.getName());
                                    }
                                }

                                AttributeInfo attributeInfo = null;

                                if (attributeClassType != null) {
                                    attributeInfo = AttributeInfoBuilder.build(childElementTerm.getName(), attributeClassType, flags);
                                }
                                else {
                                    attributeInfo = AttributeInfoBuilder.build(childElementTerm.getName());
                                }

                                if(attributeInfo != null){
                                    attributes.add(attributeInfo);
                                }
                            }
                        }
                    }
                }
            }
            objectClassBuilder.addAllAttributeInfo(attributes);
            ObjectClassInfo objectClassInfo = objectClassBuilder.build();

            schemaBuilder.defineObjectClass(objectClassInfo);

            if (supportedOp.size() >= 1) {
                for (Class<? extends SPIOperation> removeOp : FrameworkUtil.allSPIOperations()) {
                    if (!supportedOp.contains(removeOp)) {
                        try {
                            schemaBuilder.removeSupportedObjectClass(removeOp, objectClassInfo);
                        }
                        catch (IllegalArgumentException ex) {
                            log.info("SupportedObjectClass {0} not supported", removeOp.toString());
                        }
                    }
                }
            }
        }
        
        Schema returnSchema = schemaBuilder.build();

        log.info("Exit {0}", method);
        
        return returnSchema;
    }

    public XSSchemaSet getXsdSchema(){
        if(schemaSet != null){
            return schemaSet;
        }
        else {
            this.schemaSet = SchemaParserUtil.parseXSDSchema(filePath);
            return schemaSet;
        }
    }  
}