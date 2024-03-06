/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
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

package org.identityconnectors.framework.common.objects.filter;

import java.util.List;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.l10n.CurrentLocale;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;

/**
 * A FilteredResultsHandlerVisitor can accept the
 * {@link org.identityconnectors.framework.common.objects.ConnectorObject}. It
 * can be used for case-sensitive and case-ignore mode to accept the
 * {@link java.lang.String} and {@link java.lang.Character} values.
 * 
 * @since 1.5
 */
public class FilteredResultsHandlerVisitor implements
        FilterVisitor<FilteredResultsHandlerVisitor.FilterResult, ConnectorObject> {

    public static final FilteredResultsHandlerVisitor DEFAULT_CASE_SENSITIVE_VISITOR =
            new FilteredResultsHandlerVisitor(false);
    public static final FilteredResultsHandlerVisitor DEFAULT_CASE_IGNORE_VISITOR =
            new FilteredResultsHandlerVisitor(true);

    public static enum FilterResult {
        FALSE, TRUE, UNDEFINED;

        static FilterResult valueOf(final boolean b) {
            return b ? TRUE : FALSE;
        }

        boolean toBoolean() {
            return this == TRUE; // UNDEFINED collapses to FALSE.
        }
    }

    public static Filter wrapFilter(final Filter nestedFilter, final boolean caseIgnore) {
        return null == nestedFilter ? null : new Filter() {
            public boolean accept(final ConnectorObject obj) {
                return nestedFilter.accept(
                        caseIgnore ? DEFAULT_CASE_IGNORE_VISITOR : DEFAULT_CASE_SENSITIVE_VISITOR,
                        obj).toBoolean();
            }

            public <R, P> R accept(FilterVisitor<R, P> v, P p) {
                return v.visitExtendedFilter(p, this);
            }
        };
    }

    private boolean caseIgnore = false;

    public FilteredResultsHandlerVisitor(boolean caseIgnore) {
        this.caseIgnore = caseIgnore;
    }

    public FilterResult visitAndFilter(ConnectorObject connectorObject, AndFilter filter) {
        FilterResult result = FilterResult.TRUE;
        for (final Filter subFilter : filter.getFilters()) {
            final FilterResult r = subFilter.accept(this, connectorObject);
            if (r.ordinal() < result.ordinal()) {
                result = r;
            }
            if (result == FilterResult.FALSE) {
                break;
            }
        }
        return result;
    }

    public FilterResult visitContainsFilter(ConnectorObject connectorObject, ContainsFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        String valueAssertion = expectSingleValue(connectorObject, filter.getName(), String.class);
        if (null != valueAssertion) {
            if (caseIgnore) {
                result =
                        FilterResult.valueOf(valueAssertion.toLowerCase(CurrentLocale.get())
                                .contains(filter.getValue().toLowerCase(CurrentLocale.get())));
            } else {
                result = FilterResult.valueOf(valueAssertion.contains(filter.getValue()));
            }
        }
        return result;
    }

    public FilterResult visitContainsAllValuesFilter(ConnectorObject connectorObject,
            ContainsAllValuesFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        Attribute attribute = connectorObject.getAttributeByName(filter.getName());
        List<Object> attributeValues = null;
        if (null != attribute && null != (attributeValues = attribute.getValue())) {
            final List<Object> filterValues = filter.getAttribute().getValue();
            if (filterValues.isEmpty()) {
                result = FilterResult.TRUE;
            } else if (attributeValues.isEmpty()) {
                result = FilterResult.FALSE;
            } else if (caseIgnore) {
                boolean stillContains = true;
                for (Object o : filter.getAttribute().getValue()) {
                    boolean found = false;
                    if (o instanceof String) {
                        for (Object c : attributeValues) {
                            if (c instanceof String && ((String) c).equalsIgnoreCase((String) o)) {
                                found = true;
                                break;
                            }
                        }
                    } else if (o instanceof Character) {
                        for (Object c : attributeValues) {
                            if (c instanceof Character
                                    && Character.toUpperCase((Character) c) != Character
                                            .toUpperCase((Character) o)) {
                                found = true;
                                break;
                            }
                        }
                    } else {
                        result =
                                FilterResult.valueOf(attributeValues.containsAll(filter
                                        .getAttribute().getValue()));
                        break;
                    }
                    if (!(stillContains = stillContains && found)){
                        break;
                    }
                }
                result = FilterResult.valueOf(stillContains);
            } else {
                result = FilterResult.valueOf(attributeValues.containsAll(filterValues));
            }
        }
        return result;
    }

    public FilterResult visitEqualsFilter(ConnectorObject connectorObject, EqualsFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        Attribute attribute = connectorObject.getAttributeByName(filter.getName());
        if (null != attribute) {
            final List<Object> attributeValues = attribute.getValue();
            final List<Object> filterValues = filter.getAttribute().getValue();
            result =
                    FilterResult.valueOf(CollectionUtil.equals(attributeValues, filterValues,
                            caseIgnore));
        }
        return result;
    }

    public FilterResult visitExtendedFilter(ConnectorObject connectorObject, Filter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        if (filter instanceof PresenceFilter) {
            result =
                    FilterResult.valueOf(null != connectorObject
                            .getAttributeByName(((PresenceFilter) filter).getName()));
        }
        return result;
    }

    public FilterResult visitGreaterThanFilter(ConnectorObject connectorObject,
            GreaterThanFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        final Object valueAssertion = expectSingleValue(connectorObject, filter.getName());
        if (null != valueAssertion) {
            if (!(valueAssertion instanceof Comparable)) {
                throw new IllegalArgumentException("Attribute value " + filter.getName()
                        + " must be comparable! Found" + valueAssertion.getClass());
            }
            final Object filterValue = filter.getValue();
            if (caseIgnore && filterValue instanceof String) {
                if (valueAssertion instanceof String) {
                    result =
                            FilterResult.valueOf(((String) valueAssertion)
                                    .compareToIgnoreCase((String) filterValue) > 0);
                }
            } else if (caseIgnore && filterValue instanceof Character) {
                if (valueAssertion instanceof Character) {
                    result =
                            FilterResult
                                    .valueOf((Character.toLowerCase((Character) valueAssertion))
                                            - (Character.toLowerCase((Character) filterValue)) > 0);
                }
            } else {
                result =
                        FilterResult.valueOf(CollectionUtil.forceCompare(valueAssertion,
                                filterValue) > 0);
            }
        }
        return result;
    }

    public FilterResult visitGreaterThanOrEqualFilter(ConnectorObject connectorObject,
            GreaterThanOrEqualFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        final Object valueAssertion = expectSingleValue(connectorObject, filter.getName());
        if (null != valueAssertion) {
            if (!(valueAssertion instanceof Comparable)) {
                throw new IllegalArgumentException("Attribute value " + filter.getName()
                        + " must be comparable! Found" + valueAssertion.getClass());
            }
            final Object filterValue = filter.getValue();
            if (caseIgnore && filterValue instanceof String) {
                if (valueAssertion instanceof String) {
                    result =
                            FilterResult.valueOf(((String) valueAssertion)
                                    .compareToIgnoreCase((String) filterValue) >= 0);
                }
            } else if (caseIgnore && filterValue instanceof Character) {
                if (valueAssertion instanceof Character) {
                    result =
                            FilterResult
                                    .valueOf((Character.toLowerCase((Character) valueAssertion))
                                            - (Character.toLowerCase((Character) filterValue)) >= 0);
                }
            } else {
                result =
                        FilterResult.valueOf(CollectionUtil.forceCompare(valueAssertion,
                                filterValue) >= 0);
            }
        }
        return result;
    }

    public FilterResult visitLessThanFilter(ConnectorObject connectorObject, LessThanFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        final Object valueAssertion = expectSingleValue(connectorObject, filter.getName());
        if (null != valueAssertion) {
            if (!(valueAssertion instanceof Comparable)) {
                throw new IllegalArgumentException("Attribute value " + filter.getName()
                        + " must be comparable! Found" + valueAssertion.getClass());
            }
            final Object filterValue = filter.getValue();
            if (caseIgnore && filterValue instanceof String) {
                if (valueAssertion instanceof String) {
                    result =
                            FilterResult.valueOf(((String) valueAssertion)
                                    .compareToIgnoreCase((String) filterValue) < 0);
                }
            } else if (caseIgnore && filterValue instanceof Character) {
                if (valueAssertion instanceof Character) {
                    result =
                            FilterResult
                                    .valueOf((Character.toLowerCase((Character) valueAssertion))
                                            - (Character.toLowerCase((Character) filterValue)) < 0);
                }
            } else {
                result =
                        FilterResult.valueOf(CollectionUtil.forceCompare(valueAssertion,
                                filterValue) < 0);
            }
        }
        return result;
    }

    public FilterResult visitLessThanOrEqualFilter(ConnectorObject connectorObject,
            LessThanOrEqualFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        final Object valueAssertion = expectSingleValue(connectorObject, filter.getName());
        if (null != valueAssertion) {
            if (!(valueAssertion instanceof Comparable)) {
                throw new IllegalArgumentException("Attribute value " + filter.getName()
                        + " must be comparable! Found" + valueAssertion.getClass());
            }
            final Object filterValue = filter.getValue();
            if (caseIgnore && filterValue instanceof String) {
                if (valueAssertion instanceof String) {
                    result =
                            FilterResult.valueOf(((String) valueAssertion)
                                    .compareToIgnoreCase((String) filterValue) <= 0);
                }
            } else if (caseIgnore && filterValue instanceof Character) {
                if (valueAssertion instanceof Character) {
                    result =
                            FilterResult
                                    .valueOf((Character.toLowerCase((Character) valueAssertion))
                                            - (Character.toLowerCase((Character) filterValue)) <= 0);
                }
            } else {
                result =
                        FilterResult.valueOf(CollectionUtil.forceCompare(valueAssertion,
                                filterValue) <= 0);
            }
        }
        return result;
    }

    public FilterResult visitNotFilter(ConnectorObject connectorObject, NotFilter filter) {
        switch (filter.getFilter().accept(this, connectorObject)) {
        case FALSE:
            return FilterResult.TRUE;
        case UNDEFINED:
            return FilterResult.UNDEFINED;
        default: // TRUE
            return FilterResult.FALSE;
        }
    }

    public FilterResult visitOrFilter(ConnectorObject connectorObject, OrFilter filter) {
        FilterResult result = FilterResult.FALSE;
        for (final Filter subFilter : filter.getFilters()) {
            final FilterResult r = subFilter.accept(this, connectorObject);
            if (r.ordinal() > result.ordinal()) {
                result = r;
            }
            if (result == FilterResult.TRUE) {
                break;
            }
        }
        return result;
    }

    public FilterResult visitStartsWithFilter(ConnectorObject connectorObject,
            StartsWithFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        String valueAssertion = expectSingleValue(connectorObject, filter.getName(), String.class);
        if (null != valueAssertion) {
            if (caseIgnore) {
                result =
                        FilterResult.valueOf(valueAssertion.toLowerCase(CurrentLocale.get())
                                .startsWith(filter.getValue().toLowerCase(CurrentLocale.get())));
            } else {
                result = FilterResult.valueOf(valueAssertion.startsWith(filter.getValue()));
            }
        }
        return result;
    }

    public FilterResult visitEndsWithFilter(ConnectorObject connectorObject, EndsWithFilter filter) {
        FilterResult result = FilterResult.UNDEFINED;
        String valueAssertion = expectSingleValue(connectorObject, filter.getName(), String.class);
        if (null != valueAssertion) {
            if (caseIgnore) {
                result =
                        FilterResult.valueOf(valueAssertion.toLowerCase(CurrentLocale.get())
                                .endsWith(filter.getValue().toLowerCase(CurrentLocale.get())));
            } else {
                result = FilterResult.valueOf(valueAssertion.endsWith(filter.getValue()));
            }
        }
        return result;
    }

    protected <T> T expectSingleValue(ConnectorObject connectorObject, String attributeName,
            Class<T> expect) {
        T result = null;
        Object o = expectSingleValue(connectorObject, attributeName);
        if (null != o && expect.isAssignableFrom(o.getClass())) {
            result = expect.cast(o);
        }
        return result;
    }

    protected Object expectSingleValue(ConnectorObject connectorObject, String attributeName) {
        Attribute attr = connectorObject.getAttributeByName(attributeName);
        if (null != attr && null != attr.getValue() && attr.getValue().size() == 1) {
            return attr.getValue().get(0);
        }
        return null;
    }
}
