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

package com.forgerock.openicf.xml.xsdparser;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.sun.xml.xsom.parser.AnnotationContext;
import com.sun.xml.xsom.parser.AnnotationParser;

public class XSDAnnotationParser extends AnnotationParser{
	
	private boolean parse = false;
	private String addValue = "";
	
	StringBuilder stringBuilder = new StringBuilder();

	@Override
	public ContentHandler getContentHandler(AnnotationContext annotationContext, String parentElementName, ErrorHandler errorHandler, EntityResolver entryResolver) {
            return new ContentHandler() {

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

                    if(localName.equals("appinfo") || localName.equals("sequence")){
                        parse = true;
                    }

                    if(!qName.equals("xsd:appinfo") && !qName.equals("xsd:element")){
                        addValue = qName;
                    }
                }
                
                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if(localName.equals("appinfo")){
                            parse = false;
                    }
                }

                @Override
                public void characters(char[] chars, int start, int length) throws SAXException {
                    if(parse){
                        StringBuilder sb = new StringBuilder();
                        sb.append(chars, start, length);
                        if(!sb.toString().replace(" ", "").trim().equals("")){
                            String stringToAppend = addValue + " " + sb.toString().trim();
                            stringBuilder.append(stringToAppend);
                            stringBuilder.append("\n");
                        }
                    }
                }

                @Override
                public void startPrefixMapping(String prefix, String uri) throws SAXException {}

                @Override
                public void endPrefixMapping(String prefix) throws SAXException {}

                @Override
                public void startDocument() throws SAXException {}
                
                @Override
                public void endDocument() throws SAXException {}

                @Override
                public void skippedEntity(String name) throws SAXException {}

                @Override
                public void setDocumentLocator(Locator locator) {}

                @Override
                public void processingInstruction(String target, String data)throws SAXException {}

                @Override
                public void ignorableWhitespace(char[] chars, int start, int length)throws SAXException {}
            };
	}

	@Override
	public Object getResult(Object object) {
            return stringBuilder.toString().trim();
	}
}