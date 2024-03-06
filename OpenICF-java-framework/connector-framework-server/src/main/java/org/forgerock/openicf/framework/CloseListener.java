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

package org.forgerock.openicf.framework;

import java.io.Closeable;

/**
 * A CloseListener is used to receive Close event of source object.
 * 
 * @param <T>
 *            the type of source which is Closed
 * @author Laszlo Hordos
 */
public interface CloseListener<T extends Closeable> {

    /**
     * Callback method to be called by source object if its state changed to
     * Closed.
     * 
     * @param source
     *            the object which is closed.
     */
    void onClosed(T source);
}
