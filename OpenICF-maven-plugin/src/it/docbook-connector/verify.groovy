/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 ForgeRock AS. All Rights Reserved
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

assert new File( basedir, 'target/openicf-docbkx' ).exists();
assert new File( basedir, 'target/docbkx/pdf/OpenICF-Docbook-Connector-1.4.0.0-Snapshot.pdf' ).exists();
content = new File( basedir, 'target/docbkx/html/docbook-connector-1.4.0.0-SNAPSHOT/index.html' ).text;

assert content.contains( '"connectorName" : "org.forgerock.openicf.connectors.docbook.DocBookConnector"' );


sitedir = new File( basedir, 'target/site' );

assert new File( sitedir, 'openicf-report.html' ).exists();


return true;