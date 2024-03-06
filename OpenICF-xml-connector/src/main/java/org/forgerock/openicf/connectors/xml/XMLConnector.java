/*
 *
 * Copyright (c) 2010 ForgeRock Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1.php or
 * OpenIDM/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at OpenIDM/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2010 [name of copyright owner]"
 *
 * $Id$
 */
package org.forgerock.openicf.connectors.xml;

import org.forgerock.openicf.connectors.xml.query.abstracts.Query;
import org.forgerock.openicf.connectors.xml.query.QueryBuilder;
import org.forgerock.openicf.connectors.xml.xsdparser.SchemaParser;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.security.*;
import org.identityconnectors.framework.spi.*;
import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;

/**
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
@ConnectorClass(displayNameKey = "XML", configurationClass = XMLConfiguration.class)
public class XMLConnector implements Connector, AuthenticateOp, CreateOp, DeleteOp, SearchOp<Query>, SchemaOp, TestOp, UpdateOp {

    private static final Log log = Log.getLog(XMLConnector.class);
    private static /*volatile*/ final Map<String, ConcurrentXMLHandler> XMLHandlerCache = new HashMap<String, ConcurrentXMLHandler>(1);
    private XMLConfiguration config;
    private XMLHandler xmlInstanceHandler = null;

    private static Map<String, Object> lockMap = new HashMap<String, Object>();

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.Connector#getConfiguration()
     */
    public Configuration getConfiguration() {
        return this.config;
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    public void init(Configuration configuration) {
        this.config = Assertions.nullChecked((XMLConfiguration) configuration, "config");
        synchronized (XMLConnector.class) {
            try {
                String canonicalPath = config.getXmlFilePath().getCanonicalPath();
                ConcurrentXMLHandler handler = XMLHandlerCache.get(canonicalPath);

                if (null == handler) {
                    SchemaParser schemaParser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
                    handler = new ConcurrentXMLHandler(config, schema(), schemaParser.getXsdSchema());
                    XMLHandlerCache.put(canonicalPath, handler);
                }
                xmlInstanceHandler = handler.init();
            }
            catch (IOException ex) {
                log.error(ex, "Failed to get the CanonicalPath of {0}", config.getXmlFilePath());
                throw new ConnectorIOException(ex);
            }
        }
        log.info("XMLConnector initialized");
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
        xmlInstanceHandler.dispose();
        log.ok("Dispose {0}", config.getXmlFilePath());
    }
    
    private synchronized Object getLock() {
        String filename = config.getXmlFilePath().getAbsolutePath();
        Object lock = lockMap.get(filename);
        if (lock == null) {
            lock = new Object();
            lockMap.put(filename, lock);
        }
        return lock;
    }

    public Uid authenticate(final ObjectClass objClass, final String username, final GuardedString password, final OperationOptions options) {
        if (ObjectClass.ACCOUNT.is(Assertions.nullChecked(objClass, "objectClass").getObjectClassValue())) {

            Uid uid = xmlInstanceHandler.authenticate(Assertions.blankChecked(username, "username"), Assertions.nullChecked(password, "password"));

            if (uid == null) {
                throw new InvalidPasswordException("Invalid password for user: " + username);
            }
            log.info("authenticated {0}", uid);
            return uid;
        }
        throw new IllegalArgumentException("Authentication failed. Can only authenticate against " + ObjectClass.ACCOUNT_NAME + " resources.");
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.CreateOp#create(org.identityconnectors.framework.common.objects.ObjectClass, java.util.Set, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public Uid create(final ObjectClass objClass, final Set<Attribute> attributes, final OperationOptions options) {
        synchronized (getLock()) {
            Assertions.nullCheck(objClass, "objectClass");
            Assertions.nullCheck(attributes, "attributes");
            Uid returnUid = xmlInstanceHandler.create(objClass, attributes);
            log.info("Created {0}", returnUid);
            return returnUid;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.UpdateOp#update(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.Uid, java.util.Set, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public Uid update(ObjectClass objClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        synchronized (getLock()) {
            Assertions.nullCheck(objClass, "objectClass");
            Assertions.nullCheck(uid, "attributes");
            Uid returnUid = xmlInstanceHandler.update(objClass, uid,
                    replaceAttributes);
            log.info("Updated {0}", returnUid);
            return returnUid;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.DeleteOp#delete(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.Uid, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public void delete(final ObjectClass objClass, final Uid uid, final OperationOptions options) {
        synchronized (getLock()) {
            Assertions.nullCheck(objClass, "objectClass");
            Assertions.nullCheck(uid, "uid");
            xmlInstanceHandler.delete(objClass, uid);
            log.info("Deleted {0}", uid);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SchemaOp#schema()
     */
    public Schema schema() {
        SchemaParser schemaParser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
        return schemaParser.parseSchema();
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SearchOp#createFilterTranslator(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public FilterTranslator<Query> createFilterTranslator(ObjectClass objClass, OperationOptions options) {
        return new XMLFilterTranslator(xmlInstanceHandler.isSupportUid(objClass));
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SearchOp#executeQuery(org.identityconnectors.framework.common.objects.ObjectClass, java.lang.Object, org.identityconnectors.framework.common.objects.ResultsHandler, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    public void executeQuery(ObjectClass objClass, Query query, ResultsHandler handler, OperationOptions options) {
        synchronized (getLock()) {
            QueryBuilder queryBuilder = new QueryBuilder(query, objClass);
            Collection<ConnectorObject> hits = xmlInstanceHandler.search(
                    queryBuilder.toString(), objClass);
            int count = 0;
            for (ConnectorObject hit : hits) {
                count++;
                handler.handle(hit);
            }
            log.info("Query returned {0} object(s)", count);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.TestOp#test()
     */
    public void test() {
        Assertions.nullCheck(config, "configuration");
        Assertions.nullCheck(xmlInstanceHandler, "xmlHandler");
        config.validate();
        log.info("Test Succeed");
    }
}
