package org.forgerock.openicf.csvfile.util;

import org.identityconnectors.framework.common.exceptions.ConnectorException;

/**
 * @author Viliam Repan (lazyman)
 */
public class CSVSchemaException extends ConnectorException {

    private static final long serialVersionUID = 1L;

    public CSVSchemaException(String message) {
        super(message);
    }

    public CSVSchemaException(Throwable originalException) {
        super(originalException);
    }

    public CSVSchemaException(String message, Throwable originalException) {
        super(message, originalException);
    }
}
