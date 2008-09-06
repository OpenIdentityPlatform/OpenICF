/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.framework.impl.serializer.xml;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.serializer.XmlObjectResultsHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


public class XmlObjectParser {

        
    public static void parse(InputSource inputSource,
            XmlObjectResultsHandler handler, 
            boolean validate)
    {
        try
        { 
            MySAXHandler saxHandler = new MySAXHandler(handler,validate);
            XMLReader reader = XMLReaderFactory.createXMLReader();
            reader.setFeature("http://xml.org/sax/features/validation",validate);
            reader.setEntityResolver(saxHandler);
            reader.setContentHandler(saxHandler);
            reader.setErrorHandler(saxHandler);
            reader.parse(inputSource);
        }
        catch (Exception e)
        {
            throw ConnectorException.wrap(e);
        }
    }


        

    private static class MySAXHandler implements ContentHandler, EntityResolver, ErrorHandler
    {
        /**
         * The document for the current top-level element. with each top-level element,
         * we discard the previous to avoid accumulating memory
         */
        private Document _currentTopLevelElementDocument;
        

        /**
         * Stack of elements we are creating
         */
        private List<Element> _elementStack = new ArrayList<Element>(10);

        /**
         * Do we want to validate
         */
        private final boolean _validate;

        /**
         * Results handler that we write our objects to
         */
        private final XmlObjectResultsHandler _handler;
        
        /**
         * Is the handler still handing
         */
        private boolean _stillHandling = true;
        
        
        public MySAXHandler(XmlObjectResultsHandler handler, boolean validate)
        {
            _handler  = handler;
            _validate = validate;
        }


        private Element getCurrentElement()
        {
            if (_elementStack.size() > 0 )
            {
                return _elementStack.get(_elementStack.size()-1);
            }
            else
            {
                return null;
            }
        }

        public void characters(char[] ch, int start, int length) 
        {
            Element currentElement = getCurrentElement();
            if ( currentElement != null )
            {
                currentElement.appendChild(_currentTopLevelElementDocument.createTextNode(new String(ch, start, length)));
            }
        }

        public void endDocument() 
        {
        }

        public void endElement(String namespaceURI, String localName, String qName)
        {
            if (_elementStack.size() > 0) //we don't push the top-level MULTI_OBJECT_ELEMENT on the stack
            {
                Element element = _elementStack.remove(_elementStack.size()-1);
                if (_elementStack.size() == 0) {
                    _currentTopLevelElementDocument = null;
                    if (_stillHandling) {
                        XmlObjectDecoder decoder = new XmlObjectDecoder(element,null);
                        Object object = decoder.readObject();
                        _stillHandling = _handler.handle(object);
                    }
                }
            }            
        }

        public void endPrefixMapping(String prefix) 
        {
        }

        public void ignorableWhitespace(char[] ch, int start, int length) 
        {
            Element currentElement = getCurrentElement();
            if ( currentElement != null )
            {
                currentElement.appendChild(_currentTopLevelElementDocument.createTextNode(new String(ch, start, length)));
            }
        }

        public void processingInstruction(String target, String data) 
        {
        }

        public void setDocumentLocator(Locator locator) 
        {
        }

        public void skippedEntity(String name) 
        {
        }

        public void startDocument() 
        {
        }

        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) 
        {
            Element element = null;
            if (_elementStack.size() == 0)
            {
                if (!XmlObjectSerializerImpl.MULTI_OBJECT_ELEMENT.equals(localName))
                {
                    try {
                        _currentTopLevelElementDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                    }
                    catch (Exception e) {
                        throw ConnectorException.wrap(e);
                    }
                    element = _currentTopLevelElementDocument.createElement(localName);
                }
            }
            else
            {
                element = 
                    _currentTopLevelElementDocument.createElement(localName);
                getCurrentElement().appendChild(element);
            }
            
            if ( element != null )
            {
                _elementStack.add(element);
                for (int i = 0; i < atts.getLength(); i++)
                {
                    String attrName  = atts.getLocalName(i);
                    String value     = atts.getValue(i);
                    element.setAttribute(attrName, value);
                }
            }
        }

        public void startPrefixMapping(String prefix, String uri) 
        {
        }

        public InputSource resolveEntity(String pubid, String sysid)
            throws SAXException {
            if (XmlObjectSerializerImpl.CONNECTORS_DTD.equals(pubid))
            {
                //stupid freakin sax parser. even if validation
                //is turned off it still takes the same amount of
                //time. need to return an empty dtd to fake it out
                if (!_validate)
                {
                    return new InputSource(new StringReader("<?xml version='1.0' encoding='UTF-8'?>"));
                }
                try
                {
                    URL resoUrl = XmlObjectParser.class.getResource(pubid);
                    return new InputSource(resoUrl.openStream());            
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    throw new SAXException(e);
                }
            }
            else
            {
                return null;
            }
        }

        public void error(SAXParseException exception)
           throws SAXException
        {
            throw exception;
        }

        public void fatalError(SAXParseException exception)
                throws SAXException
        {
            throw exception;
        }

        public void warning(SAXParseException exception)
        {
        }
    }

}
