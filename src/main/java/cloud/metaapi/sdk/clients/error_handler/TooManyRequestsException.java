package cloud.metaapi.sdk.clients.error_handler;

/**
 * Represents too many requests error. Throwing this error results in 429 (Too Many Requests) HTTP response code.
 */
public class TooManyRequestsException extends ApiException {

    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs too many requests error.
     * @param message error message
     */
    public TooManyRequestsException(String message) {
        super(message, 429);
    }
}