package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * MetaTrader account information (see https://metaapi.cloud/docs/client/models/metatraderAccountInformation/)
 */
public class MetatraderAccountInformation {
    
    /**
     * Margin calculation mode enum
     */
    public enum MarginMode { ACCOUNT_MARGIN_MODE_EXCHANGE, ACCOUNT_MARGIN_MODE_RETAIL_NETTING,
        ACCOUNT_MARGIN_MODE_RETAIL_HEDGING }
    
    /**
     * Platform id, either mt4 or mt5
     */
    public String platform;
    /**
     * Broker name
     */
    public String broker;
    /**
     * Account base currency ISO code
     */
    public String currency;
    /**
     * Broker server name
     */
    public String server;
    /**
     * Account balance
     */
    public double balance;
    /**
     * Account liquidation value
     */
    public double equity;
    /**
     * Used margin
     */
    public double margin;
    /**
     * Free margin
     */
    public double freeMargin;
    /**
     * Account leverage coefficient
     */
    public double leverage;
    /**
     * Margin level calculated as % of equity/margin or {@code null}
     */
    public Double marginLevel;
    /**
     * Flag indicating that trading is allowed
     */
    public boolean tradeAllowed;
    /**
     * Flag indicating that investor password was used (supported for g2 only), or {@code null}
     */
    public Boolean investorMode;
    /**
     * Margin calculation mode
     */
    public MarginMode marginMode;
}