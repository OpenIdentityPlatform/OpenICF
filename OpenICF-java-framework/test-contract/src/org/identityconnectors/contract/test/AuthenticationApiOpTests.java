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
package org.identityconnectors.contract.test;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.operations.APIOperation;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.api.operations.CreateApiOp;
import org.identityconnectors.framework.api.operations.DeleteApiOp;
import org.identityconnectors.framework.api.operations.GetApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp;
import org.identityconnectors.framework.api.operations.UpdateApiOp.Type;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

/**
 * Contract test of {@link AuthenticationApiOp}
 */
@RunWith(Parameterized.class)
public class AuthenticationApiOpTests extends ObjectClassRunner {

    /**
     * Logging..
     */
    private static final Log LOG = Log.getLog(AuthenticationApiOpTests.class);
    private static final String TEST_NAME = "Authentication";
    private static final String USERNAME_PROP = "username";
    private static final String WRONG_PASSWORD_PROP = "wrong.password";

    public AuthenticationApiOpTests(ObjectClass oclass) {
        super(oclass);
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public Class<? extends APIOperation> getAPIOperation() {
        return AuthenticationApiOp.class;
    }

    /**
     * {@inheritDoc}     
     */
    @Override
    public void testRun() {

        Uid uid = null;
        
        try {
            // create a user
            Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                    getObjectClassInfo(), getTestName(), 0, true, false);

            uid = getConnectorFacade().create(getObjectClass(), attrs, getOperationOptionsByOp(CreateApiOp.class));

            // get the user to make sure it exists now
            ConnectorObject obj = getConnectorFacade().getObject(getObjectClass(), uid,
                    getOperationOptionsByOp(GetApiOp.class));
            assertNotNull("Unable to retrieve newly created object", obj);

            // get username
            String name = (String) getDataProvider().getTestSuiteAttribute(String.class.getName(),
                    getObjectClass().getObjectClassValue() + "." + USERNAME_PROP, TEST_NAME);

            // test negative case with valid user, but wrong password
            boolean authenticateFailed = false;

            // get wrong password
            String wrongPassword = (String) getDataProvider().getTestSuiteAttribute(String.class.getName(),
                    getObjectClass().getObjectClassValue() + "." + WRONG_PASSWORD_PROP, TEST_NAME);

            try {
                getConnectorFacade().authenticate(name,new GuardedString(wrongPassword.toCharArray()),
                        getOperationOptionsByOp(AuthenticationApiOp.class));
            } catch (InvalidCredentialException e) {
                // it failed as it should have
                authenticateFailed = true;
            }

            assertTrue("Negative test case for Authentication failed, should throw InvalidCredentialException",
                    authenticateFailed);

            // now try with the right password
            String password = ConnectorHelper.getString(getDataProvider(),
                    getTestName(), OperationalAttributes.PASSWORD_NAME,
                    getObjectClassInfo().getType(), 0);

            Uid authenticatedUid = getConnectorFacade().authenticate(name,
                    new GuardedString(password.toCharArray()),
                    getOperationOptionsByOp(AuthenticationApiOp.class));

            String MSG = "Authenticate returned wrong Uid, expected: %s, returned: %s.";
            assertEquals(String.format(MSG, uid, authenticatedUid), uid, authenticatedUid);
            
            // now try to set the password to be expired and authenticate again
            // it's possible only in case Update and either PASSWORD_EXPIRED or
            // PASSWORD_EXPIRATION_DATE are supported
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(),UpdateApiOp.class)
                    && (isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRED_NAME) 
                            || isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME))) {
                LOG.info("PasswordExpirationException test follows.");
                
                Uid newUid = null;                                
                Set<Attribute> updateAttrs = new HashSet<Attribute>();
                if (isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRED_NAME)) {                    
                    updateAttrs.add(AttributeBuilder.buildPasswordExpired(true));
                    
                } else {
                    // set PASSWORD_EXPIRATION_DATE to now
                    updateAttrs.add(AttributeBuilder.buildPasswordExpirationDate(new Date()));
                }
                
                // add uid for update
                updateAttrs.add(uid);
                
                newUid = getConnectorFacade().update(Type.REPLACE, getObjectClass(), updateAttrs, null);
                if (!uid.equals(newUid) && newUid != null) {
                    uid = newUid;
                }

                
                // and now authenticate
                authenticateFailed = false;
                try {
                    getConnectorFacade().authenticate(name, new GuardedString(password.toCharArray()),
                            getOperationOptionsByOp(AuthenticationApiOp.class));
                } catch (PasswordExpiredException ex) {
                    // ok
                    authenticateFailed = true;
                    MSG = "PasswordExpiredException contains wrong Uid, expected: %s, returned: %s";
                    assertEquals(String.format(MSG, uid, ex.getUid()), uid, ex.getUid());
                }

                assertTrue("Authenticate should throw PasswordExpiredException.",
                        authenticateFailed);
            }
            else {
                LOG.info("Skipping PasswordExpirationException test.");
            }
        } finally {
            if (uid != null) {
                // delete the object
                ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid,
                        false, getOperationOptionsByOp(DeleteApiOp.class));
            }
            
        }
    }
    
    /**
     * Returns true if operational attribute is supported and updateable.
     */
    private boolean isOperationalAttributeUpdateable(String name) {
        ObjectClassInfo oinfo = getObjectClassInfo();
        for (AttributeInfo ainfo : oinfo.getAttributeInfo()) {
            if (ainfo.is(name)) {
                return ainfo.isUpdateable();
            }
        }
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestName() {
        return TEST_NAME;
    }

}
