package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification.FillingMode;

/**
 * Common trade options
 */
public class TradeOptions {
    /**
     * Optional order comment or {@code null}. The sum of the line lengths of the comment and the
     * clientId must be less than or equal to 26. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment;
    /**
     * Optional client-assigned id or {@code null}. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 26. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId;
    /**
     * Magic (expert id) number or {@code null}. If not set default value specified in account entity will be used.
     */
    public Long magic;
    /**
     * Optional slippage in points or {@code null}. Should be greater or equal to zero. If not set,
     * default value specified in account entity will be used. Slippage is ignored if execution mode set to
     * SYMBOL_TRADE_EXECUTION_MARKET in symbol specification. Not used for close by orders.
     */
    public Integer slippage;
    /**
     * Optional allowed filling modes in the order of priority or {@code null}. Default is to
     * allow all filling modes and prefer ORDER_FILLING_FOK over ORDER_FILLING_IOC. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type_filling for extra
     * explanation. Note that filling modes can be specified for market orders only, i.e. createMarketBuyOrder,
     * createMarketSellOrder, closePositionPartially, closePosition, closePositionBySymbol.
     */
    public List<FillingMode> fillingModes;
}