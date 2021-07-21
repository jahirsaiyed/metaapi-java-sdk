package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.meta_api.LatencyListener.SymbolPriceTimestamps;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader symbol price. Contains current price for a symbol (see
 * https://metaapi.cloud/docs/client/models/metatraderSymbolPrice/)
 */
public class MetatraderSymbolPrice {
    /**
     * Symbol (e.g. a currency pair or an index)
     */
    public String symbol;
    /**
     * Bid price
     */
    public double bid;
    /**
     * Ask price
     */
    public double ask;
    /**
     * Tick value for a profitable position
     */
    public double profitTickValue;
    /**
     * Tick value for a losing position
     */
    public double lossTickValue;
    /**
     * Current exchange rate of account currency into account base currency (USD if you
     * did not override it), or {@code null}
     */
    public Double accountCurrencyExchangeRate;
    /**
     * Quote time, in ISO format
     */
    public IsoTime time;
    /**
     * Quote time, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format
     */
    public String brokerTime;
    /**
     * Contains latency information about price streaming, or {@code null}
     */
    public SymbolPriceTimestamps timestamps;
}