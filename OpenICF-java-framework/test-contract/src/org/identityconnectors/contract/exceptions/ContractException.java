package org.identityconnectors.contract.exceptions;

/**
 * Generic Contract Tests exception. Base class for all contract tests exceptions.
 */
public class ContractException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractException() {
        super();
    }

    /**
     * Sets a message for the {@link Exception}.
     *  
     * @param message
     *            passed to the {@link RuntimeException} message.
     */
    public ContractException(String message) {
        super(message);
    }

    /**
     * Sets the stack trace to the original exception, so this exception can
     * masquerade as the original only be a {@link RuntimeException}.
     * 
     * @param originalException
     *            the original exception adapted to {@link RuntimeException}.
     */
    public ContractException(Throwable originalException) {
        super(originalException);
    }

    /**
     * Sets the stack trace to the original exception, so this exception can
     * masquerade as the original only be a {@link RuntimeException}.
     * 
     * @param message
     * @param originalException
     *            the original exception adapted to {@link RuntimeException}.
     */
    public ContractException(String message, Throwable originalException) {
        super(message, originalException);
    }

    /**
     * Re-throw the original exception.
     * 
     * @throws Exception
     *             throws the original passed in the constructor.
     */
    public void rethrow() throws Throwable {
        throw (getCause() == null) ? this : getCause();
    }

    /**
     * If {@link Exception} parameter passed in is a {@link RuntimeException} it
     * is simply returned. Otherwise the {@link Exception} is wrapped in a
     * <code>ContractException</code> and returned.
     * 
     * @param ex
     *            Exception to wrap or cast and return.
     * @return a <code>RuntimeException</code> that either 
     *           <i>is</i> the specified exception
     *            or <i>contains</i> the specified exception. 
     */
    public static RuntimeException wrap(Throwable ex) {
        // make sure to just throw Errors don't return them..
        if (ex instanceof Error) {
            throw (Error) ex;
        }
        // don't bother to wrap a exception that is already a runtime..
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new ContractException(ex);
    }
}
