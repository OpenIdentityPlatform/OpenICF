package org.identityconnectors.common.l10n;

import java.util.Locale;

/**
 * Thread local variable that impacts localization
 * of all messages in the connector framework. This is 
 * roughly equivalent to .Net's Thread.CurrentCulture. Note
 * that this is an inheritable thread local so it
 * is automatically inherited from parent to child thread.
 * Of course, if the child thread is part of a thread
 * pool, you will still need to manually propagate
 * from parent to child.
 */
public class CurrentLocale {
    private static final InheritableThreadLocal<Locale>
        _value = new InheritableThreadLocal<Locale>();
    /**
     * Sets the locale for the current thread.
     * @param locale The locale to use.
     */
    public static void set(Locale locale) {
        _value.set(locale);
    }
    
    /**
     * Clears the locale for the current thread.
     */
    public static void clear() {
        set(null);
    }
    
    /**
     * Gets the locale from the current thread.
     * Returns <code>Locale.getDefault</code> if
     * no locale is specified.
     * @return the locale from the current thread.
     */
    public static Locale get() {
        Locale rv = _value.get();
        return rv == null ? Locale.getDefault() : rv;
    }
    
    /**
     * Returns true iff a thread-local locale is specified
     * on the current thread. 
     * @return true iff a thread-local locale is specified
     * on the current thread.
     */
    public static boolean isSet() {
        return _value.get() != null;
    }
}
