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
package org.identityconnectors.ldap;

import static org.identityconnectors.ldap.LdapUtil.getStringAttrValues;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.GuardedString.Accessor;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.ldap.schema.LdapSchemaMapping;

public class LdapConnection {

    // TODO: SASL authentication, "dn:entryDN" user name.

    // The LDAP attributes with a byte array syntax.
    private static final Set<String> LDAP_BINARY_SYNTAX_ATTRS;
    // The LDAP attributes which require the binary option for transfer.
    private static final Set<String> LDAP_BINARY_OPTION_ATTRS;

    static {
        // Cf. http://java.sun.com/products/jndi/tutorial/ldap/misc/attrs.html.
        LDAP_BINARY_SYNTAX_ATTRS = CollectionUtil.newCaseInsensitiveSet();
        LDAP_BINARY_SYNTAX_ATTRS.add("audio");
        LDAP_BINARY_SYNTAX_ATTRS.add("jpegPhoto");
        LDAP_BINARY_SYNTAX_ATTRS.add("photo");
        LDAP_BINARY_SYNTAX_ATTRS.add("personalSignature");
        LDAP_BINARY_SYNTAX_ATTRS.add("userPassword");
        LDAP_BINARY_SYNTAX_ATTRS.add("userCertificate");
        LDAP_BINARY_SYNTAX_ATTRS.add("caCertificate");
        LDAP_BINARY_SYNTAX_ATTRS.add("authorityRevocationList");
        LDAP_BINARY_SYNTAX_ATTRS.add("deltaRevocationList");
        LDAP_BINARY_SYNTAX_ATTRS.add("certificateRevocationList");
        LDAP_BINARY_SYNTAX_ATTRS.add("crossCertificatePair");
        LDAP_BINARY_SYNTAX_ATTRS.add("x500UniqueIdentifier");
        LDAP_BINARY_SYNTAX_ATTRS.add("supportedAlgorithms");
        // Java serialized objects.
        LDAP_BINARY_SYNTAX_ATTRS.add("javaSerializedData");
        // These seem to only be present in Active Directory.
        LDAP_BINARY_SYNTAX_ATTRS.add("thumbnailPhoto");
        LDAP_BINARY_SYNTAX_ATTRS.add("thumbnailLogo");

        // Cf. RFC 4522 and RFC 4523.
        LDAP_BINARY_OPTION_ATTRS = CollectionUtil.newCaseInsensitiveSet();
        LDAP_BINARY_OPTION_ATTRS.add("userCertificate");
        LDAP_BINARY_OPTION_ATTRS.add("caCertificate");
        LDAP_BINARY_OPTION_ATTRS.add("authorityRevocationList");
        LDAP_BINARY_OPTION_ATTRS.add("deltaRevocationList");
        LDAP_BINARY_OPTION_ATTRS.add("certificateRevocationList");
        LDAP_BINARY_OPTION_ATTRS.add("crossCertificatePair");
        LDAP_BINARY_OPTION_ATTRS.add("supportedAlgorithms");
    }

    private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final Log log = Log.getLog(LdapConnection.class);

    private final LdapConfiguration config;
    private final LdapSchemaMapping schemaMapping;

    private LdapContext initCtx;
    private Set<String> supportedControls;

    public LdapConnection(LdapConfiguration config) {
        this.config = config;
        schemaMapping = new LdapSchemaMapping(this);
    }

    public LdapConfiguration getConfiguration() {
        return config;
    }

    public LdapContext getInitialContext() {
        if (initCtx != null) {
            return initCtx;
        }
        initCtx = createContext(config.getBindDN(), config.getBindPassword());
        return initCtx;
    }

    private LdapContext createContext(String bindDN, GuardedString password) {
        final LdapContext[] result = { null };

        final Hashtable<Object, Object> env = new Hashtable<Object, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(Context.PROVIDER_URL, config.getLdapUrl());
        env.put(Context.REFERRAL, "follow");
        env.put(Context.SECURITY_AUTHENTICATION, config.getAuthentication());
        if (!config.isAuthenticationNone()) {
            env.put(Context.SECURITY_PRINCIPAL, bindDN);
            password.access(new Accessor() {
                public void access(char[] clearChars) {
                    env.put(Context.SECURITY_CREDENTIALS, clearChars);
                    // Connect while in the accessor, otherwise clearChars will be cleared.
                    result[0] = doCreateContext(env);
                }
            });
        } else {
            result[0] = doCreateContext(env);
        }

        return result[0];
    }

    private LdapContext doCreateContext(Hashtable<?, ?> env) {
        try {
            return new InitialLdapContext(env, null);
        } catch (AuthenticationException e) {
            if (e.getMessage().toLowerCase().contains("invalid credentials")) {
                throw new InvalidCredentialException(e);
            } else {
                throw new ConnectorSecurityException(e);
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    public void close() {
        try {
            quietClose(initCtx);
        } finally {
            initCtx = null;
        }
    }

    private static void quietClose(LdapContext ctx) {
        try {
            if (ctx != null) {
                ctx.close();
            }
        } catch (NamingException e) {
            log.warn(e, null);
        }
    }

    public LdapSchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public LdapNativeSchema createNativeSchema() {
        try {
            if (config.isReadSchema()) {
                return new ServerNativeSchema(this);
            } else {
                return new StaticNativeSchema();
            }
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    public void authenticate(String username, GuardedString password) {
        LdapContext ctx = null;
        try {
            ctx = createContext(username, password);
        } finally {
            quietClose(ctx);
        }
    }

    public void checkAlive() {
        if (initCtx == null) {
            // Fine, not yet initialized.
            return;
        }
        try {
            Attributes attrs = initCtx.getAttributes("", new String[] { "subschemaSubentry" } );
            attrs.get("subschemaSubentry");
        } catch (NamingException e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * Returns {@code} true if the control with the given OID is supported by
     * the server.
     */
    public boolean supportsControl(String oid) {
        return getSupportedControls().contains(oid);
    }

    private Set<String> getSupportedControls() {
        if (supportedControls == null) {
            try {
                Attributes attrs = getInitialContext().getAttributes("", new String[] { "supportedControl" });
                supportedControls = Collections.unmodifiableSet(getStringAttrValues(attrs, "supportedControl"));
            } catch (NamingException e) {
                log.warn(e, "Exception while retrieving the supported controls");
                supportedControls = Collections.emptySet();
            }
        }
        return supportedControls;
    }

    /**
     * Returns the base DNs passed through the {@link LdapConnector#OP_BASE_DNS}
     * option, or an empty array if the option is not present.
     */
    public String[] getOptionsBaseDNs(OperationOptions options) {
        String[] result = (String[]) options.getOptions().get(LdapConnector.OP_BASE_DNS);
        if (result != null) {
            if (result.length == 0) {
                throw new ConnectorException("The value of the OP_BASE_DNS option should not be an empty array");
            }
            String illegalBaseDN = checkBaseDNs(result);
            if (illegalBaseDN != null) {
                throw new ConnectorException("Base DN " + illegalBaseDN
                        + " passed in OP_BASE_DNS is not under one of the configured base DNs "
                        + Arrays.asList(config.getBaseDNs()));
            }
            return result;
        }
        return new String[0];
    }

    /**
     * Checks that the wanted base DNs are included in the permitted base DNs.
     * Returns the first wanted base DN for which this assertion is false or
     * {@code null}.
     */
    private String checkBaseDNs(String[] entries) {
        for (String entry : entries) {
            try {
                LdapName entryName = new LdapName(entry);
                if (!config.isContainedUnderBaseDNs(entryName)) {
                    return entry;
                }
            } catch (InvalidNameException e) {
                throw new ConnectorException(e);
            }
        }
        return null;
    }

    public boolean needsBinaryOption(String attrName) {
        return LDAP_BINARY_OPTION_ATTRS.contains(attrName);
    }

    public boolean isBinarySyntax(String attrName) {
        return LDAP_BINARY_SYNTAX_ATTRS.contains(attrName);
    }
}
