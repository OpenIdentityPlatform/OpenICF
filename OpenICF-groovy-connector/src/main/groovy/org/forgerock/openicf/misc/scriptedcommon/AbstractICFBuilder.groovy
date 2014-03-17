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
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget

/**
 * An AbstractICFBuilder is a super class of Groovy Builder of ICF Objects.
 *
 * @author Laszlo Hordos
 */
@CompileStatic
@PackageScope([PackageScopeTarget.CLASS])
abstract class AbstractICFBuilder<B> {

    protected final B builder

    protected AbstractICFBuilder(B builder) {
        this.builder = builder
    }

    protected void complete() {}

    protected void delegateToTag(Class<? extends AbstractICFBuilder> clazz, Closure body) {
        AbstractICFBuilder tag = (AbstractICFBuilder) clazz.newInstance(builder)
        def clone = body.rehydrate(tag, this, this)
        clone()
        tag.complete()
    }
}
