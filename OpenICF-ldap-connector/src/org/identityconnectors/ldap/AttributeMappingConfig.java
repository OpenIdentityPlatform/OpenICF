package org.identityconnectors.ldap;

public class AttributeMappingConfig {

    private final String fromAttribute;
    private final String toAttribute;

    public AttributeMappingConfig(String fromAttribute, String toAttribute) {
        this.fromAttribute = fromAttribute;
        this.toAttribute = toAttribute;
    }

    public String getFromAttribute() {
        return fromAttribute;
    }

    public String getToAttribute() {
        return toAttribute;
    }

    public int hashCode() {
        return fromAttribute.hashCode() + toAttribute.hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof AttributeMappingConfig) {
            AttributeMappingConfig that = (AttributeMappingConfig) o;
            if (!this.fromAttribute.equals(that.fromAttribute)) {
                return false;
            }
            if (!this.toAttribute.equals(that.toAttribute)) {
                return false;
            }
            return true;
        }
        return false;
    }
}
