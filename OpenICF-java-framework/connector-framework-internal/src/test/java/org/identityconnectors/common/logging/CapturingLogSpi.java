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
 * Copyright 2026 3A Systems, LLC.
 */
package org.identityconnectors.common.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Logging SPI of this module's tests, selected by the surefire configuration
 * through {@link Log#LOGSPI_PROP}: it logs to standard out like the default
 * {@link StdOutLogger} and also records every message, so that a test can
 * check what was logged.
 */
public class CapturingLogSpi extends StdOutLogger {

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<Entry>();

    private static final class Entry {
        final Class<?> clazz;
        final Log.Level level;
        final String message;

        Entry(Class<?> clazz, Log.Level level, String message) {
            this.clazz = clazz;
            this.level = level;
            this.message = message;
        }
    }

    /**
     * Returns whether this SPI is the one the framework logs through.
     */
    public static boolean isActive() {
        return Log.getSpiClass() == CapturingLogSpi.class;
    }

    /** Forgets every message recorded so far. */
    public static void clear() {
        ENTRIES.clear();
    }

    /**
     * Returns the messages logged so far for {@code clazz} at {@code level}.
     */
    public static List<String> messages(Class<?> clazz, Log.Level level) {
        List<String> messages = new ArrayList<String>();
        for (Entry entry : ENTRIES) {
            if (entry.clazz == clazz && entry.level == level) {
                messages.add(entry.message);
            }
        }
        return messages;
    }

    @Override
    public void log(Class<?> clazz, String methodName, Log.Level level, String message,
            Throwable ex) {
        ENTRIES.add(new Entry(clazz, level, message));
        super.log(clazz, methodName, level, message, ex);
    }
}
