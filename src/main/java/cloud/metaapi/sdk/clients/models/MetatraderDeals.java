package cloud.metaapi.sdk.clients.models;

import java.util.List;

/**
 * MetaTrader history deals search query response
 */
public class MetatraderDeals {
    /**
     * List of history deals returned
     */
    public List<MetatraderDeal> deals;
    /**
     * Flag indicating that deal initial synchronization is still in progress
     * and thus search results may be incomplete
     */
    public boolean synchronizing;
}