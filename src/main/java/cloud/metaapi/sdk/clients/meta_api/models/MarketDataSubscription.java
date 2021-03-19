package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Market data subscription
 */
public class MarketDataSubscription {
  /**
   * Subscription type, one of quotes, candles, ticks, or marketDepth
   */
  public String type;
  /**
   * When subscription type is candles, defines the timeframe according to which the
   * candles must be generated. Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m,
   * 15m, 20m, 30m, 1h, 2h, 3h, 4h, 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m,
   * 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn. Can be {@code null}.
   */
  public String timeframe;
  /**
   * Defines how frequently the terminal will stream data to client. If {@code null}, then
   * the value configured in account will be used
   */
  public Integer intervalInMilliseconds;
}