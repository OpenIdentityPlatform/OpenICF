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
package org.identityconnectors.framework.spi.operations;

import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.spi.Connector;

/**
 * Implement this interface to allow the Connector to describe which types of objects
 * the Connector manages on the target resource (and which operations
 * and which options the Connector supports for each type of object).
 * @param T The result type of the translator. 
 * @see AbstractFilterTranslator For more information
 */
public interface SchemaOp extends SPIOperation {

    /**
     * Describes the types of objects this {@link Connector} supports. This
     * method is considered an operation since determining supported objects may
     * require configuration information and allows this determination to be
     * dynamic.
     * <p>
     * The special {@link org.identityconnectors.framework.common.objects.Uid} attribute 
     * should never appear in the schema, as it is not a true attribute of an object, 
     * rather a reference to it. If your resource object-class has a writable unique id attribute 
     * that is different than its {@link org.identityconnectors.framework.common.objects.Name}, 
     * then your schema should contain a resource-specific attribute that represents this unique id.
     * For example, a Unix account object might contain <I>unix_uid</I>.
     * 
     * @return basic schema supported by this {@link Connector}.
     */
    Schema schema();
}
