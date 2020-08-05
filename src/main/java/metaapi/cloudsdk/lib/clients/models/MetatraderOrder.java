package metaapi.cloudsdk.lib.clients.models;

import java.util.Optional;

/**
 * MetaTrader order
 */
public class MetatraderOrder {
    
    /**
     * Order type. See https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type
     */
    public enum OrderType {
        ORDER_TYPE_SELL, ORDER_TYPE_BUY, ORDER_TYPE_BUY_LIMIT, 
        ORDER_TYPE_SELL_LIMIT, ORDER_TYPE_BUY_STOP, ORDER_TYPE_SELL_STOP
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
     * Optional time order was executed or canceled at. Will be specified for completed orders only
     */
    public Optional<IsoTime> doneTime;
    /**
     * Order symbol
     */
    public String symbol;
    /**
     * Order open price (market price for market orders, limit price for limit orders or stop price
     * for stop orders)
     */
    public Optional<Double> openPrice;
    /**
     * Current price
     */
    public double currentPrice;
    /**
     * Optional order stop loss price
     */
    public Optional<Double> stopLoss;
    /**
     * Optional order take profit price
     */
    public Optional<Double> takeProfit;
    /**
     * Order requested quantity
     */
    public double volume;
    /**
     * Order remaining quantity, i.e. requested quantity - filled quantity
     */
    public double currentVolume;
    /**
     * Order position id. Present only if the order has a position attached to it
     */
    public Optional<String> positionId;
    /**
     * Optional order comment. The sum of the line lengths of the comment and the clientId must be less
     * than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> comment;
    /**
     * Optional order original comment (present if possible to restore original comment from history)
     */
    public Optional<String> originalComment;
    /**
     * Optional client-assigned id. The id value can be assigned when submitting a trade
     * and will be present on position, history orders and history deals related to the trade.
     * You can use this field to bind your trades to objects in your application and then track trade progress.
     * The sum of the line lengths of the comment and the clientId must be less than or equal to 27.
     * For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> clientId;
    /**
     * Platform id (mt4 or mt5)
     */
    public String platform;
    /**
     * Optional flag indicating that order client id and original comment was not
     * identified yet and will be updated in a future synchronization packet
     */
    public Optional<Boolean> updatePending;
}