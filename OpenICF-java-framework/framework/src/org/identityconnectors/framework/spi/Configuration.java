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
package org.identityconnectors.framework.spi;

import java.net.URI;
import java.util.Locale;

import org.identityconnectors.framework.common.objects.ConnectorMessages;


/**
 * All Bean properties are considered configuration for the Connector. The
 * implementation of the Configuration interface must have a default
 * constructor. Each Bean property will correspond to two message fields in a
 * properties file named Messages in the same package as the implementing class.
 * For instance "hostname.help" is the key in the messages files for the
 * 'Hostname' property. The other property "hostname.display" is the message
 * used to display the property in a view. The initial get[Bean] is used as
 * default for display. All Beans properties are considered required. Use the
 * validate method to determine if all necessary configuration information
 * provided is valid.
 * <p>
 * Valid types for use are (array form of the type is also valid):
 * <ul>
 * <li>{@link String}</li>
 * <li>{@link Integer}</li>
 * <li>{@link Long}</li>
 * <li>{@link Boolean}</li>
 * <li>{@link Float}</li>
 * <li>{@link Double}</li>
 * <li>{@link URI}</li>
 * </ul>
 */
public interface Configuration {
    /**
     * Determine if the configuration is valid based on the values set.
     */
    public void validate();

    /**
     * Should return the ConnectorMessages that is set by {#setConnectorMessages}
     * @return The locale
     */
    public ConnectorMessages getConnectorMessages();
    
    /**
     * Should return the locale that is set by {#setLocale}
     * @return The locale
     */
    public Locale getLocale();
    
    /**
     * Called after the {@link Configuration}'s instance is created to make
     * sure that the {@link Connector} has the proper locale to create messages
     * for {@link Exception}s etc. 
     * 
     * @param locale
     *            current locale the {@link Connector} is operating in.
     */
    public void setLocale(Locale locale);
    
    /**
     * Called after the {@link Configuration}'s instance is created to make
     * sure that the {@link Connector} has the proper ConnectorMessages to create messages
     * for {@link Exception}s etc. 
     * 
     * @param messages
     *            The message catalog for the given connector.
     */
    public void setConnectorMessages(ConnectorMessages messages);
}
