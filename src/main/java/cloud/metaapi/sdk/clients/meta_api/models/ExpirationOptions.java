package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Pending order expiration settings
 */
public class ExpirationOptions {
    
    /**
     * Type pending order expiration type. See
     * https://www.mql5.com/en/docs/constants/tradingconstants/orderproperties#enum_order_type_time for more details.
     * MetaTrader4 platform supports only ORDER_TIME_SPECIFIED expiration type.
     */
    public enum ExpirationType { ORDER_TIME_GTC, ORDER_TIME_DAY, ORDER_TIME_SPECIFIED, ORDER_TIME_SPECIFIED_DAY }
    
    /**
     * Type pending order expiration type
     */
    public ExpirationType type;
    /**
     * Optional pending order expiration time or {@code null}. Ignored if expiration type is not one of
     * ORDER_TIME_DAY or ORDER_TIME_SPECIFIED
     */
    public IsoTime time;
}