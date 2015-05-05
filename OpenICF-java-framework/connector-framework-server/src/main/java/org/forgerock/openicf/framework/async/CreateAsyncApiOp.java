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

package org.forgerock.openicf.framework.async;

import java.util.Set;

import org.forgerock.util.promise.Promise;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * {@inheritDoc}
 *
 * @since 1.5
 */
public interface CreateAsyncApiOp extends CreateApiOp {

    /**
     * Create a target object based on the specified attributes.
     *
     * The Connector framework always requires attribute
     * <code>ObjectClass</code>. The <code>Connector</code> itself may require
     * additional attributes. The API will confirm that the set contains the
     * <code>ObjectClass</code> attribute and that no two attributes in the set
     * have the same {@link Attribute#getName() name}.
     *
     * @param objectClass
     *            the type of object to create. Must not be null.
     * @param createAttributes
     *            includes all the attributes necessary to create the target
     *            object (including the <code>ObjectClass</code> attribute).
     * @param options
     *            additional options that impact the way this operation is run.
     *            May be null.
     * @return the unique id for the object that is created. For instance in
     *         LDAP this would be the 'dn', for a database this would be the
     *         primary key, and for 'ActiveDirectory' this would be the GUID.
     * @throws IllegalArgumentException
     *             if <code>ObjectClass</code> is missing or elements of the set
     *             produce duplicate values of {@link Attribute#getName()}.
     * @throws NullPointerException
     *             if the parameter <code>createAttributes</code> is
     *             <code>null</code>.
     * @throws RuntimeException
     *             if the {@link org.identityconnectors.framework.spi.Connector}
     *             SPI throws a native {@link Exception}.
     */
    public Promise<Uid, RuntimeException> createAsync(final ObjectClass objectClass,
            final Set<Attribute> createAttributes, final OperationOptions options);
}
