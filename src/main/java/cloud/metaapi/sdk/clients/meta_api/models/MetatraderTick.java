package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader tick data
 */
public class MetatraderTick {
  /**
   * Symbol (e.g. a currency pair or an index)
   */
  public String symbol;
  /**
   * Time
   */
  public IsoTime time;
  /**
   *  Time, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format
   */
  public String brokerTime;
  /**
   * Bid price, or {@code null}
   */
  public Double bid;
  /**
   * Ask price, or {@code null}
   */
  public Double ask;
  /**
   * Last deal price, or {@code null}
   */
  public Double last;
  /**
   * Volume for the current last deal price, or {@code null}
   */
  public Double volume;
  /**
   * Is tick a result of buy or sell deal, one of buy or sell
   */
  public String side;
}