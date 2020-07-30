package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Represents unexpected error. Throwing this error results in 500 (Internal Error) HTTP response code.
 */
public class InternalError extends ApiError {
    
    private static final long serialVersionUID = 1L;

    /**
     * Constructs unexpected error.
     * @param message error message
     */
    public InternalError(String message) {
        super(message, 500);
    }
}