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

package org.forgerock.openicf.misc.scriptedcommon

import org.identityconnectors.framework.common.objects.filter.AndFilter
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter
import org.identityconnectors.framework.common.objects.filter.ContainsFilter
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter
import org.identityconnectors.framework.common.objects.filter.EqualsFilter
import org.identityconnectors.framework.common.objects.filter.Filter
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.LessThanFilter
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter
import org.identityconnectors.framework.common.objects.filter.NotFilter
import org.identityconnectors.framework.common.objects.filter.OrFilter
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter

/**
 * An AbstractFilterVisitor implements Visitor Pattern for {@link Filter}.
 *
 * @author Laszlo Hordos
 */
abstract class AbstractFilterVisitor<P, R> implements FilterVisitor<P, R> {


    public R accept(P parameter, Filter filter) {
        use(EnhancedFilter) {
            if (filter instanceof AndFilter) {
                ((AndFilter) filter).accept(this, parameter);
            } else if (filter instanceof ContainsAllValuesFilter) {
                ((ContainsAllValuesFilter) filter).accept(this, parameter);
            } else if (filter instanceof ContainsFilter) {
                ((ContainsFilter) filter).accept(this, parameter);
            } else if (filter instanceof EndsWithFilter) {
                ((EndsWithFilter) filter).accept(this, parameter);
            } else if (filter instanceof EqualsFilter) {
                ((EqualsFilter) filter).accept(this, parameter);
            } else if (filter instanceof GreaterThanFilter) {
                ((GreaterThanFilter) filter).accept(this, parameter);
            } else if (filter instanceof GreaterThanOrEqualFilter) {
                ((GreaterThanOrEqualFilter) filter).accept(this, parameter);
            } else if (filter instanceof LessThanFilter) {
                ((LessThanFilter) filter).accept(this, parameter);
            } else if (filter instanceof LessThanOrEqualFilter) {
                ((LessThanOrEqualFilter) filter).accept(this, parameter);
            } else if (filter instanceof NotFilter) {
                ((NotFilter) filter).accept(this, parameter);
            } else if (filter instanceof OrFilter) {
                ((OrFilter) filter).accept(this, parameter);
            } else if (filter instanceof StartsWithFilter) {
                ((StartsWithFilter) filter).accept(this, parameter);
            }
        }
    }

    static class EnhancedFilter {
        static <P, R> R accept(AndFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(ContainsAllValuesFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(ContainsFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(EndsWithFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(EqualsFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(GreaterThanFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(GreaterThanOrEqualFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(LessThanFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(LessThanOrEqualFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(NotFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(OrFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }

        static <P, R> R accept(StartsWithFilter self, FilterVisitor<P, R> visitor, P parameter) {
            return visitor.visit(parameter, self);
        }
    }
}
