package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * MetaTrader trade (see https://metaapi.cloud/docs/client/models/metatraderTrade/)
 */
public class MetatraderTrade {
    
    /**
     * Action type enum
     */
    public enum ActionType {
        ORDER_TYPE_SELL, ORDER_TYPE_BUY, ORDER_TYPE_BUY_LIMIT, ORDER_TYPE_SELL_LIMIT,
        ORDER_TYPE_BUY_STOP, ORDER_TYPE_SELL_STOP, POSITION_MODIFY, POSITION_PARTIAL, POSITION_CLOSE_ID,
        POSITIONS_CLOSE_SYMBOL, ORDER_MODIFY, ORDER_CANCEL
    }
    
    /**
     * Action type
     */
    public ActionType actionType;
    /**
     * Symbol to trade or {@code null}
     */
    public String symbol = null;
    /**
     * Order volume or {@code null}
     */
    public Double volume = null;
    /**
     * Order limit or stop price or {@code null}
     */
    public Double openPrice = null;
    /**
     * Stop loss price or {@code null}
     */
    public Double stopLoss = null;
    /**
     * Take profit price or {@code null}
     */
    public Double takeProfit = null;
    /**
     * Order id or {@code null}, must be specified for order modification commands
     */
    public String orderId = null;
    /**
     * Position id or {@code null}, must be specified for position modification commands
     */
    public String positionId = null;
    /**
     * Order comment or {@code null}. The sum of the line lengths of the comment and the clientId must be less
     * than or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment = null;
    /**
     * Client-assigned id or {@code null}. The id value can be assigned when submitting a trade and will be present on position,
     * history orders and history deals related to the trade. You can use this field to bind your trades to objects
     * in your application and then track trade progress. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId = null;
    /**
     * Magic number (expert adviser id) or {@code null}
     */
    public Integer magic = null;
}