package org.identityconnectors.dbcommon;

import java.util.*;

/**
 * Resolver of properties in UNIX/ant style
 * @author kitko
 *
 */
public class PropertiesResolver {
	
	private PropertiesResolver(){}
	
	/** Resolves properties
	 * @param properties properties we want to resolve
	 * @param resolvedProperties already known properties
	 * @return resolved properties
	 */
	public static Properties resolveProperties(Properties properties,Properties resolvedProperties) {
		if (properties == null) {
			return null;
		}
		return resolveProperties(copyProperties(properties),copyProperties(resolvedProperties),new HashSet<String>(5));
	}
	
	/**
	 * Resolve properties containing already known values
	 * @param properties
	 * @return resolved properties
	 */
	public static Properties resolveProperties(Properties properties){
		if (properties == null) {
			return null;
		}
		Properties copy = copyProperties(properties);
		return resolveProperties(copy,copy,new HashSet<String>(5));
	}
	

	private static Properties resolveProperties(Properties properties,Properties resolvedProperties,Set<String> justResolving) {
		for(Map.Entry<Object,Object> entry : properties.entrySet()){
			String value = (String) entry.getValue();
			if(value.contains("$")){
				value = resolveName((String) entry.getKey(),properties,resolvedProperties,justResolving);
				entry.setValue(value);
			}
		}
		return properties;
	}

	private static String resolveName(String name,Properties properties,Properties resolvedProperties,Set<String> justResolving) {
		String value = null;
		if(!justResolving.isEmpty()){
			value = resolvedProperties.getProperty(name);
		}
		if (value != null) {
			return value;
		}
		if (justResolving.contains(name)) {
			value = "RECURSION";
			return value;
		}
		value = properties.getProperty(name);
		if (value == null) {
			value = "NOT_RESOLVED";
			return value;
		}
		else {
			justResolving.add(name);
			value = resolveValue(value,properties,resolvedProperties,justResolving);
			justResolving.remove(name);
			resolvedProperties.setProperty(name, value);
			return value;
		}
	}

	private static String resolveValue(String value,Properties properties,Properties resolvedProperties,Set<String> justResolving) {
		if (value == null) {
			return null;
		}
		int index = 0;
		int length = value.length();
		StringBuffer result = new StringBuffer();
		while (index >= 0 && index < length) {
			int varStart = value.indexOf("${", index);
			if (varStart >= 0) {
				int varEnd = value.indexOf('}', varStart);
				if (varEnd >= 0) {
					String varSubstring = value.substring(varStart, varEnd + 1);
					String varName = varSubstring.substring(2, varSubstring.length() - 1);
					String varValue = resolveName(varName,properties,resolvedProperties,justResolving);
					if ("NOT_RESOLVED".equals(value)){
						varValue = varSubstring;
					}
					else if("RECUSRION".equals(value)){
						varValue = "${RECURSION!!!_" + varName + "}";
					}
					result.append(value.substring(index, varStart));
					result.append(varValue);
					index = varEnd + 1;
				}
				else {
					result.append(value.substring(index, value.length()));
					index = value.length();
				}
			}
			else {
				result.append(value.substring(index, value.length()));
				index = value.length();
			}
		}
		return result.toString();
	}
	
	
	private static Properties copyProperties(Properties properties){
		Properties result = new Properties();
		if(properties == null){
			return result; 
		}
		for(Map.Entry<?,?> entry : properties.entrySet()){
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
	
	
}
