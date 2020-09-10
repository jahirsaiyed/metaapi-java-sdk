package cloud.metaapi.sdk.clients.error_handler;

import java.util.List;

import cloud.metaapi.sdk.clients.models.ValidationDetails;

/**
 * Represents validation error. Throwing this error results in 400 (Bad Request) HTTP response code.
 */
public class ValidationException extends ApiException {

    private static final long serialVersionUID = 1L;
    
    /**
     * Validation error details
     */
    public List<ValidationDetails> details;

    /**
     * Constructs validation error.
     * @param message error message
     * @param details error data
     */
    public ValidationException(String message, List<ValidationDetails> details) {
        super(message, 400);
        this.details = details;
    }
}