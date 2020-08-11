package cloud.metaapi.sdk;

import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.SynchronizationListener;
import cloud.metaapi.sdk.clients.models.*;

/**
 * Abstract class which defines MetaTrader history storage interface.
 *
 * This class is intended to be used when account synchronization mode is set to user. In this case the consumer is
 * responsible for locally maintaining a copy of MetaTrader terminal history, and the API will send only history changes
 * to the consumer.
 */
public abstract class HistoryStorage extends SynchronizationListener {

    private boolean orderSynchronizationFinished = false;
    private boolean dealSynchronizationFinished = false;
    
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
    public CompletableFuture<Void> onDealSynchronizationFinished() {
        dealSynchronizationFinished = true;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onOrderSynchronizationFinished() {
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