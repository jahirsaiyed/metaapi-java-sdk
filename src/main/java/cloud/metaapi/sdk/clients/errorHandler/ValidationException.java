package cloud.metaapi.sdk.clients.errorHandler;

import java.util.List;

/**
 * Represents validation error. Throwing this error results in 400 (Bad Request) HTTP response code.
 */
public class ValidationException extends ApiException {

    /**
     * Represents an item of validation error details
     */
    public static class ErrorDetail {
        /**
         * Name of the parameter to which an error relates
         */
        public String parameter;
        /**
         * Message of the error
         */
        public String message;
    }
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Validation error details
     */
    public List<ErrorDetail> details;

    /**
     * Constructs validation error.
     * @param message error message
     * @param details error data
     */
    public ValidationException(String message, List<ErrorDetail> details) {
        super(message, 400);
        this.details = details;
    }
}