package cloud.metaapi.sdk.meta_api;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.models.*;

/**
 * Abstract class which defines MetaTrader history storage interface.
 */
public abstract class HistoryStorage extends SynchronizationListener {

    private Set<Integer> orderSynchronizationFinished = new HashSet<>();
    private Set<Integer> dealSynchronizationFinished = new HashSet<>();
    
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
     * Returns times of last deals by instance indices
     * @return map of last deal times by instance indices
     */
    public abstract Map<Integer, Long> getLastDealTimeByInstanceIndex();
    
    /**
     * Returns times of last history orders by instance indices
     * @return map of last history orders times by instance indices
     */
    public abstract Map<Integer, Long> getLastHistoryOrderTimeByInstanceIndex();
    
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
        return !orderSynchronizationFinished.isEmpty();
    }
    
    /**
     * Returns flag indicating whether deal history synchronization have finished
     * @return flag indicating whether deal history synchronization have finished
     */
    public boolean isDealSynchronizationFinished() {
        return !dealSynchronizationFinished.isEmpty();
    }
    
    /**
     * Returns the time of the last history order record stored in the history storage
     * @return the time of the last history order record stored in the history storage
     */
    public CompletableFuture<IsoTime> getLastHistoryOrderTime() {
      return getLastHistoryOrderTime(null);
    }
    
    /**
     * Returns the time of the last history order record stored in the history storage
     * @param instanceIndex index of an account instance connected, or {@code null}
     * @return the time of the last history order record stored in the history storage
     */
    public abstract CompletableFuture<IsoTime> getLastHistoryOrderTime(Integer instanceIndex);
    
    /**
     * Returns the time of the last history deal record stored in the history storage
     * @return the time of the last history deal record stored in the history storage
     */
    public CompletableFuture<IsoTime> getLastDealTime() {
      return getLastDealTime(null);
    }
    
    /**
     * Returns the time of the last history deal record stored in the history storage
     * @param instanceIndex index of an account instance connected, or {@code null}
     * @return the time of the last history deal record stored in the history storage
     */
    public abstract CompletableFuture<IsoTime> getLastDealTime(Integer instanceIndex);
    
    @Override
    public abstract CompletableFuture<Void> onHistoryOrderAdded(int instanceIndex, MetatraderOrder historyOrder);
    
    @Override
    public abstract CompletableFuture<Void> onDealAdded(int instanceIndex, MetatraderDeal deal);
    
    @Override
    public CompletableFuture<Void> onDealSynchronizationFinished(int instanceIndex, String synchronizationId) {
        dealSynchronizationFinished.add(instanceIndex);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onOrderSynchronizationFinished(int instanceIndex, String synchronizationId) {
        orderSynchronizationFinished.add(instanceIndex);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onConnected(int instanceIndex, int replicas) {
        orderSynchronizationFinished.remove(instanceIndex);
        dealSynchronizationFinished.remove(instanceIndex);
        return CompletableFuture.completedFuture(null);
    }
}