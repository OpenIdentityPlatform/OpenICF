/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package samples.kerberos.scripts

import org.identityconnectors.framework.common.objects.filter.*

/**
 *
 * A KerberosFilterVisistor converts a {@link org.identityconnectors.framework.common.objects.filter.Filter} to
 * input search criteria(s) for the kadmin Kerberos admin command line (list_principals)
 */
public class KerberosFilterVisistor implements FilterVisitor<String, Void> {

    public static final KerberosFilterVisistor INSTANCE = new KerberosFilterVisistor();

    /**
     * Only these attributes can be used as a search criteria
     */
    private static final ArrayList<String> ALLOWED = new ArrayList<String>(Arrays.asList(
            "PRINCIPAL",
            "__NAME__",
            "__UID__"
    ));

    @Override
    public String visitContainsFilter(Void p, ContainsFilter filter) {
        String param = filter.getName().toUpperCase();
        if (!ALLOWED.contains(param)) {
            throw new UnsupportedOperationException(param + "is not supported as a search criteria");
        }
        return "*" + filter.getValue() + "*";
    }

    @Override
    public String visitStartsWithFilter(Void p, StartsWithFilter filter) {
        String param = filter.getName().toUpperCase();
        if (!ALLOWED.contains(param)) {
            throw new UnsupportedOperationException(param + "is not supported as a search criteria");
        }
        return filter.getValue() + "*";
    }

    @Override
    public String visitEndsWithFilter(Void p, EndsWithFilter filter) {
        String param = filter.getName().toUpperCase();
        if (!ALLOWED.contains(param)) {
            throw new UnsupportedOperationException(param + "is not supported as a search criteria");
        }
        return "*" + filter.getValue();
    }

    @Override
    public String visitEqualsFilter(Void p, EqualsFilter filter) {
        String param = filter.getName().toUpperCase();
        if (!ALLOWED.contains(param)) {
            throw new UnsupportedOperationException(param + "is not supported as a search criteria");
        }
        return filter.getAttribute().getValue().get(0).toString();
    }

    @Override
    public String visitContainsAllValuesFilter(Void p, ContainsAllValuesFilter filter) {
        throw new UnsupportedOperationException("ContainsAllValuesFilter is not supported.");
    }

    @Override
    public String visitExtendedFilter(Void p, Filter filter) {
        throw new UnsupportedOperationException("Filter type is not supported: " + filter.getClass());
    }

    @Override
    public String visitGreaterThanFilter(Void p, GreaterThanFilter filter) {
        throw new UnsupportedOperationException("GreaterThanFilter is not supported.");
    }

    @Override
    public String visitGreaterThanOrEqualFilter(Void p, GreaterThanOrEqualFilter filter) {
        throw new UnsupportedOperationException("GreaterThanOrEqualFilter is not supported.");
    }

    @Override
    public String visitLessThanFilter(Void p, LessThanFilter filter) {
        throw new UnsupportedOperationException("LessThanFilter is not supported.");
    }

    @Override
    public String visitLessThanOrEqualFilter(Void p, LessThanOrEqualFilter filter) {
        throw new UnsupportedOperationException("LessThanOrEqualFilter is not supported yet.");
    }

    @Override
    public String visitNotFilter(Void p, NotFilter filter) {
        throw new UnsupportedOperationException("NotFilter is not supported yet.");
    }

    @Override
    public String visitOrFilter(Void p, OrFilter filter) {
        throw new UnsupportedOperationException("OrFilter is not supported yet.");
    }

    @Override
    public String visitAndFilter(Void p, AndFilter filter) {
        throw new UnsupportedOperationException("AndFilter is not supported.");
    }
}