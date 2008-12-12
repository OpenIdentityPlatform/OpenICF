/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
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
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
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
            String name = (String) getDataProvider().getTestSuiteAttribute(getObjectClass().getObjectClassValue() + "." + USERNAME_PROP,
                    TEST_NAME);

            // test negative case with valid user, but wrong password
            boolean authenticateFailed = false;

            // get wrong password
            String wrongPassword = (String) getDataProvider().getTestSuiteAttribute(getObjectClass().getObjectClassValue() + "." + WRONG_PASSWORD_PROP,
                    TEST_NAME);

            try {
                getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name,new GuardedString(wrongPassword.toCharArray()),
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

            Uid authenticatedUid = getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name,
                    new GuardedString(password.toCharArray()),
                    getOperationOptionsByOp(AuthenticationApiOp.class));

            String MSG = "Authenticate returned wrong Uid, expected: %s, returned: %s.";
            assertEquals(String.format(MSG, uid, authenticatedUid), uid, authenticatedUid);
            
            // test that PASSWORD change works, CURRENT_PASSWORD should be set
            // to old password value if supported
            if (isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_NAME)) {
                String newpassword = ConnectorHelper.getString(getDataProvider(), getTestName(),
                        OperationalAttributes.PASSWORD_NAME, UpdateApiOpTests.MODIFIED,
                        getObjectClassInfo().getType(), 0);
                Set<Attribute> replaceAttrs = new HashSet<Attribute>();
                replaceAttrs.add(AttributeBuilder.buildPassword(newpassword.toCharArray()));
                replaceAttrs.add(uid);

                if (ConnectorHelper.isAttrSupported(getObjectClassInfo(),
                        OperationalAttributes.CURRENT_PASSWORD_NAME)) {
                    // CURRENT_PASSWORD must be set to old password
                    replaceAttrs.add(AttributeBuilder.buildCurrentPassword(password.toCharArray()));
                }
                // update to new password
                uid = getConnectorFacade().update(getObjectClass(),
                        uid,
                        AttributeUtil.filterUid(replaceAttrs), 
                        getOperationOptionsByOp(UpdateApiOp.class));

                // authenticate with new password
                authenticatedUid = getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name,
                        new GuardedString(newpassword.toCharArray()),
                        getOperationOptionsByOp(AuthenticationApiOp.class));

                assertEquals(String.format(MSG, uid, authenticatedUid), uid, authenticatedUid);

                // LAST_PASSWORD_CHANGE_DATE
                if (ConnectorHelper.isAttrSupported(getObjectClassInfo(),
                        PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME)) {
                    LOG.info("LAST_PASSWORD_CHANGE_DATE test.");
                    // LAST_PASSWORD_CHANGE_DATE must be readable, we suppose it is
                    // add LAST_PASSWORD_CHANGE_DATE to ATTRS_TO_GET
                    OperationOptionsBuilder builder = new OperationOptionsBuilder();
                    builder.setAttributesToGet(PredefinedAttributes.LAST_LOGIN_DATE_NAME);

                    ConnectorObject lastPasswordChange = getConnectorFacade().getObject(
                            getObjectClass(), uid, builder.build());

                    // check that LAST_PASSWORD_CHANGE_DATE was set to a value
                    assertNotNull("LAST_PASSWORD_CHANGE_DATE attribute is null.",
                            lastPasswordChange.getAttributeByName(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME));
                } else {
                    LOG.info("Skipping LAST_PASSWORD_CHANGE_DATE test.");
                }
            }
            
            // LAST_LOGIN_DATE
            if (ConnectorHelper.isAttrSupported(getObjectClassInfo(), PredefinedAttributes.LAST_LOGIN_DATE_NAME)) {
                LOG.info("LAST_LOGIN_DATE test.");
                // LAST_LOGIN_DATE must be readable, we suppose it is
                // add LAST_LOGIN_DATE to ATTRS_TO_GET
                OperationOptionsBuilder builder = new OperationOptionsBuilder();
                builder.setAttributesToGet(PredefinedAttributes.LAST_LOGIN_DATE_NAME);
                
                ConnectorObject lastLogin = getConnectorFacade().getObject(getObjectClass(), uid,
                        builder.build());
                
                // check that LAST_LOGIN_DATE was set to some value
                assertNotNull("LAST_LOGIN_DATE attribute is null.", lastLogin.getAttributeByName(PredefinedAttributes.LAST_LOGIN_DATE_NAME));
            }
            else {
                LOG.info("Skipping LAST_LOGIN_DATE test.");
            }
            
            // now try to set the password to be expired and authenticate again
            // it's possible only in case Update and PASSWORD_EXPIRED
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(),UpdateApiOp.class)
                    && isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRED_NAME)) {
                LOG.info("PasswordExpirationException with PASSWORD_EXPIRED test follows.");
                
                Uid newUid = null;                                
                Set<Attribute> updateAttrs = new HashSet<Attribute>();
                updateAttrs.add(AttributeBuilder.buildPasswordExpired(true));                                   
                
                // add uid for update
                updateAttrs.add(uid);
                
                newUid = getConnectorFacade().update(getObjectClass(), 
                        uid, 
                        AttributeUtil.filterUid(updateAttrs), 
                        null);
                if (!uid.equals(newUid) && newUid != null) {
                    uid = newUid;
                }

                
                // and now authenticate
                authenticateFailed = false;
                try {
                    getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name, new GuardedString(password.toCharArray()),
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
                LOG.info("Skipping PasswordExpirationException with PASSWORD_EXPIRED test.");
            }
            
            // now try to set the password to be expired and authenticate again
            // it's possible only in case Update and PASSWORD_EXPIRATION_DATE
            if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(),UpdateApiOp.class)
                    && isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME)) {
                LOG.info("PasswordExpirationException with PASSWORD_EXPIRATION_DATE test follows.");
                
                Uid newUid = null;                                
                Set<Attribute> updateAttrs = new HashSet<Attribute>();
                // set PASSWORD_EXPIRATION_DATE to now
                updateAttrs.add(AttributeBuilder.buildPasswordExpirationDate(new Date()));
                
                // add uid for update
                updateAttrs.add(uid);
                
                newUid = getConnectorFacade().update(getObjectClass(), 
                        uid,
                        AttributeUtil.filterUid(updateAttrs), null);
                if (!uid.equals(newUid) && newUid != null) {
                    uid = newUid;
                }

                
                // and now authenticate
                authenticateFailed = false;
                try {
                    getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name, new GuardedString(password.toCharArray()),
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
                LOG.info("Skipping PasswordExpirationException with PASSWORD_EXPIRATION_DATE test.");
            }
            
            // ENABLE
            if (ConnectorHelper.isCRU(getObjectClassInfo(), OperationalAttributes.ENABLE_NAME)) {
                LOG.info("Authenticate of DISABLED account test.");
                // disable account
                Set<Attribute> replaceAttrs = new HashSet<Attribute>();
                replaceAttrs.add(AttributeBuilder.buildEnabled(false));
                replaceAttrs.add(uid);
                               
                uid = getConnectorFacade().update(getObjectClass(),
                        uid,
                        AttributeUtil.filterUid(replaceAttrs), null);

                boolean thrown = false;
                // try to authenticate
                try {
                    getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name,
                        new GuardedString(password.toCharArray()), null);
                }
                catch (RuntimeException ex) {
                    thrown = true;
                }
                assertTrue("Authenticate must throw for disabled account", thrown);
            } else {
                LOG.info("Skipping authenticate of DISABLED account test.");
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
     * Tests that connector respects order of PASSWORD and PASSWORD_EXPIRED attributes during update.
     * PASSWORD should be performed before PASSWORD_EXPIRED.
     */
    @Test
    public void testPasswordBeforePasswordExpired() {
        // run test only in case operation is supported and both PASSWORD and PASSWORD_EXPIRED are supported
        if (ConnectorHelper.operationSupported(getConnectorFacade(), getObjectClass(), getAPIOperation())
                && isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_NAME)
                && isOperationalAttributeUpdateable(OperationalAttributes.PASSWORD_EXPIRED_NAME)) {
            Uid uid = null;
            try {
                // create an user
                Set<Attribute> attrs = ConnectorHelper.getCreateableAttributes(getDataProvider(),
                        getObjectClassInfo(), getTestName(), 0, true, false);
                uid = getConnectorFacade().create(getObjectClass(), attrs,
                        getOperationOptionsByOp(CreateApiOp.class));
                
                // get username
                String name = (String) getDataProvider().getTestSuiteAttribute(getObjectClass().getObjectClassValue() + "." + USERNAME_PROP,
                        TEST_NAME);
                                                
                // get new password
                String newpassword = ConnectorHelper.getString(getDataProvider(), getTestName(),
                        OperationalAttributes.PASSWORD_NAME, UpdateApiOpTests.MODIFIED,
                        getObjectClassInfo().getType(), 0);
                
                // change password and expire password
                Set<Attribute> replaceAttrs = new HashSet<Attribute>();
                replaceAttrs.add(AttributeBuilder.buildPassword(newpassword.toCharArray()));
                replaceAttrs.add(AttributeBuilder.buildPasswordExpired(true));
                replaceAttrs.add(uid);

                if (ConnectorHelper.isAttrSupported(getObjectClassInfo(), OperationalAttributes.CURRENT_PASSWORD_NAME)) {
                    // get old password
                    String password = ConnectorHelper.getString(getDataProvider(),
                            getTestName(), OperationalAttributes.PASSWORD_NAME,
                            getObjectClassInfo().getType(), 0);
                    
                    // CURRENT_PASSWORD must be set to old password
                    replaceAttrs.add(AttributeBuilder.buildCurrentPassword(password.toCharArray()));
                }
                // update to new password and expire password
                uid = getConnectorFacade().update(getObjectClass(),
                        uid,
                        AttributeUtil.filterUid(replaceAttrs), getOperationOptionsByOp(UpdateApiOp.class));

                boolean thrown = false;
                try {
                    // authenticate with new password                
                    getConnectorFacade().authenticate(ObjectClass.ACCOUNT, name,
                        new GuardedString(newpassword.toCharArray()),
                        getOperationOptionsByOp(AuthenticationApiOp.class));
                }
                catch (PasswordExpiredException ex) {
                    // expected
                    thrown = true;
                }
                
                assertTrue("Authenticate should throw PasswordExpiredException.", thrown);
                
            } finally {
                // delete the object
                ConnectorHelper.deleteObject(getConnectorFacade(), getSupportedObjectClass(), uid,
                        false, getOperationOptionsByOp(DeleteApiOp.class));
            }
        }
        else {
            LOG.info("Skipping test ''testPasswordBeforePasswordExpired'' for object class {0}", getObjectClass());
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
