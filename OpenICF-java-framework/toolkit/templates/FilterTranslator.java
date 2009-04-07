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
package <%= packageName %>;

import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.common.StringUtil;

/**
 * This is an implementation of AbstractFilterTranslator that gives a concrete representation
 * of which filters can be applied at the connector level (natively). If the 
 * <%= resourceName %> doesn't support a certain expression type, that factory
 * method should return null. This level of filtering is present only to allow any
 * native contructs that may be available to help reduce the result set for the framework,
 * which will (strictly) reapply all filters specified after the connector does the initial
 * filtering.<p><p>Note: The generic query type is most commonly a String, but does not have to be.
 * 
 * @author $userName
 * @version 1.0
 * @since 1.0
 */
public class <%= resourceName %>FilterTranslator extends AbstractFilterTranslator<String> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createContainsExpression(ContainsFilter filter, boolean not) {
        /* 
         * Example implementation:
         * You may define the format of the queries for your connector, but
         * you must make sure that the executeQuery() (if you implemented Search) 
         * method handles it appropriately.
         */   
        String name = filter.getAttribute().getName();
        String value = AttributeUtil.getAsStringValue(filter.getAttribute());        
        if (StringUtil.isBlank(value)) {
            return null;
        }else if(not) { 
            //create an expression that means "not contains" or "doesn't contain" if possible
            return name + "!=*" + value + "*"; 
        }else {
            return name + "=*" + value + "*";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createEndsWithExpression(EndsWithFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createStartsWithExpression(StartsWithFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createEqualsExpression(EqualsFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createAndExpression(String leftExpression, String rightExpression) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createOrExpression(String leftExpression, String rightExpression) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createGreaterThanExpression(GreaterThanFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createGreaterThanOrEqualExpression(GreaterThanOrEqualFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createLessThanExpression(LessThanFilter filter, boolean not) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String createLessThanOrEqualExpression(LessThanOrEqualFilter filter, boolean not) {
        return null;
    }
}
