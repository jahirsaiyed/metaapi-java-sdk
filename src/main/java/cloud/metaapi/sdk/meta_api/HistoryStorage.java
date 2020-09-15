package cloud.metaapi.sdk.meta_api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.models.*;

/**
 * Abstract class which defines MetaTrader history storage interface.
 */
public abstract class HistoryStorage extends SynchronizationListener {

    private boolean orderSynchronizationFinished = false;
    private boolean dealSynchronizationFinished = false;
    
    /**
     * Returns all deals stored in history storage
     * @return all deals stored in history storage
     */
    public abstract List<MetatraderDeal> getDeals();
    
    /**
     * Returns all history orders stored in history storage
     * @return all history orders stored in history storage
     */
    public abstract List<MetatraderOrder> getHistoryOrders();
    
    /**
     * Resets the storage
     */
    public abstract void reset();
    
    /**
     * Loads history data from the file manager
     * @return completable future which resolves when the history is loaded
     */
    public abstract CompletableFuture<Void> loadData();
    
    /**
     * Saves unsaved history items to storage
     * @return completable future which resolves when storage is updated
     */
    public abstract CompletableFuture<Void> updateStorage();
    
    /**
     * Returns flag indicating whether order history synchronization have finished
     * @return flag indicating whether order history synchronization have finished
     */
    public boolean isOrderSynchronizationFinished() {
        return orderSynchronizationFinished;
    }
    
    /**
     * Returns flag indicating whether deal history synchronization have finished
     * @return flag indicating whether deal history synchronization have finished
     */
    public boolean isDealSynchronizationFinished() {
        return dealSynchronizationFinished;
    }
    
    /**
     * Returns the time of the last history order record stored in the history storage
     * @returns the time of the last history order record stored in the history storage
     */
    public abstract CompletableFuture<IsoTime> getLastHistoryOrderTime();
    
    /**
     * Returns the time of the last history deal record stored in the history storage
     * @returns the time of the last history deal record stored in the history storage
     */
    public abstract CompletableFuture<IsoTime> getLastDealTime();
    
    @Override
    public abstract CompletableFuture<Void> onHistoryOrderAdded(MetatraderOrder historyOrder);
    
    @Override
    public abstract CompletableFuture<Void> onDealAdded(MetatraderDeal deal);
    
    @Override
    public CompletableFuture<Void> onDealSynchronizationFinished(String synchronizationId) {
        dealSynchronizationFinished = true;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onOrderSynchronizationFinished(String synchronizationId) {
        orderSynchronizationFinished = true;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onConnected() {
        orderSynchronizationFinished = false;
        dealSynchronizationFinished = false;
        return CompletableFuture.completedFuture(null);
    }
}