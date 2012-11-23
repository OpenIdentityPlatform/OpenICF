package org.forgerock.openicf.csvfile.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author Viliam Repan (lazyman)
 */
public class TokenFileNameFilter implements FilenameFilter {

    private String csvFileName;

    public TokenFileNameFilter(String csvFileName) {
        this.csvFileName = csvFileName;
    }

    @Override
    public boolean accept(File parent, String fileName) {
        File file = new File(parent, fileName);
        if (file.isDirectory()) {
            return false;
        }

        if (fileName.matches(csvFileName.replaceAll("\\.", "\\\\.") + "\\.[0-9]{13}$")) {
            return true;
        }

        return false;
    }
}
