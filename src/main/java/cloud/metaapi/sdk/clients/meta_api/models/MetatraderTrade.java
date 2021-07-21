package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.meta_api.models.StopOptions.StopUnits;

/**
 * MetaTrader trade (see https://metaapi.cloud/docs/client/models/metatraderTrade/)
 */
public class MetatraderTrade {
    
    /**
     * Action type enum
     */
    public enum ActionType {
        ORDER_TYPE_SELL, ORDER_TYPE_BUY, ORDER_TYPE_BUY_LIMIT, ORDER_TYPE_SELL_LIMIT, ORDER_TYPE_BUY_STOP,
        ORDER_TYPE_SELL_STOP, ORDER_TYPE_BUY_STOP_LIMIT, ORDER_TYPE_SELL_STOP_LIMIT, POSITION_MODIFY,
        POSITION_PARTIAL, POSITION_CLOSE_ID, POSITION_CLOSE_BY, POSITIONS_CLOSE_SYMBOL, ORDER_MODIFY, ORDER_CANCEL
    }
    
    /**
     * Action type
     */
    public ActionType actionType;
    /**
     * Symbol to trade or {@code null}
     */
    public String symbol;
    /**
     * Order volume or {@code null}
     */
    public Double volume;
    /**
     * Order limit or stop price or {@code null}
     */
    public Double openPrice;
    /**
     * Stop loss price or {@code null}
     */
    public Double stopLoss;
    /**
     * Stop loss units, or {@code null}. Default is ABSOLUTE_PRICE
     */
    public StopUnits stopLossUnits;
    /**
     * Take profit price or {@code null}
     */
    public Double takeProfit;
    /**
     * Take profit units, or {@code null}. Default is ABSOLUTE_PRICE
     */
    public StopUnits takeProfitUnits;
    /**
     * Order id or {@code null}, must be specified for order modification commands
     */
    public String orderId;
    /**
     * Position id or {@code null}, must be specified for position modification commands
     */
    public String positionId;
    /**
     * Identifier of an opposite position used for closing by order ORDER_TYPE_CLOSE_BY, or {@code null}
     */
    public String closeByPositionId;
    /**
     * The Limit order price for the StopLimit order, or {@code null}
     */
    public Double stopLimitPrice;
    /**
     * Order comment or {@code null}. The sum of the line lengths of the comment and the clientId must be less
     * than or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment;
    /**
     * Client-assigned id or {@code null}. The id value can be assigned when submitting a trade and will be present on position,
     * history orders and history deals related to the trade. You can use this field to bind your trades to objects
     * in your application and then track trade progress. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId;
    /**
     * Magic number (expert adviser id) or {@code null}
     */
    public Integer magic;
}