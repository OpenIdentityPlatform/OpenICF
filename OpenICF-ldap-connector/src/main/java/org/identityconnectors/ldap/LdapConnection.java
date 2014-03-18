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
 * 
 * Portions Copyrighted 2013 Forgerock
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
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;

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

        // MS-AD ObjectGUID
        LDAP_BINARY_SYNTAX_ATTRS.add(LdapConstants.MS_GUID_ATTR);
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
        env.put("java.naming.ldap.attributes.binary", LdapConstants.MS_GUID_ATTR);
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(Context.PROVIDER_URL, getLdapUrls());
        env.put(Context.REFERRAL, config.getReferralsHandling());

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
            //SUN_DSEE, OPENDS, OPENDJ, IBM, MSAD, MSAD_LDS, MSAD_GC, NOVELL, UNBOUNDID, OPENLDAP, UNKNOWN
            switch (getServerType()) {
                case MSAD:
                case MSAD_GC:
                case MSAD_LDS:
                    if (message.contains("ldap: error code 49 ")) {
                        if (message.contains("data 525,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("User not found"));
                        } else if (message.contains("data 52e,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("Invalid credentials"));
                        } else if (message.contains("data 530,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("Not permitted to logon at this time"));
                        } else if (message.contains("data 531,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("Not permitted to logon at this workstation"));
                        } else if (message.contains("data 532,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED, new AuthenticationException("Password expired"));
                        } else if (message.contains("data 533,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("Account disabled"));
                        } else if (message.contains("data 701,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("Account expired"));
                        } else if (message.contains("data 773,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("User must reset password"));
                        } else if (message.contains("data 775,")) {
                            authnResult = new AuthenticationResult(AuthenticationResultType.FAILED, new AuthenticationException("User account locked"));
                        } else {
                        }
                    }
                    break;
                case SUN_DSEE:
                    if (message.contains("password expired")) { // Sun DS.
                        authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED, e);
                    }
                    break;
                case UNKNOWN:
                    if (message.contains("password has expired")) { // RACF.
                        authnResult = new AuthenticationResult(AuthenticationResultType.PASSWORD_EXPIRED, e);
                    }
                    break;
                case OPENDJ:
                case OPENLDAP:
                default:
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
            Attributes attrs = getInitialContext().getAttributes("", new String[]{"subschemaSubentry"});
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
                Attributes attrs = getInitialContext().getAttributes("", new String[]{"supportedControl"});
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
            Attributes attrs = getInitialContext().getAttributes("", new String[]{"vendorVersion", "vendorName", "highestCommittedUSN", "rootDomainNamingContext", "structuralObjectClass"});
            String vendorName = getStringAttrValue(attrs, "vendorName");
            if (null != vendorName) {
                vendorName = vendorName.toLowerCase();
                if (vendorName.contains("ibm")) {
                    log.info("IBM Directory server has been detected");
                    return ServerType.IBM;
                }
                if (vendorName.contains("novell")) {
                    log.info("Novell eDirectory server has been detected");
                    return ServerType.NOVELL;
                }
                if (vendorName.contains("unboundid")) {
                    log.info("UnboundID Directory server has been detected");
                    return ServerType.UNBOUNDID;
                }
            }
            String vendorVersion = getStringAttrValue(attrs, "vendorVersion");
            if (vendorVersion != null) {
                vendorVersion = vendorVersion.toLowerCase();
                if (vendorVersion.contains("opends")) {
                    log.info("OpenDS Directory server has been detected");
                    return ServerType.OPENDS;
                }
                if (vendorVersion.contains("opendj")) {
                    log.info("ForgeRock OpenDJ Directory server has been detected");
                    return ServerType.OPENDJ;
                }
                if (vendorVersion.contains("sun") && vendorVersion.contains("directory")) {
                    log.info("Sun DSEE Directory server has been detected");
                    return ServerType.SUN_DSEE;
                }
            } else {
                String hUSN = getStringAttrValue(attrs, "highestCommittedUSN");
                String rDC = getStringAttrValue(attrs, "rootDomainNamingContext");
                String sOC = getStringAttrValue(attrs, "structuralObjectClass");
                if (hUSN != null) {
                // Windows Active Directory
                    if (rDC != null) {
                        // Only DCs and GCs have the rootDomainNamingContext
                        // We check the port number as well. DC is using the standard 389|636 pair.
                        if ((config.getPort() != 389) && (config.getPort() != 636)) {
                            log.info("MS Active Directory Global Catalog server has been detected");
                            return ServerType.MSAD_GC;
                        } else {
                            log.info("MS Active Directory server has been detected");
                            return ServerType.MSAD;
                        }
                    }
                    // ADLDS does not have the rootDomainNamingContext...
                    log.info("MS Active Directory Lightweight Directory Services server has been detected");
                    return ServerType.MSAD_LDS;
                }
                else if (sOC != null && sOC.equalsIgnoreCase("OpenLDAProotDSE")){
                    return ServerType.OPENLDAP;
                }
            }
        } catch (NamingException e) {
            log.warn(e, "Exception while detecting the server type");
        }
        log.info("Directory server type is unknown");
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
                throw new InvalidCredentialException(cause.getMessage(),cause);
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

        SUN_DSEE, OPENDS, OPENDJ, IBM, MSAD, MSAD_LDS, MSAD_GC, NOVELL, UNBOUNDID, OPENLDAP, UNKNOWN
    }
}
