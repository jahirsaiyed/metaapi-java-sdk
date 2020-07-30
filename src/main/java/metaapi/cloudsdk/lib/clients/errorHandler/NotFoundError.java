package metaapi.cloudsdk.lib.clients.errorHandler;

/**
 * Throwing this error results in 404 (Not Found) HTTP response code.
 */
public class NotFoundError extends ApiError {

    private static final long serialVersionUID = 1L;

    /**
     * Represents NotFoundError.
     * @param message error message
     */
    public NotFoundError(String message) {
        super(message, 404);
    }
}