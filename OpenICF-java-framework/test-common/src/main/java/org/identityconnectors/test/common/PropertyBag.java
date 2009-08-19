package org.identityconnectors.test.common;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Property bag is readonly set of properties. Properties in bag are kept as objects and can be accessed using name and its type .
 * @author kitko
 */
public final class PropertyBag {

    private final Map<String, Object> bag;

    PropertyBag(Map<String, Object> bag) {
        this.bag = new HashMap<String, Object>(bag);
    }

    /**
     * Gets property by name and type. If no property exists with the name IllegalArgumentException is thrown.
     * @param <T> Type of property
     * @param name Name of Property
     * @param type Type of property
     * @return value of property in bag, also null if there is property named Name with null value
     * @throws IllegalArgumentException if no property with name is stored in bag
     * @throws ClassCastException if property with the name has not compatible type
     */
    public <T> T getProperty(String name, Class<T> type) {
        if (!bag.containsKey(name)) {
            throw new IllegalArgumentException(MessageFormat.format("Property named [{0}] not found in bag", name));
        }
        return castValue(name, type);
    }

    /**
     * Here we just try provide nicer exception to client when casting to type fails.
     */
    private <T> T castValue(String name, Class<T> type) {
        Object value = bag.get(name);
        // This means property has really null value in bag, so return null
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(MessageFormat.format("Property named [{0}] is of type [{1}] but expected type was [{2}]", name, value
                    .getClass(), type));
        }
        return type.cast(value);

    }

    /**
     * Gets property by name and type, returning default when no property with the name is found in bag.
     * @param <T> Type of property
     * @param name Name of property
     * @param type Type of property
     * @param def Default value returned when no property with the name is found in bag
     * @return value of property in bag or default when not found
     */
    public <T> T getProperty(String name, Class<T> type, T def) {
        if (!bag.containsKey(name)) {
            return def;
        }
        return castValue(name, type);
    }

    /**
     * Retrieves String property. Method calls just {@link #getProperty(String, Class)} with String.class type, it does not try to convert value from
     * the bag to String.
     * @param Name
     * @return String value
     */
    public String getStringProperty(String name) {
        return getProperty(name, String.class);
    }

    @Override
    public String toString() {
        return bag.toString();
    }

    Map<String, Object> toMap() {
        return bag;
    }

}
