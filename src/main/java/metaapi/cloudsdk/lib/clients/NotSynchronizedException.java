package metaapi.cloudsdk.lib.clients;

/**
 * Error which indicates that MetaApi MetaTrader account was not synchronized yet. See
 * https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details
 */
public class NotSynchronizedException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs the error
     * @param message error message
     */
    public NotSynchronizedException(String message) {
        super(message + ". See https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details");
    }	
}