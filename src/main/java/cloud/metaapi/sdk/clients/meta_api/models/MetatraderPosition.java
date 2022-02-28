package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader position
 */
public class MetatraderPosition {
    
    /**
     * Position type
     */
    public enum PositionType { POSITION_TYPE_BUY, POSITION_TYPE_SELL }
    
    /**
     * Position opening reason. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/positionproperties#enum_position_reason
     */
    public enum PositionReason { POSITION_REASON_CLIENT, POSITION_REASON_EXPERT,
        POSITION_REASON_MOBILE, POSITION_REASON_WEB, POSITION_REASON_UNKNOWN }
    
    /**
     * Position id (ticket number)
     */
    public String id;
    /**
     * Position type
     */
    public PositionType type;
    /**
     * Position symbol
     */
    public String symbol;
    /**
     * Position magic number, identifies the EA which opened the position
     */
    public long magic;
    /**
     * Time position was opened at
     */
    public IsoTime time;
    /**
     * Time position was opened at, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format
     */
    public String brokerTime;
    /**
     * Last position modification time
     */
    public IsoTime updateTime;
    /**
     * Position open price
     */
    public double openPrice;
    /**
     * Current price
     */
    public double currentPrice;
    /**
     * Current tick value
     */
    public double currentTickValue;
    /**
     * Position stop loss price or {@code null}
     */
    public Double stopLoss;
    /**
     * Position take profit price or {@code null}
     */
    public Double takeProfit;
    /**
     * Position volume
     */
    public double volume;
    /**
     * Position cumulative swap
     */
    public Double swap;
    /**
     * Position cumulative profit
     */
    public Double profit;
    /**
     * Position comment or {@code null}. The sum of the line lengths of the comment and 
     * the clientId must be less than or equal to 26. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment;
    /**
     * Client-assigned id or {@code null}. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. 
     * You can use this field to bind your trades to objects in your application and then track trade progress.
     * The sum of the line lengths of the comment and the clientId must be less than or equal to 26. 
     * For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId;
    /**
     * Profit of the part of the position which is not yet closed, including swap
     */
    public Double unrealizedProfit;
    /**
     * Profit of the already closed part, including commissions and swap
     */
    public Double realizedProfit;
    /**
     * Position commission
     */
    public double commission;
    /**
     * Position opening reason
     */
    public PositionReason reason;
    /**
     * Current exchange rate of account currency into account base currency (USD if you
     * did not override it), or {@code null}
     */
    public Double accountCurrencyExchangeRate;
    /**
     * Position original comment, or {@code null} (present if possible to restore from history)
     */
    public String originalComment;
    /**
     * Flag indicating that position original comment and clientId was not identified
     * yet and will be updated in a future packet, or {@code null}
     */
    public String updatePending;
}