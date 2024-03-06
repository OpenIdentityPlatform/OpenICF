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


import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.security.GuardedByteArray
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConfigurationException
import org.identityconnectors.framework.common.exceptions.ConnectionBrokenException
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.exceptions.ConnectorIOException
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException
import org.identityconnectors.framework.common.exceptions.OperationTimeoutException
import org.identityconnectors.framework.common.exceptions.PreconditionFailedException
import org.identityconnectors.framework.common.exceptions.PreconditionRequiredException
import org.identityconnectors.framework.common.exceptions.RetryableException
import org.identityconnectors.framework.common.exceptions.UnknownUidException
import org.identityconnectors.framework.common.objects.NameUtil
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Uid

import java.nio.charset.Charset

class TestHelper {

    static final ObjectClass TEST = new ObjectClass(NameUtil.createSpecialName('TEST'))
    static final ObjectClass SAMPLE = new ObjectClass(NameUtil.createSpecialName('SAMPLE'))


    static Uid exceptionTest(OperationType operation, ObjectClass objectClass, Uid uid, OperationOptions options) {
        if (null != options.getRunAsUser()){
            if (null == options.getRunWithPassword()) {
                throw new IllegalArgumentException("Missing Run As Password")
            } else if ("valid-session".equals(options.getRunAsUser() && options.getRunWithPassword() == null)){
                // Use valid session ID
            } else if (!"admin".equals(options.getRunAsUser()) || !new GuardedString("Passw0rd".toCharArray()).equals(options.getRunWithPassword())){
                throw new ConnectorSecurityException("Invalid Run As Credentials");
            }
        }
        if ("TEST1".equals(uid.uidValue)) {
            if (OperationType.CREATE.equals(operation)) {
                throw new AlreadyExistsException(
                        "Object with Uid '${uid.uidValue}' and ObjectClass '${objectClass.objectClassValue}' already exists!").initUid(uid);
            } else {
                throw new UnknownUidException(uid, objectClass);
            }
        } else if ("TEST2".equals(uid.uidValue)) {
            //ICF 1.4 Exception
            if (OperationType.DELETE.equals(operation)) {
                return uid;
            } else {
                throw new InvalidAttributeValueException();
            }
        } else if ("TEST3".equals(uid.uidValue)) {
            //ICF 1.1 Exception
            if (OperationType.DELETE.equals(operation)) {
                return uid;
            } else {
                throw new IllegalArgumentException();
            }
        } else if ("TEST4".equals(uid.uidValue)) {
            if (OperationType.CREATE.equals(operation)) {
                throw RetryableException.wrap("Created but some attributes are not set, call update with new 'uid'!", uid);
            } else {
                throw new PreconditionFailedException();
            }
        } else if ("TEST5".equals(uid.uidValue)) {
            if (OperationType.CREATE.equals(operation)) {
                return uid;
            } else {
                throw new PreconditionRequiredException();
            }
        } else if ("TIMEOUT".equals(uid.uidValue)) {
            Thread.sleep(30000)
        } else if ("TESTEX_CE".equals(uid.uidValue)) {
            throw new ConfigurationException(new MissingResourceException("Test Failed", operation.name(), "Example"));
        } else if ("TESTEX_CB".equals(uid.uidValue)) {
            throw new ConnectionBrokenException("Example Message");
        } else if ("TESTEX_CF".equals(uid.uidValue)) {
            throw new ConnectionFailedException("Example Message");
        } else if ("TESTEX_C".equals(uid.uidValue)) {
            throw new ConnectorException("Example Message");
        } else if ("TESTEX_CIO".equals(uid.uidValue)) {
            throw new ConnectorIOException("Example Message");
        } else if ("TESTEX_OT".equals(uid.uidValue)) {
            throw new OperationTimeoutException("Example Message");
        } else if ("TESTEX_NPE".equals(uid.uidValue)) {
            throw new NullPointerException("Example Message");
        }
        return uid;
    }

    static Map<String, Object> getConnectorObjectTemplate() {

        return [
                attributeString                    : "retipipiter",
                attributeStringMultivalue          : ["value1", "value2"] as String[],

                attributelongp                     : 11l,
                attributelongpMultivalue           : [12l, 13l] as long[],

                attributeLong                      : 14 as Long,
                attributeLongMultivalue            : [15 as Long, 16 as Long] as Long[],

                attributechar                      : 'a' as char,
                attributecharMultivalue            : ['b' as char, 'c' as char] as char[],

                attributeCharacter                 : 'd' as Character,
                attributeCharacterMultivalue       : ['e' as Character, 'f' as Character] as Character[],

                attributedoublep                   : Double.MIN_NORMAL,
                attributedoublepMultivalue         : [Double.MIN_VALUE, Double.MAX_VALUE] as double[],

                attributeDouble                    : 17 as Double,
                attributeDoubleMultivalue          : [18 as Double, 19 as Double] as Double[],

                attributefloatp                    : 20F,
                attributefloatpMultivalue          : [21F, 22F] as float[],

                attributeFloat                     : 23 as Float,
                attributeFloatMultivalue           : [24 as Float, 25 as Float] as Float[],

                attributeint                       : 26 as int,
                attributeintMultivalue             : [27 as int, 28 as int] as int[],

                attributeInteger                   : 29 as Integer,
                attributeIntegerMultivalue         : [30 as Integer, 31 as Integer] as Integer[],

                attributebooleanp                  : true as boolean,
                attributebooleanpMultivalue        : [true as boolean, false as boolean] as boolean[],

                attributeBoolean                   : false as Boolean,
                attributeBooleanMultivalue         : [true as Boolean, false as Boolean] as Boolean[],

                // Sample script for IDME-113
                attributebytep                     : 48 as byte,
                attributebytepMultivalue           : [49 as byte, 50 as byte] as byte[],

                // Sample script for IDME-113
                attributeByte                      : 51 as Byte,
                attributeByteMultivalue            : [52 as Byte, 53 as Byte] as Byte[],

                //This must be wrapped into array because 'attributeByteMultivalue' and 'attributeByteArray' collide
                attributeByteArray                 : ["array".getBytes(Charset.forName("UTF-8"))] as byte[][] ,
                attributeByteArrayMultivalue       : ["item1".getBytes(Charset.forName("UTF-8")),
                                                      "item2".getBytes(Charset.forName("UTF-8"))] as byte[][],

                attributeBigDecimal                : BigDecimal.ONE,
                attributeBigDecimalMultivalue      : [BigDecimal.ZERO, BigDecimal.TEN] as BigDecimal[],

                attributeBigInteger                : BigInteger.ONE,
                attributeBigIntegerMultivalue      : [BigInteger.ZERO, BigInteger.TEN] as BigInteger[],

                attributeGuardedByteArray          : new GuardedByteArray("array".getBytes(Charset.forName("UTF-8"))),
                attributeGuardedByteArrayMultivalue: [new GuardedByteArray("item1".getBytes(Charset.forName("UTF-8"))),
                                                      new GuardedByteArray("item2".getBytes(Charset.forName("UTF-8")))]
                        as GuardedByteArray[],

                attributeGuardedString             : new GuardedString("secret".toCharArray()),
                attributeGuardedStringMultivalue   : [new GuardedString("secret1".toCharArray()),
                                                      new GuardedString("secret2".toCharArray())] as GuardedString[],

                // Sample script for IDME-113
                attributeMap                       : [
                        string     : 'String',
                        number     : 42,
                        trueOrFalse: true,
                        nullValue  : null,
                        collection : ['item1', 'item2'],
                        object     : [
                                key1: 'value1',
                                key2: 'value2'
                        ]
                ],
                attributeMapMultivalue             : [
                        [
                                string     : 'String',
                                number     : 42,
                                trueOrFalse: true,
                                nullValue  : null,
                                collection : ['item1', 'item2'],
                                object     : [
                                        key1: 'value1',
                                        key2: 'value2'
                                ]
                        ],
                        [
                                string     : 'String',
                                number     : 43,
                                trueOrFalse: true,
                                nullValue  : null,
                                collection : ['item1', 'item2'],
                                object     : [
                                        key1: 'value1',
                                        key2: 'value2'
                                ]
                        ]
                ] as Map[]
        ]
    }
}
