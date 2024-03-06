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

package org.forgerock.openicf.connectors.xml.query;

import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQConstants;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQExpression;
import javax.xml.xquery.XQResultSequence;
import net.sf.saxon.xqj.SaxonXQDataSource;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.w3c.dom.Document;

public class XQueryHandler {

    private XQDataSource datasource;
    private XQConnection connection;
    private XQExpression expression;
    private String query;
    private Document document;

    /**
     * Setup logging for the {@link XQueryHandler}.
     */
    private static final Log log = Log.getLog(XQueryHandler.class);

    public XQueryHandler(String query, Document document) {
        this.query = query;
        this.document = document;

        initialize();
    }

    private void initialize() {
        final String method = "initialize";
        log.info("Entry {0}", method);

        try {
            datasource = new SaxonXQDataSource();
            connection = datasource.getConnection();
            expression = connection.createExpression();
            expression.bindNode(XQConstants.CONTEXT_ITEM, document, null);
        }
        catch (XQException ex) {
            log.warn(ex, method);
            throw ConnectorException.wrap(ex);
        }

        log.info("Exit {0}", method);
    }

    public XQResultSequence getResultSequence() {
        final String method = "getResultSequence";
        log.info("Entry {0}", method);

        XQResultSequence sequence = null;
        try {
            sequence = expression.executeQuery(query);
        }
        catch (XQException ex) {
            log.warn(ex, method);
            throw ConnectorException.wrap(ex);
        }
        
        log.info("Exit {0}", method);

        return sequence;
    }

    public void close() {
        final String method = "close";
        log.info("Entry {0}", method);

        try {
            connection.close();
            expression.close();
        }
        catch (XQException ex) {
            log.warn(ex, method);
            throw ConnectorException.wrap(ex);
        }

        log.info("Exit {0}", method);
    }
}