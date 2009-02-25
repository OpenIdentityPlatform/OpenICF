/*  +---------------------------------------------------+
 *  ----------- Contract Tests configuration ------------
 *  +---------------------------------------------------+ 
 */
import org.identityconnectors.contract.data.groovy.Lazy
import org.identityconnectors.common.security.GuardedString

// Connector configuration
connector=Lazy.get("configurations.oracle")

// Oracle NUMBER type is returned as BigDecimal
// NUMBER(x) is returned as BigDecimal without decimal part
// BigDecimal 15.00 is not equal to 15
// BigDecimal default value is generated with decimal part
AGE=Lazy.random("#####", java.math.BigDecimal.class)
modified.AGE=Lazy.random("#####", java.math.BigDecimal.class)
SALARY=Lazy.random("#####", java.math.BigDecimal.class)
modified.SALARY=Lazy.random("#####", java.math.BigDecimal.class)

// Oracle returns BigDecimal insteadof Integer
testsuite.Schema.AGE.attribute.__ACCOUNT__.oclasses= [
    type: java.math.BigDecimal.class, 
    readable: true,
    writable: true,  
    required: false, 
    multiValue: false,
    returnedByDefault: true
]
