/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
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

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import static org.identityconnectors.common.CollectionUtil.newCaseInsensitiveSet;
import static org.identityconnectors.common.StringUtil.isNotBlank;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValue;
import static org.identityconnectors.ldap.LdapUtil.getStringAttrValues;
import static org.identityconnectors.ldap.LdapUtil.nullAsEmpty;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.identityconnectors.common.Pair;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.GuardedString.Accessor;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.ldap.schema.LdapSchemaMapping;

import com.sun.jndi.ldap.ctl.PasswordExpiredResponseControl;

public class LdapConnection {

    // TODO: SASL authentication, "dn:entryDN" user name.

    // The LDAP attributes with a byte array syntax.
    private static final Set<String> LDAP_BINARY_SYNTAX_ATTRS;
    // The LDAP attributes which require the binary option for transfer.
    private static final Set<String> LDAP_BINARY_OPTION_ATTRS;

    static {
        // Cf. http://java.sun.com/products/jndi/tutorial/ldap/misc/attrs.html.
        LDAP_BINARY_SYNTAX_ATTRS = newCaseInsensitiveSet();
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
        LDAP_BINARY_OPTION_ATTRS = newCaseInsensitiveSet();
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
    private ServerType serverType;

    public LdapConnection(LdapConfiguration config) {
        this.config = config;
        schemaMapping = new LdapSchemaMapping(this);
    }

    public String format(String key, String dflt, Object... args) {
        return config.getConnectorMessages().format(key, dflt, args);
    }

    public LdapConfiguration getConfiguration() {
        return config;
    }

    public LdapContext getInitialContext() {
        if (initCtx != null) {
            return initCtx;
        }
        initCtx = connect(config.getPrincipal(), config.getCredentials());
        return initCtx;
    }

    private LdapContext connect(String principal, GuardedString credentials) {
        Pair<AuthenticationResult, LdapContext> pair = createContext(principal, credentials);
        if (pair.first.getType().equals(AuthenticationResultType.SUCCESS)) {
            return pair.second;
        }
        pair.first.propagate();
        throw new IllegalStateException("Should never get here");
    }

    private Pair<AuthenticationResult, LdapContext> createContext(String principal, GuardedString credentials) {
        final List<Pair<AuthenticationResult, LdapContext>> result = new ArrayList<Pair<AuthenticationResult, LdapContext>>(1);

        final Hashtable<Object, Object> env = new Hashtable<Object, Object>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(Context.PROVIDER_URL, getLdapUrls());
        env.put(Context.REFERRAL, "follow");

        if (config.isSsl()) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }

        String authentication = isNotBlank(principal) ? "simple" : "none";
        env.put(Context.SECURITY_AUTHENTICATION, authentication);

        if (isNotBlank(principal)) {
            env.put(Context.SECURITY_PRINCIPAL, principal);
            if (credentials != null) {
                credentials.access(new Accessor() {
                    public void access(char[] clearChars) {
                        env.put(Context.SECURITY_CREDENTIALS, clearChars);
                        // Connect while in the accessor, otherwise clearChars will be cleared.
                        result.add(createContext(env));
                    }
                });
                assert result.size() > 0;
            } else {
                result.add(createContext(env));
            }
        } else {
            result.add(createContext(env));
        }

        return result.get(0);
    }

    private Pair<AuthenticationResult, LdapContext> createContext(Hashtable<?, ?> env) {
        AuthenticationResult authnResult = null;
        InitialLdapContext context = null;
        try {
            context = new InitialLdapContext(env, null);
            if (config.isRespectResourcePasswordPolicyChangeAfterReset()) {
                if (hasPasswordExpiredControl(context.getResponseControls())) {
                    authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED);
                }
            }
            // TODO: process Password Policy control.
        } catch (AuthenticationException e) {
            String message = e.getMessage().toLowerCase();
            if (message.contains("password expired")) { // Sun DS.
                authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED, e);
            } else if (message.contains("password has expired")) { // RACF.
                authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED, e);
            } else {
                authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, e);
            }
        } catch (NamingException e) {
            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, e);
        }
        if (authnResult == null) {
            assert context != null;
            authnResult = new AuthenticationResult(AuthenticationResultType.SUCCESS);
        }
        return new Pair<AuthenticationResult, LdapContext>(authnResult, context);
    }

    private static boolean hasPasswordExpiredControl(Control[] controls) {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof PasswordExpiredResponseControl) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getLdapUrls() {
        StringBuilder builder = new StringBuilder();
        builder.append("ldap://");
        builder.append(config.getHost());
        builder.append(':');
        builder.append(config.getPort());
        for (String failover : nullAsEmpty(config.getFailover())) {
            builder.append(' ');
            builder.append(failover);
        }
        return builder.toString();
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

    public AuthenticationResult authenticate(String entryDN, GuardedString password) {
        assert entryDN != null;
        log.ok("Attempting to authenticate {0}", entryDN);
        Pair<AuthenticationResult, LdapContext> pair = createContext(entryDN, password);
        if (pair.second != null) {
            quietClose(pair.second);
        }
        log.ok("Authentication result: {0}", pair.first);
        return pair.first;
    }

    public void test() {
        checkAlive();
    }

    public void checkAlive() {
        try {
            Attributes attrs = getInitialContext().getAttributes("", new String[] { "subschemaSubentry" } );
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
                supportedControls = unmodifiableSet(getStringAttrValues(attrs, "supportedControl"));
            } catch (NamingException e) {
                log.warn(e, "Exception while retrieving the supported controls");
                supportedControls = emptySet();
            }
        }
        return supportedControls;
    }

    public ServerType getServerType() {
        if (serverType == null) {
            serverType = detectServerType();
        }
        return serverType;
    }

    private ServerType detectServerType() {
        try {
            Attributes attrs = getInitialContext().getAttributes("", new String[] { "vendorVersion" });
            String vendorVersion = getStringAttrValue(attrs, "vendorVersion");
            if (vendorVersion != null) {
                vendorVersion = vendorVersion.toLowerCase();
                if (vendorVersion.contains("opends")) {
                    return ServerType.OPENDS;
                }
                if (vendorVersion.contains("sun") && vendorVersion.contains("directory")) {
                    return ServerType.SUN_DSEE;
                }
            }
        } catch (NamingException e) {
            log.warn(e, "Exception while detecting the server type");
        }
        return ServerType.UNKNOWN;
    }

    public boolean needsBinaryOption(String attrName) {
        return LDAP_BINARY_OPTION_ATTRS.contains(attrName);
    }

    public boolean isBinarySyntax(String attrName) {
        return LDAP_BINARY_SYNTAX_ATTRS.contains(attrName);
    }

    public enum AuthenticationResultType {

        SUCCESS {
            @Override
            public void propagate(Exception cause) {
            }
        },
        PASSWORD_EXPIRED {
            @Override
            public void propagate(Exception cause) {
                throw new PasswordExpiredException(cause);
            }
        },
        FAILED {
            @Override
            public void propagate(Exception cause) {
                throw new ConnectorSecurityException(cause);
            }
        };

        public abstract void propagate(Exception cause);
    }

    public static class AuthenticationResult {

        private final AuthenticationResultType type;
        private final Exception cause;

        public AuthenticationResult(AuthenticationResultType type) {
            this(type, null);
        }

        public AuthenticationResult(AuthenticationResultType type, Exception cause) {
            assert type != null;
            this.type = type;
            this.cause = cause;
        }

        public void propagate() {
            type.propagate(cause);
        }

        public AuthenticationResultType getType() {
            return type;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("AuthenticationResult[type: " + type);
            if (cause != null) {
                result.append("; cause: " + cause.getMessage());
            }
            result.append(']');
            return result.toString();
        }
    }

    public enum ServerType {

        SUN_DSEE, OPENDS, UNKNOWN
    }
}
