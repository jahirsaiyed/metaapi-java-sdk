package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Represents conflict error. Throwing this error results in 409 (Conflict) HTTP response code.
 */
public class ConflictError extends ApiError {

    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs conflict error.
     * @param message error message
     */
    public ConflictError(String message) {
        super(message, 409);
    }
}