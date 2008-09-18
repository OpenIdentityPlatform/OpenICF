package org.identityconnectors.contract.data.macro;

import java.util.HashMap;
import java.util.Map;

public class MacroAliases {
    public static final Map<String,Object[]> ALIASES = new HashMap<String, Object[]>();
    
    static {
        ALIASES.put("INTEGER", new Object[] {"OBJECT", "java.lang.Integer"});
        ALIASES.put("LONG", new Object[] {"OBJECT", "java.lang.Long"});
        ALIASES.put("FLOAT", new Object[] {"OBJECT", "java.lang.Float"});
        ALIASES.put("DOUBLE", new Object[] {"OBJECT", "java.lang.Double"});
        ALIASES.put("BIGDECIMAL", new Object[] {"OBJECT", "java.math.BigDecimal"});
        ALIASES.put("BIGINTEGER", new Object[] {"OBJECT", "java.math.BigInteger"});
        ALIASES.put("BOOLEAN", new Object[] {"OBJECT", "java.lang.Boolean"});
        ALIASES.put("FILE", new Object[] {"OBJECT", "java.io.File"});
        ALIASES.put("URI", new Object[] {"OBJECT", "java.net.URI"});
    }
}
