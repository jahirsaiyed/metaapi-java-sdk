package cloud.metaapi.sdk.meta_api;

public class InvalidSynchronizationModeException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception
     * @param account MetaTrader account
     */
    public InvalidSynchronizationModeException(MetatraderAccount account) {
        super("Your acount " + account.getName() + " " + account.getId() + " was created with " 
            + account.getSynchronizationMode() + " synchronization mode which does not supports the streaming API. "
            + "Thus please update your account to 'user' synchronization mode if to invoke this method. See "
            + "https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details");
    }
}