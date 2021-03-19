package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader candle
 */
public class MetatraderCandle {
  /**
   * Symbol (e.g. currency pair or an index)
   */
  public String symbol;
  /**
   * Timeframe candle was generated for, e.g. 1h. One of 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m,
   * 15m, 20m, 30m, 1h, 2h, 3h, 4h, 6h, 8h, 12h, 1d, 1w, 1mn
   */
  public String timeframe;
  /**
   * Candle opening time
   */
  public IsoTime time;
  /**
   * Candle opening time, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format
   */
  public String brokerTime;
  /**
   * Open price
   */
  public double open;
  /**
   * High price
   */
  public double high;
  /**
   * Low price
   */
  public double low;
  /**
   * Close price
   */
  public double close;
  /**
   * Tick volume, i.e. number of ticks inside the candle
   */
  public double tickVolume;
  /**
   * Spread in points
   */
  public double spread;
  /**
   * Trade volume
   */
  public double volume;
}