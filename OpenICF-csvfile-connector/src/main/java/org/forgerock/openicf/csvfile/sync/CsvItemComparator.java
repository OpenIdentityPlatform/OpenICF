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
package org.forgerock.openicf.csvfile.sync;

import org.forgerock.openicf.csvfile.util.CsvItem;
import java.util.Comparator;

/**
 *
 * @author lazyman
 */
class CsvItemComparator implements Comparator<CsvItem> {

    private int index;

    public CsvItemComparator(int index) {
        this.index = index;
    }

    @Override
    public int compare(CsvItem t1, CsvItem t2) {
        String value1 = t1.getAttribute(index);
        String value2 = t2.getAttribute(index);

        return String.CASE_INSENSITIVE_ORDER.compare(value1, value2);
    }
}
