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
 * Portions Copyrighted 2011 Viliam Repan (lazyman)
 *
 * $Id$
 */
package org.forgerock.openicf.csvfile.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Viliam Repan (lazyman)
 */
public class CsvItem {

    private List<String> attributes;

    public CsvItem(List<String> attributes) {
        this.attributes = attributes;
    }

    public List<String> getAttributes() {
        if (attributes == null) {
            attributes = new ArrayList<String>();
        }
        return attributes;
    }

    public String getAttribute(int index) {
        if (index < 0 || attributes == null || attributes.size() <= index) {
            return null;
        }

        return attributes.get(index);
    }

    @Override
    public String toString() {
        return Arrays.toString(attributes.toArray());
    }
}
