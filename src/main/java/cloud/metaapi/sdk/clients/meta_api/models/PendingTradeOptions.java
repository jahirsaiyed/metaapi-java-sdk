package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Pending order trade options 
 */
public class PendingTradeOptions extends TradeOptions {
    /**
     * Optional pending order expiration settings or {@code null}. See Pending order expiration settings section
     */
    public ExpirationOptions expiration;
}