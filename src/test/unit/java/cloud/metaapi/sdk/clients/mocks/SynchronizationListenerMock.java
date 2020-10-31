package cloud.metaapi.sdk.clients.mocks;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * Mock for testing synchronization listener events
 */
public class SynchronizationListenerMock extends SynchronizationListener {

    /**
     * Completable future which is completed when onConnected event is handled
     */
    public CompletableFuture<Void> onConnectedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onDisconnected event is handled
     */
    public CompletableFuture<Void> onDisconnectedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onBrokerConnectionStatusChanged event is handled
     * resulting with event received data
     */
    public CompletableFuture<Boolean> onBrokerConnectionStatusChangedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onSynchronizationStarted event is handled 
     * resulting with event received data
     */
    public CompletableFuture<Void> onSynchronizationStartedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onAccountInformationUpdated event is handled 
     * resulting with event received data
     */
    public CompletableFuture<MetatraderAccountInformation> onAccountInformationUpdatedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onPositionReplaced event is handled
     * resulting with event received data
     */
    public CompletableFuture<List<MetatraderPosition>> onPositionsReplacedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onPositionsUpdated event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderPosition> onPositionUpdatedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onPositionRemoved event is handled
     * resulting with event received data
     */
    public CompletableFuture<String> onPositionRemovedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onOrdersReplaced event is handled
     * resulting with event received data
     */
    public CompletableFuture<List<MetatraderOrder>> onOrdersReplacedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onOrderUpdated event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderOrder> onOrderUpdatedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onOrderCompleted event is handled
     * resulting with event received data
     */
    public CompletableFuture<String> onOrderCompletedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onHistoryOrderAdded event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderOrder> onHistoryOrderAddedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onDealAdded event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderDeal> onDealAddedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onDealSynchronizationFinished event is handled
     */
    public CompletableFuture<Void> onDealSynchronizationFinishedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onOrderSynchronizationFinished event is handled
     */
    public CompletableFuture<Void> onOrderSynchronizationFinishedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onSymbolSpecificationUpdated event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderSymbolSpecification> onSymbolSpecificationUpdatedResult = new CompletableFuture<>();
    /**
     * Completable future which is completed when onSymbolPriceUpdated event is handled
     * resulting with event received data
     */
    public CompletableFuture<MetatraderSymbolPrice> onSymbolPriceUpdatedResult = new CompletableFuture<>();
    
    @Override
    public CompletableFuture<Void> onConnected() {
        this.onConnectedResult.complete(null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDisconnected() {
        this.onDisconnectedResult.complete(null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onBrokerConnectionStatusChanged(boolean connected) {
        this.onBrokerConnectionStatusChangedResult.complete(connected);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onSynchronizationStarted() {
        this.onSynchronizationStartedResult.complete(null);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onAccountInformationUpdated(MetatraderAccountInformation accountInformation) {
        this.onAccountInformationUpdatedResult.complete(accountInformation);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onPositionsReplaced(List<MetatraderPosition> positions) {
        this.onPositionsReplacedResult.complete(positions);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionUpdated(MetatraderPosition position) {
        this.onPositionUpdatedResult.complete(position);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionRemoved(String positionId) {
        this.onPositionRemovedResult.complete(positionId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrdersReplaced(List<MetatraderOrder> orders) {
        this.onOrdersReplacedResult.complete(orders);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onOrderUpdated(MetatraderOrder order) {
        this.onOrderUpdatedResult.complete(order);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderCompleted(String orderId) {
        this.onOrderCompletedResult.complete(orderId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onHistoryOrderAdded(MetatraderOrder historyOrder) {
        this.onHistoryOrderAddedResult.complete(historyOrder);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDealAdded(MetatraderDeal deal) {
        this.onDealAddedResult.complete(deal);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDealSynchronizationFinished(String synchronizationId) {
        this.onDealSynchronizationFinishedResult.complete(null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderSynchronizationFinished(String synchronizationId) {
        this.onOrderSynchronizationFinishedResult.complete(null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onSymbolSpecificationUpdated(MetatraderSymbolSpecification specification) {
        this.onSymbolSpecificationUpdatedResult.complete(specification);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onSymbolPriceUpdated(MetatraderSymbolPrice price) {
        this.onSymbolPriceUpdatedResult.complete(price);
        return CompletableFuture.completedFuture(null);
    }
}