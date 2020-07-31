package metaapi.cloudsdk.lib.clients.models;

/**
 * Updated MetaTrader account data
 */
public class MetatraderAccountUpdateDto {
    /**
     * MetaTrader account human-readable name in the MetaApi app
     */
    public String name;
    /**
     * MetaTrader account password. The password can be either investor password for read-only
     * access or master password to enable trading features. Required for cloud account
     */
    public String password;
    /**
     * MetaTrader server which hosts the account
     */
    public String server;
    /**
     * Synchronization mode, can be automatic or user. See
     * https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details.
     */
    public String synchronizationMode;
}