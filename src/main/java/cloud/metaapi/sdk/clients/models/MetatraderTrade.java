package cloud.metaapi.sdk.clients.models;

import java.util.Optional;

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
        POSITION_CLOSE_SYMBOL, ORDER_MODIFY, ORDER_CANCEL
    }
    
    /**
     * Action type
     */
    public ActionType actionType;
    /**
     * Symbol to trade
     */
    public Optional<String> symbol = Optional.empty();
    /**
     * Order volume
     */
    public Optional<Double> volume = Optional.empty();
    /**
     * Order limit or stop price
     */
    public Optional<Double> openPrice = Optional.empty();
    /**
     * Stop loss price
     */
    public Optional<Double> stopLoss = Optional.empty();
    /**
     * Take profit price
     */
    public Optional<Double> takeProfit = Optional.empty();
    /**
     * Order id, must be specified for order modification commands
     */
    public Optional<String> orderId = Optional.empty();
    /**
     * Position id, must be specified for position modification commands
     */
    public Optional<String> positionId = Optional.empty();
    /**
     * Order comment. The sum of the line lengths of the comment and the clientId must be less
     * than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> comment = Optional.empty();
    /**
     * Client-assigned id. The id value can be assigned when submitting a trade and will be present on position,
     * history orders and history deals related to the trade. You can use this field to bind your trades to objects
     * in your application and then track trade progress. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> clientId = Optional.empty();
}