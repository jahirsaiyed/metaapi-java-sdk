package cloud.metaapi.sdk.clients.meta_api.models;

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
     * Flag indicating if trades should be placed as manual trades, or {@code null}. Default is false.
     */
    public boolean manualTrades;
    /**
     * Quote streaming interval in seconds, or {@code null}. Set to 0 in order to receive quotes on each tick.
     * Default value is 2.5 seconds. Intervals less than 2.5 seconds are supported only for G2
     */
    public Double quoteStreamingIntervalInSeconds;
}