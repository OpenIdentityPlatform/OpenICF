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
package org.identityconnectors.ldap.sync.sunds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class LdifParser implements Iterable<LdifParser.Line> {

    private final String ldif;

    public LdifParser(String ldif) {
        this.ldif = ldif;
    }

    public Iterator<Line> iterator() {
        return new LineIterator(getUnfoldedLines());
    }

    private List<String> getUnfoldedLines() {
        String[] lines = ldif.split("\n", -1);
        ArrayList<String> result = new ArrayList<String>(lines.length);
        StringBuilder builder = null;
        for (String line : lines) {
            if (line.startsWith(" ")) {
                String content = line.substring(1);
                if (builder == null) {
                    builder = new StringBuilder(content);
                } else {
                    builder.append(content);
                }
            } else {
                if (builder != null) {
                    result.add(builder.toString());
                }
                builder = new StringBuilder(line);
            }
        }
        if (builder != null) {
            result.add(builder.toString());
        }
        return result;
    }

    private static final class LineIterator implements Iterator<Line> {

        private final Iterator<String> rawLines;
        private Line next;

        public LineIterator(List<String> rawLines) {
            this.rawLines = rawLines.iterator();
        }

        public boolean hasNext() {
            if (next == null) {
                next = getNext();
            }
            return next != null;
        }

        public Line next() {
            if (next == null) {
                next = getNext();
            }
            if (next == null) {
                throw new NoSuchElementException();
            }
            Line result = next;
            next = null;
            return result;
        }

        private Line getNext() {
            while (rawLines.hasNext()) {
                String rawLine = rawLines.next();
                if (rawLine.startsWith("-")) {
                    return Separator.INSTANCE;
                }
                int sepIndex = rawLine.indexOf(':');
                if (sepIndex > 0) {
                    String name = rawLine.substring(0, sepIndex).trim();
                    String value = rawLine.substring(sepIndex + 1).trim();
                    return new NameValue(name, value);
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public abstract static class Line {

    }

    public final static class NameValue extends Line {

        private final String name;
        private final String value;

        public NameValue(String name, String value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "LdifParser$NameValue[name: " + name + "; value: " + value + "]";
        }
    }

    public static final class Separator extends Line {

        final static Separator INSTANCE = new Separator();

        private Separator() {
        }

        @Override
        public String toString() {
            return "LdifParser$Separator";
        }
    }
}
