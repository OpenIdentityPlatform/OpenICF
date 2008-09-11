package org.identityconnectors.contract.exceptions;

/**
 * Exception thrown when the value for a key cannot be resolved.
 */
public class ObjectNotFoundException extends ContractException {

    private static final long serialVersionUID = 1L;
    
    public ObjectNotFoundException() {
        super();
    }

    public ObjectNotFoundException(String message) {
        super(message);
    }

    public ObjectNotFoundException(Throwable t) {
        super(t);
    }

    public ObjectNotFoundException(String message, Throwable t) {
        super(message, t);
    }
}
