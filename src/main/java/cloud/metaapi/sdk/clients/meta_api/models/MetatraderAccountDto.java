package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

/**
 * MetaTrader account model
 */
public class MetatraderAccountDto {
    
    /**
     * Account deployment state enum
     */
    public enum DeploymentState { CREATED, DEPLOYING, DEPLOYED, UNDEPLOYING, UNDEPLOYED, DELETING }
    
    /**
     * Terminal and broker connection status enum
     */
    public enum ConnectionStatus { CONNECTED, DISCONNECTED, DISCONNECTED_FROM_BROKER }
    
    /**
     * Account unique identifier
     */
    public String _id;
    /**
     * MetaTrader account human-readable name in the MetaApi app
     */
    public String name;
    /**
     * Account type, can be cloud, cloud-g1, cloud-g2 or self-hosted. Cloud and cloud-g2 are aliases.
     */
    public String type;
    /**
     * MetaTrader account number
     */
    public String login;
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
     * Account deployment state
     */
    public DeploymentState state;
    /**
     * Terminal and broker connection status
     */
    public ConnectionStatus connectionStatus;
    /**
     * Authorization token to be used for accessing single account data. Intended to be used in browser API.
     */
    public String accessToken;
    /**
     * Flag indicating if trades should be placed as manual trades, or {@code null}.
     * Default is false. Supported on G2 only
     */
    public Boolean manualTrades;
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