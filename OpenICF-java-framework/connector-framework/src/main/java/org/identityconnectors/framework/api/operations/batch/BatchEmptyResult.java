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
package org.identityconnectors.framework.api.operations.batch;

/**
 * Object to return in a batch result list when there is no data as a result of the operation, e.g. DeleteApiOp. This
 * helps to ensure the list of results matches 1:1 the list of operations and there is a consistent object type
 * used whenever there would otherwise be no result at all.
 */
public class BatchEmptyResult {
    private final String message;

    public BatchEmptyResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
