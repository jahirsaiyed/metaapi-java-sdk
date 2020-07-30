package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Represents validation error. Throwing this error results in 400 (Bad Request) HTTP response code.
 */
public class ValidationError extends ApiError {

    private static final long serialVersionUID = 1L;
    
    /**
     * Validation error details
     * TODO: Clarify content. May be this is must be a JSON object
     */
    public String details;

    /**
     * Constructs validation error.
     * @param message error message
     * @param details error data
     */
    public ValidationError(String message, String details) {
        super(message, 400);
        this.details = details;
    }
}