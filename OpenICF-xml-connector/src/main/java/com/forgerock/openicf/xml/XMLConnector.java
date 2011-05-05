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
package com.forgerock.openicf.xml;

import com.forgerock.openicf.xml.query.abstracts.Query;
import com.forgerock.openicf.xml.query.QueryBuilder;
import com.forgerock.openicf.xml.xsdparser.SchemaParser;
import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.security.*;
import org.identityconnectors.framework.spi.*;
import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.InvalidPasswordException;

/**
 *
 * @author $author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
@ConnectorClass(displayNameKey = "XML", configurationClass = XMLConfiguration.class)
public class XMLConnector implements PoolableConnector, AuthenticateOp, CreateOp, DeleteOp, SearchOp<Query>, SchemaOp, TestOp, UpdateOp {

    private static final Log log = Log.getLog(XMLConnector.class);
    private XMLHandler xmlHandler;
    private XMLConfiguration config;
    private SchemaParser schemaParser;
    //@TODO - Use cache while more than one client is alive
    private static volatile int invokers = 0;

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
        final String method = "init";
        log.info("Entry {0}", method);

        Assertions.nullCheck(configuration, "config");

        synchronized (XMLConnector.class) {
            this.config = (XMLConfiguration) configuration;
            this.schemaParser = new SchemaParser(XMLConnector.class, config.getXsdFilePath());
            this.xmlHandler = new XMLHandlerImpl(config, schema(), schemaParser.getXsdSchema());
            //Increase the number of actual threads that are using this connector form the pool.
            invokers++;
        }
        log.info("XMLConnector initialized");
        log.info("Exit {0}", method);
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    public void dispose() {
        synchronized (XMLConnector.class) {
            invokers--;
            if (invokers == 0) {
                xmlHandler.serialize();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.PoolableConnector#checkAlive()
     */
    public void checkAlive() {
        //TODO: implement this method
    }

    @Override
    public Uid authenticate(final ObjectClass objClass, final String username, final GuardedString password, final OperationOptions options) {
        final String method = "authenticate";
        log.info("Entry {0}", method);

        Assertions.nullCheck(objClass, "objectClass");
        Assertions.nullCheck(username, "username");
        Assertions.blankCheck(username, "username");
        Assertions.nullCheck(password, "password");

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Authentication failed. Can only authenticate against " + ObjectClass.ACCOUNT_NAME + " resources.");
        }

        Uid uid = xmlHandler.authenticate(username, password);

        if (uid == null) {
            throw new InvalidPasswordException("Invalid password for user: " + username);
        }

        log.info("Exit {0}", method);

        return uid;
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.CreateOp#create(org.identityconnectors.framework.common.objects.ObjectClass, java.util.Set, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    @Override
    public Uid create(final ObjectClass objClass, final Set<Attribute> attributes, final OperationOptions options) {
        final String method = "create";
        log.info("Entry {0}", method);

        Assertions.nullCheck(objClass, "objectClass");
        Assertions.nullCheck(attributes, "attributes");

        Uid returnUid = xmlHandler.create(objClass, attributes);

        log.info("Exit {0}", method);

        return returnUid;
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.UpdateOp#update(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.Uid, java.util.Set, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    @Override
    public Uid update(ObjectClass objClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        final String method = "update";
        log.info("Entry {0}", method);

        Assertions.nullCheck(objClass, "objectClass");
        Assertions.nullCheck(uid, "attributes");

        Uid returnUid = xmlHandler.update(objClass, uid, replaceAttributes);

        log.info("Exit {0}", method);

        return returnUid;
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.DeleteOp#delete(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.Uid, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    @Override
    public void delete(final ObjectClass objClass, final Uid uid, final OperationOptions options) {
        final String method = "delete";
        log.info("Entry {0}", method);

        Assertions.nullCheck(objClass, "objectClass");
        Assertions.nullCheck(uid, "uid");

        xmlHandler.delete(objClass, uid);

        log.info("Exit {0}", method);
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SchemaOp#schema()
     */
    @Override
    public Schema schema() {
        return schemaParser.parseSchema();
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SearchOp#createFilterTranslator(org.identityconnectors.framework.common.objects.ObjectClass, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    @Override
    public FilterTranslator<Query> createFilterTranslator(ObjectClass objClass, OperationOptions options) {
        return new XMLFilterTranslator();
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.SearchOp#executeQuery(org.identityconnectors.framework.common.objects.ObjectClass, java.lang.Object, org.identityconnectors.framework.common.objects.ResultsHandler, org.identityconnectors.framework.common.objects.OperationOptions)
     */
    @Override
    public void executeQuery(ObjectClass objClass, Query query, ResultsHandler handler, OperationOptions options) {
        final String method = "executeQuery";
        log.info("Entry {0}", method);

        QueryBuilder queryBuilder = new QueryBuilder(query, objClass);

        Collection<ConnectorObject> hits = xmlHandler.search(queryBuilder.toString(), objClass);

        for (ConnectorObject hit : hits) {
            handler.handle(hit);
        }

        log.info("Exit {0}", method);
    }

    /*
     * (non-Javadoc)
     * @see org.identityconnectors.framework.spi.operations.TestOp#test()
     */
    @Override
    public void test() {
        final String method = "test";
        log.info("Entry {0}", method);

        Assertions.nullCheck(config, "config");
        Assertions.nullCheck(xmlHandler, "xmlHandler");
        Assertions.nullCheck(schemaParser, "schemaParser");

        config.validate();

        log.info("Exit {0}", method);
    }
}
