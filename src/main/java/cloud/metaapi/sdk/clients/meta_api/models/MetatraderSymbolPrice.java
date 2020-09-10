package cloud.metaapi.sdk.clients.meta_api.models;

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
}