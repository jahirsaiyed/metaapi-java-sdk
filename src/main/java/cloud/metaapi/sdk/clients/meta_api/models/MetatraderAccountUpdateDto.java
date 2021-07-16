package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;
import java.util.Map;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.CopyFactoryRole;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.Extension;

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
  /**
   * API extensions
   */
  public List<Extension> extensions;
  /**
   * Extra information which can be stored together with your account
   */
  public Map<String, Object> metadata;
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