/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openicf.maven.xhtml;

import org.apache.maven.doxia.module.xhtml.XhtmlParser;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.sink.Sink;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * A XhtmljsParser allows the script content in final HTML document.
 *
 * @author Laszlo Hordos
 */
@Component(role = Parser.class, hint = "xhtml-custom")
public class XhtmlCustomParser extends XhtmlParser {

    /** {@inheritDoc} */
    @Override
    protected void handleText(XmlPullParser parser, Sink sink) throws XmlPullParserException {
        String text = getText(parser);
        if (StringUtils.isNotEmpty(text)) {
            if (isScriptBlock()) {
                sink.rawText(text);
            } else {
                super.handleText(parser, sink);
            }
        }
    }
}
