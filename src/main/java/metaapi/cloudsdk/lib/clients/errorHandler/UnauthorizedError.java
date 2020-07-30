package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Throwing this error results in 401 (Unauthorized) HTTP response code.
 */
public class UnauthorizedError extends ApiError {

    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs unauthorized error.
     * @param message error message
     */
    public UnauthorizedError(String message) {
        super(message, 401);
    }
}