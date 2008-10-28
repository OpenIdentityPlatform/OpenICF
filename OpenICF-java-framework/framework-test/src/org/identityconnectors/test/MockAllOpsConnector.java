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
package org.identityconnectors.test;

import java.util.Set;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.operations.AdvancedUpdateOp;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ScriptOnConnectorOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;


public class MockAllOpsConnector extends MockConnector implements CreateOp,
        DeleteOp, UpdateOp, SearchOp<String>, AdvancedUpdateOp, AuthenticateOp,
        TestOp, ScriptOnConnectorOp, ScriptOnResourceOp {

    public Object runScriptOnConnector(ScriptContext request,
            OperationOptions options) {
        assert request != null;
        assert options != null;
        addCall(request, options);
        return null;
    }

    public Object runScriptOnResource(ScriptContext request,
            OperationOptions options) {
        assert request != null;
        assert options != null;
        addCall(request, options);
        return null;
    }

    public Uid create(final ObjectClass oclass, final Set<Attribute> attrs,
            OperationOptions options) {
        assert attrs != null;
        addCall(attrs);
        return null;
    }

    public void delete(final ObjectClass objClass, final Uid uid,
            OperationOptions options) {
        assert uid != null && objClass != null;
        addCall(objClass, uid);
    }

    public Uid update(ObjectClass objclass, Set<Attribute> attrs,
            OperationOptions options) {
        assert objclass != null && attrs != null;
        addCall(objclass, attrs);
        return null;
    }

    public FilterTranslator<String> createFilterTranslator(ObjectClass oclass,
            OperationOptions options) {
        assert oclass != null && options != null;
        addCall(oclass, options);
        // no translation - ok since this is just for tests
        return new AbstractFilterTranslator<String>() {
        };
    }

    public void executeQuery(ObjectClass oclass, String query,
            ResultsHandler handler, OperationOptions options) {
        assert oclass != null && handler != null && options != null;
        addCall(oclass, query, handler, options);
    }

    public void search(ObjectClass oclass, Filter filter,
            ResultsHandler handler, OperationOptions options) {
        assert filter != null;
        addCall(filter);
    }

    public void authenticate(final Set<Attribute> attrs,
            OperationOptions options) {
        assert attrs != null;
        addCall(attrs);
    }

    public Uid update(AdvancedUpdateOp.Type type, ObjectClass objclass,
            Set<Attribute> attrs, OperationOptions options) {
        assert type != null && objclass != null && attrs != null;
        addCall(type, objclass, attrs);
        return null;
    }

    public Uid authenticate(String username, GuardedString password,
            OperationOptions options) {
        assert username != null && password != null;
        addCall(username, password);
        return null;
    }

    public void test() {
        addCall();
    }
}
