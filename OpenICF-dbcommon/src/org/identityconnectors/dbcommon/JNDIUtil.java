/**
 * 
 */
package org.identityconnectors.dbcommon;

import java.util.Hashtable;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.objects.ConnectorMessages;

/**
 * Common utility methods regarding JNDI
 * @author kitko
 *
 */
public abstract class JNDIUtil {
    /** */
	private JNDIUtil() {
        //empty
    }
	/**	 */
	public static final String INVALID_JNDI_ENTRY = "invalid.jndi.entry"; 
	
	/**
	 * Parses arrays of string as entries of properties. Each entry must be in form
	 * <code>key=value</code>. We use <strong>=</strong> as only separator and treat only first occurrence of =.
	 * Blank entries are skipped.
	 * @param entries could be null or size = 0
	 * @param messages the error messages from the configuration resource bundle
	 * @return hashtable of given entries
	 * @throws IllegalArgumentException when there is any error in format of any entry
	 */
	public static Hashtable<String,String> arrayToHashtable(String[] entries, ConnectorMessages messages){
		Hashtable<String, String> result = new Hashtable<String, String>();
        if (entries != null) {
            for (String entry : entries) {
                if (StringUtil.isNotBlank(entry)) {
                    int firstEq = entry.indexOf('=');
                    if (firstEq == -1) {
                        throwFormatException(messages, entry, "Invalid value in JNDI entry");
                    }
                    if (firstEq == 0) {
                        throwFormatException(messages, entry, "First character cannot be =");
                    }
                    final String key = entry.substring(0, firstEq);
                    final String value = firstEq == entry.length() - 1 ? null : entry.substring(firstEq + 1);
                    result.put(key, value);
                }
            }
        }
		return result;
	}
	
	private static void throwFormatException(ConnectorMessages messages,String entry,String defaultMsg){
		String msg = null;
		if(messages == null){
			msg = defaultMsg + " : " + entry;
		}
		else{
			msg = messages.format(INVALID_JNDI_ENTRY,INVALID_JNDI_ENTRY + " : " + entry,entry);
		}
		throw new IllegalArgumentException(msg);
	}
	

}
