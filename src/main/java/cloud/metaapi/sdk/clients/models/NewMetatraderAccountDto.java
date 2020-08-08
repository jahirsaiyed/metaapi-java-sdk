package cloud.metaapi.sdk.clients.models;

/**
 * New MetaTrader account model
 */
public class NewMetatraderAccountDto {
    /**
     * MetaTrader account human-readable name in the MetaApi app
     */
    public String name;
    /**
     * Account type, can be cloud or self-hosted
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
     * Synchronization mode, can be automatic or user. See
     * https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details.
     */
    public String synchronizationMode;
    /**
     * Id of the account's provisioning profile
     */
    public String provisioningProfileId;
    /**
     * Algorithm used to parse your broker timezone. Supported values are icmarkets for
     * America/New_York DST switch and roboforex for EET DST switch (the values will be changed soon)
     */
    public String timeConverter;
    /**
     * Application name to connect the account to. Currently allowed values are MetaApi and AgiliumTrade
     */
    public String application;
    /**
     * MetaTrader magic to place trades using
     */
    public int magic;
}
