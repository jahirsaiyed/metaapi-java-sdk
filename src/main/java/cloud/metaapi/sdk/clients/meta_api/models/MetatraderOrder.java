package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.meta_api.models.ExpirationOptions.ExpirationType;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader order
 */
public class MetatraderOrder {
    
    /**
     * Order type. See https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type
     */
    public enum OrderType {
        ORDER_TYPE_SELL, ORDER_TYPE_BUY, ORDER_TYPE_BUY_LIMIT, 
        ORDER_TYPE_SELL_LIMIT, ORDER_TYPE_BUY_STOP, ORDER_TYPE_SELL_STOP,
        ORDER_TYPE_BUY_STOP_LIMIT, ORDER_TYPE_SELL_STOP_LIMIT, ORDER_TYPE_CLOSE_BY
    }
    
    /**
     * Order state. See https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_state
     */
    public enum OrderState {
        ORDER_STATE_STARTED, ORDER_STATE_PLACED, ORDER_STATE_CANCELED, 
        ORDER_STATE_PARTIAL, ORDER_STATE_FILLED, ORDER_STATE_REJECTED, ORDER_STATE_EXPIRED, 
        ORDER_STATE_REQUEST_ADD, ORDER_STATE_REQUEST_MODIFY, ORDER_STATE_REQUEST_CANCEL
    }
    
    /**
     * Order opening reason. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_reason
     */
    public enum OrderReason { ORDER_REASON_CLIENT, ORDER_REASON_MOBILE, ORDER_REASON_WEB,
        ORDER_REASON_EXPERT, ORDER_REASON_SL, ORDER_REASON_TP, ORDER_REASON_SO, ORDER_REASON_UNKNOWN
    }
    
    /**
     * Order filling mode. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type_filling
     */
    public enum FillingMode { ORDER_FILLING_FOK, ORDER_FILLING_IOC, ORDER_FILLING_RETURN }
    
    /**
     * Order id (ticket number)
     */
    public String id;
    /**
     * Order type
     */
    public OrderType type;
    /**
     * Order state
     */
    public OrderState state;
    /**
     * Order magic number, identifies the EA which created the order
     */
    public int magic;
    /**
     * Time order was created at
     */
    public IsoTime time;
    /**
     * Time order was created at, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format
     */
    public String brokerTime;
    /**
     * Time order was executed or canceled at or {@code null}. Will be specified for completed orders only
     */
    public IsoTime doneTime;
    /**
     * Time order was executed or canceled at, in broker timezone, YYYY-MM-DD HH:mm:ss.SSS format, or {@code null}.
     * Will be specified for completed orders only
     */
    public String doneBrokerTime;
    /**
     * Order symbol
     */
    public String symbol;
    /**
     * Order open price (market price for market orders, limit price for limit orders or stop price
     * for stop orders) or {@code null}
     */
    public Double openPrice;
    /**
     * Current price
     */
    public double currentPrice;
    /**
     * Order stop loss price or {@code null}
     */
    public Double stopLoss;
    /**
     * Order take profit price or {@code null}
     */
    public Double takeProfit;
    /**
     * Order requested quantity
     */
    public double volume;
    /**
     * Order remaining quantity, i.e. requested quantity - filled quantity
     */
    public double currentVolume;
    /**
     * Order position id or {@code null}. Present only if the order has a position attached to it
     */
    public String positionId;
    /**
     * Order comment or {@code null}. The sum of the line lengths of the comment and the clientId must be less
     * than or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment;
    /**
     * Order original comment or {@code null} (present if possible to restore original comment from history)
     */
    public String originalComment;
    /**
     * Client-assigned id or {@code null}. The id value can be assigned when submitting a trade
     * and will be present on position, history orders and history deals related to the trade.
     * You can use this field to bind your trades to objects in your application and then track trade progress.
     * The sum of the line lengths of the comment and the clientId must be less than or equal to 26.
     * For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId;
    /**
     * Platform id (mt4 or mt5)
     */
    public String platform;
    /**
     * Optional flag indicating that order client id and original comment was not
     * identified yet and will be updated in a future synchronization packet, or {@code null}
     */
    public Boolean updatePending;
    /**
     * Order opening reason
     */
    public OrderReason reason;
    /**
     * Order filling mode
     */
    public FillingMode fillingMode;
    /**
     * Order expiration type
     */
    public ExpirationType expirationType;
    /**
     * Optional order expiration time or {@code null}
     */
    public IsoTime expirationTime;
    /**
     * Current exchange rate of account currency into USD, or {@code null}
     */
    public Double accountCurrencyExchangeRate;
}