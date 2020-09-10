package cloud.metaapi.sdk.clients.meta_api;

/**
 * Error which indicates that MetaTrader terminal did not start yet. 
 * You need to wait until account is connected and retry.
 */
public class NotConnectedException extends Exception {

    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs the error
     * @param message error message
     */
    public NotConnectedException(String message) {
        super(message);
    }
}