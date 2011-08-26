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
import static org.forgerock.openicf.csvfile.util.Utils.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.forgerock.openicf.csvfile.CSVFileConfiguration;

/**
 *
 * @author lazyman
 */
public class InMemoryDiff {

    private CSVFileConfiguration configuration;
    private Pattern linePattern;
    private File oldFile;
    private File newFile = null;

    public InMemoryDiff(File oldFile, File newFile, Pattern linePattern, CSVFileConfiguration configuration) {
        notNullArgument(newFile, "newFile");
        notNullArgument(linePattern, "linePattern");
        notNullArgument(configuration, "configuration");

        this.oldFile = oldFile;
        this.newFile = newFile;
        this.configuration = configuration;
        this.linePattern = linePattern;
    }

    @SuppressWarnings("unchecked")
    public List<Change> diff() throws DiffException {
        List<Change> changes = new ArrayList<Change>();
        try {
            if (oldFile != null) {
                testHeaders(newFile, oldFile);
            }

            RecordSet newRecordSet = createRecordSet(newFile);
            int uidIndex = newRecordSet.getHeaders().indexOf(configuration.getUniqueAttribute());
            if (oldFile != null) {
                RecordSet oldRecordSet = createRecordSet(oldFile);
                //compare records from both record sets
                List<CsvItem> newList = new ArrayList(newRecordSet.getRecords());
                List<CsvItem> oldList = new ArrayList(oldRecordSet.getRecords());

                changes.addAll(findChanges(uidIndex, newRecordSet.getHeaders(), newList, oldList));
            } else {
                //everything will be add
                Set<CsvItem> items = newRecordSet.getRecords();
                Iterator<CsvItem> iterator = items.iterator();
                Change change = null;
                while (iterator.hasNext()) {
                    CsvItem item = iterator.next();

                    change = new Change(item.getAttribute(uidIndex), Change.Type.CREATE,
                            newRecordSet.getHeaders(), item.getAttributes());
                    changes.add(change);
                }
            }
        } catch (DiffException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DiffException("Can't create csv diff, reason: " + ex.getMessage(), ex);
        }

        return changes;
    }

    private List<Change> findChanges(int uidIndex, List<String> headers, List<CsvItem> newList, List<CsvItem> oldList) {
        List<Change> changes = new ArrayList<Change>();
        Change change = null;
        int newIndex = 0, oldIndex = 0;
        String oldUid, newUid;

        outer:
        for (; newIndex < newList.size(); newIndex++) {
            CsvItem newItem = newList.get(newIndex);
            newUid = newItem.getAttribute(uidIndex);
            for (; oldIndex < oldList.size();) {
                CsvItem oldItem = oldList.get(oldIndex);
                oldUid = oldItem.getAttribute(uidIndex);

                int compare = String.CASE_INSENSITIVE_ORDER.compare(newUid, oldUid);
                if (compare < 0) {
                    CsvItem item = newList.get(newIndex);
                    change = new Change(item.getAttribute(uidIndex), Change.Type.CREATE,
                            headers, item.getAttributes());
                    changes.add(change);
                    break;
                } else if (compare > 0) {
                    CsvItem item = oldList.get(oldIndex);
                    change = new Change(item.getAttribute(uidIndex), Change.Type.DELETE,
                            headers, item.getAttributes());
                    changes.add(change);
                    oldIndex++;
                } else {
                    if (!isEqual(newItem, oldItem)) {
                        change = new Change(newItem.getAttribute(uidIndex), Change.Type.MODIFY,
                                headers, newItem.getAttributes());
                        changes.add(change);
                    }
                    oldIndex++;
                    break;
                }

                if (oldIndex >= oldList.size()) {
                    break outer;
                }
            }
        }

        for (; newIndex < newList.size(); newIndex++) {
            CsvItem item = newList.get(newIndex);
            change = new Change(item.getAttribute(uidIndex), Change.Type.CREATE,
                    headers, item.getAttributes());
            changes.add(change);
        }
        for (; oldIndex < oldList.size(); oldIndex++) {
            CsvItem item = oldList.get(oldIndex);
            change = new Change(item.getAttribute(uidIndex), Change.Type.DELETE,
                    headers, item.getAttributes());
            changes.add(change);
        }

        return changes;
    }

    private boolean isEqual(CsvItem item1, CsvItem item2) {
        return Arrays.equals(item1.getAttributes().toArray(), item2.getAttributes().toArray());
    }

    private RecordSet createRecordSet(File file) throws IOException, DiffException {
        RecordSet recordSet = null;

        BufferedReader reader = null;
        try {
            reader = createReader(file, configuration);
            List<String> headers = readHeader(reader, linePattern, configuration);
            int index = headers.indexOf(configuration.getUniqueAttribute());
            if (index < 0 || index >= headers.size()) {
                throw new DiffException("Header in '" + file.getAbsolutePath()
                        + "' doesn't contain unique attribute '" + configuration.getUniqueAttribute()
                        + "' as defined in configuration.");
            }
            Set<CsvItem> set = new TreeSet<CsvItem>(new CsvItemComparator(index));
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (isEmptyOrComment(line)) {
                    continue;
                }

                set.add(new CsvItem(parseValues(line, linePattern, configuration)));
            }

            recordSet = new RecordSet(headers, set);
        } finally {
            if (reader != null) {
                closeReader(reader, null);
            }
        }

        return recordSet;
    }

    private void testHeaders(File newFile, File oldFile) throws IOException, DiffException {
        List<String> newHeaders = null;
        List<String> oldHeaders = null;

        BufferedReader newReader = null;
        BufferedReader oldReader = null;
        try {
            newReader = createReader(newFile, configuration);
            newHeaders = readHeader(newReader, linePattern, configuration);

            oldReader = createReader(oldFile, configuration);
            oldHeaders = readHeader(oldReader, linePattern, configuration);
        } finally {
            closeReader(newReader, null);
            closeReader(oldReader, null);
        }

        if (newHeaders == null || oldHeaders == null || !Arrays.equals(newHeaders.toArray(),
                oldHeaders.toArray())) {
            throw new DiffException("Headers in files '" + newFile.getPath()
                    + "' and '" + oldFile.getPath() + "' doesn't match.");
        }
    }
}
