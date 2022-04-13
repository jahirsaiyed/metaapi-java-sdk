package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;
import java.util.Map;

/**
 * MetaTrader account model
 */
public class MetatraderAccountDto {
  
  /**
   * Account deployment state enum
   */
  public enum DeploymentState { CREATED, DEPLOYING, DEPLOYED, DEPLOY_FAILED, UNDEPLOYING, UNDEPLOYED, UNDEPLOY_FAILED,
    DELETING, DELETE_FAILED, REDEPLOY_FAILED }
  
  /**
   * Terminal and broker connection status enum
   */
  public enum ConnectionStatus { CONNECTED, DISCONNECTED, DISCONNECTED_FROM_BROKER }
  
  /**
   * Account roles for CopyFactory2 application
   */
  public enum CopyFactoryRole { PROVIDER, SUBSCRIBER }
  
  /**
   * Extension model
   */
  public static class Extension {
    /**
     * Extension id
     */
    public String id;
    /**
     * Extension configuration
     */
    public Map<String, Object> configuration;
  }
  
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
  public long magic;
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
   * MetaTrader version
   */
  public int version;
  /**
   * MetaTrader account tags, or {@code null}.
   */
  public List<String> tags;
  /**
   * API extensions
   */
  public List<Extension> extensions;
  /**
   * Extra information which can be stored together with your account
   */
  public Map<String, Object> metadata;
  /**
   * Used to increase the reliability of the account. Allowed values are regular and high. Default is regular
   */
  public String reliability;
  /**
   * 3-character ISO currency code of the account base currency. Default value is USD.
   * The setting is to be used for copy trading accounts which use national currencies
   * only, such as some Brazilian brokers. You should not alter this setting unless you
   * understand what you are doing.
   */
  public String baseCurrency;
  /**
   * Account roles for CopyFactory2 application, or {@code null}
   */
  public List<CopyFactoryRole> copyFactoryRoles;
  /**
   * Number of resource slots to allocate to account, or {@code null}. Allocating extra resource slots
   * results in better account performance under load which is useful for some applications. E.g. if you have many
   * accounts copying the same strategy via CopyFactory API, then you can increase resourceSlots to get a lower trade
   * copying latency. Please note that allocating extra resource slots is a paid option. Default is 1
   */
  public Integer resourceSlots;
}