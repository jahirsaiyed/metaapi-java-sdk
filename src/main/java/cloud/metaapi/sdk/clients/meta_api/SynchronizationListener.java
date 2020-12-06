package cloud.metaapi.sdk.clients.meta_api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * Defines abstract class for a synchronization listener class
 */
public abstract class SynchronizationListener {

    /**
     * Invoked when connection to MetaTrader terminal established
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onConnected() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Server-side application health status
     */
    public static class HealthStatus {
        /**
         * Flag indicating that REST API is healthy, or {@code null}
         */
        public Boolean restApiHealthy;
        /**
         * Flag indicating that CopyFactory subscriber is healthy, or {@code null}
         */
        public Boolean copyFactorySubscriberHealthy;
        /**
         * Flag indicating that CopyFactory provider is healthy, or {@code null}
         */
        public Boolean copyFactoryProviderHealthy;
    }
    
    /**
     * Invoked when a server-side application health status is received from MetaApi
     * @param status server-side application health status
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onHealthStatus(HealthStatus status) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when connection to MetaTrader terminal terminated
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onDisconnected() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when broker connection satus have changed
     * @param connected is MetaTrader terminal is connected to broker
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onBrokerConnectionStatusChanged(boolean connected) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when MetaTrader terminal state synchronization is started
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onSynchronizationStarted() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when MetaTrader account information is updated
     * @param accountInformation updated MetaTrader account information
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onAccountInformationUpdated(MetatraderAccountInformation accountInformation) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when the positions are replaced as a result of initial terminal state synchronization
     * @param positions updated array of positions
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onPositionsReplaced(List<MetatraderPosition> positions) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when MetaTrader position is updated
     * @param position updated MetaTrader position
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onPositionUpdated(MetatraderPosition position) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when MetaTrader position is removed
     * @param positionId removed MetaTrader position id
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onPositionRemoved(String positionId) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when the orders are replaced as a result of initial terminal state synchronization
     * @param orders updated array of orders
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onOrdersReplaced(List<MetatraderOrder> orders) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when MetaTrader order is updated
     * @param order updated MetaTrader order
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onOrderUpdated(MetatraderOrder order) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invoked when MetaTrader order is completed (executed or canceled)
     * @param orderId completed MetaTrader order id
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onOrderCompleted(String orderId) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when a new MetaTrader history order is added
     * @param historyOrder new MetaTrader history order
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onHistoryOrderAdded(MetatraderOrder historyOrder) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invoked when a new MetaTrader history deal is added
     * @param deal new MetaTrader history deal
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onDealAdded(MetatraderDeal deal) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invoked when a synchronization of history deals on a MetaTrader account have finished
     * @param synchronizationId synchronization request id
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onDealSynchronizationFinished(String synchronizationId) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invoked when a synchronization of history orders on a MetaTrader account have finished
     * @param synchronizationId synchronization request id
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onOrderSynchronizationFinished(String synchronizationId) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when a symbol specification was updated
     * @param specification updated MetaTrader symbol specification
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onSymbolSpecificationUpdated(MetatraderSymbolSpecification specification) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invoked when a symbol price was updated
     * @param price updated MetaTrader symbol price
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onSymbolPriceUpdated(MetatraderSymbolPrice price) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Invoked when prices for several symbols were updated
     * @param prices updated MetaTrader symbol prices
     * @param equity account liquidation value, or {@code null}
     * @param margin margin used, or {@code null}
     * @param freeMargin free margin, or {@code null}
     * @param marginLevel margin level calculated as % of equity/margin, or {@code null}
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onSymbolPricesUpdated(List<MetatraderSymbolPrice> prices, Double equity,
        Double margin, Double freeMargin, Double marginLevel) {
        return CompletableFuture.completedFuture(null);
    }
}