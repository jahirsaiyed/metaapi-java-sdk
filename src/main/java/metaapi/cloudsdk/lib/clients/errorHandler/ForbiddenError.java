package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Throwing this error results in 403 (Forbidden) HTTP response code.
 */
public class ForbiddenError extends ApiError {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs forbidden error.
     * @param message error message
     */
    public ForbiddenError(String message) {
        super(message, 403);
    }
}