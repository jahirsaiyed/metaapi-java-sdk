package metaapi.cloudsdk.lib.clients.models;

/**
 * MetaTrader account model
 */
public class MetatraderAccountDto {
    
    /**
     * Account deployment state enum
     */
    public enum DeploymentState { CREATED, DEPLOYING, DEPLOYED, UNDEPLOYING, UNDEPLOYED, DELETING }
    
    /**
     * Terminal & broker connection status enum
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
     * Account type, can be cloud or self-hosted
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
    /**
     * Account deployment state
     */
    public DeploymentState state;
    /**
     * Terminal & broker connection status
     */
    public ConnectionStatus connectionStatus;
    /**
     * Authorization token to be used for accessing single account data. Intended to be used in browser API.
     */
    public String accessToken;
}