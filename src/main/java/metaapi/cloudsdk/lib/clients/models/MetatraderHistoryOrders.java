package metaapi.cloudsdk.lib.clients.models;

import java.util.List;

/**
 * MetaTrader history orders search query response
 */
public class MetatraderHistoryOrders {
    /**
     * List of history orders returned
     */
    public List<MetatraderOrder> historyOrders;
    /**
     * Flag indicating that history order initial synchronization is still in progress
     * and thus search results may be incomplete
     */
    public boolean synchronizing;
}