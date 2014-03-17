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

package org.forgerock.openicf.misc.scriptedcommon;

/**
 * A Logger is another logging abstraction.
 *
 * @author Laszlo Hordos
 */
public interface Logger {

    public boolean isDebug();

    public boolean isInfo();

    public boolean isWarning();

    public boolean isError();

    public void debug(Throwable ex, String format, Object... args);

    public void debugLocale(Throwable ex, String key, Object... args);

    public void debugLocale(Throwable ex, String key, String defaultMessage, Object... args);

    public void info(Throwable ex, String format, Object... args);

    public void infoLocale(Throwable ex, String key, Object... args);

    public void infoLocale(Throwable ex, String key, String defaultMessage, Object... args);

    public void warn(Throwable ex, String format, Object... args);

    public void warnLocale(Throwable ex, String key, Object... args);

    public void warnLocale(Throwable ex, String key, String defaultMessage, Object... args);

    public void error(Throwable ex, String format, Object... args);

    public void errorLocale(Throwable ex, String key, Object... args);

    public void errorLocale(Throwable ex, String key, String defaultMessage, Object... args);

    public void debug(String format, Object... args);

    public void debugLocale(String key, Object... args);

    public void debugLocale(String key, String defaultMessage, Object... args);

    public void info(String format, Object... args);

    public void infoLocale(String key, Object... args);

    public void infoLocale(String key, String defaultMessage, Object... args);

    public void warn(String format, Object... args);

    public void warnLocale(String key, Object... args);

    public void warnLocale(String key, String defaultMessage, Object... args);

    public void error(String format, Object... args);

    public void errorLocale(String key, Object... args);

    public void errorLocale(String key, String defaultMessage, Object... args);
}
