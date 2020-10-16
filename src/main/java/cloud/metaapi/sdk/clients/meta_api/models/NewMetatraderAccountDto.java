package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

/**
 * New MetaTrader account model
 */
public class NewMetatraderAccountDto {
    /**
     * MetaTrader account human-readable name in the MetaApi app
     */
    public String name;
    /**
     * Account type, can be cloud, cloud-g1, cloud-g2 or self-hosted. cloud-g2 and cloud are aliases. 
     * When you create MT5 cloud account the type is automatically converted to cloud-g1 because MT5 G2 support
     * is still experimental. You can still create MT5 G2 account by setting type to cloud-g2.
     */
    public String type;
    /**
     * MetaTrader account number
     */
    public String login;
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
     * Id of the account's provisioning profile
     */
    public String provisioningProfileId;
    /**
     * Application name to connect the account to. Currently allowed values are MetaApi and AgiliumTrade
     */
    public String application;
    /**
     * MetaTrader magic to place trades using
     */
    public int magic;
    /**
     * Flag indicating if trades should be placed as manual trades, or {@code null}. Default is false.
     */
    public boolean manualTrades;
    /**
     * Quote streaming interval in seconds, or {@code null}. Set to 0 in order to receive quotes on each tick.
     * Default value is 2.5 seconds. Intervals less than 2.5 seconds are supported only for G2
     */
    public Double quoteStreamingIntervalInSeconds;
    /**
     * MetaTrader account tags, or {@code null}.
     */
    public List<String> tags;
}
