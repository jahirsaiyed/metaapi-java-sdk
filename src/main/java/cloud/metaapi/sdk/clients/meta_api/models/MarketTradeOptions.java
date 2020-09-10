package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification.FillingMode;

/**
 * Market trade options
 */
public class MarketTradeOptions extends TradeOptions {
    /**
     * Optional allowed filling modes in the order of priority or {@code null}. Default is to
     * allow all filling modes and prefer ORDER_FILLING_FOK over ORDER_FILLING_IOC. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type_filling for extra
     * explanation
     */
    public List<FillingMode> fillingModes;
}