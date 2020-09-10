package cloud.metaapi.sdk.clients.meta_api;

/**
 * Error which indicates that a trade have failed
 */
public class TradeException extends Exception {

    private static final long serialVersionUID = 1L;
    
    /**
     * Numeric error code
     */
    public int numericCode;
    /**
     * String error code
     */
    public String stringCode;
    
    /**
     * Constructs the timeout error
     * @param message error message
     * @param numericCode numeric error code
     * @param stringCode string error code
     */
    public TradeException(String message, int numericCode, String stringCode) {
        super(message);
        this.numericCode = numericCode;
        this.stringCode = stringCode;
    }
}