package cloud.metaapi.sdk.clients.meta_api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * Defines abstract class for a synchronization listener class
 */
public abstract class SynchronizationListener {

  /**
   * Returns instance number of instance index
   * @param instanceIndex instance index
   */
  public Integer getInstanceNumber(String instanceIndex) {
    if (instanceIndex == null) {
      return null;
    }
    return Integer.parseInt(instanceIndex.split(":")[0]);
  }
  
  /**
   * Returns host name of instance index
   * @param instanceIndex instance index
   */
  public String getHostName(String instanceIndex) {
    if (instanceIndex == null) {
      return null;
    }
    return instanceIndex.split(":")[1];
  }
  
  /**
   * Invoked when connection to MetaTrader terminal established
   * @param instanceIndex index of an account instance connected
   * @param replicas number of account replicas launched
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onConnected(String instanceIndex, int replicas) {
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
   * @param instanceIndex index of an account instance connected
   * @param status server-side application health status
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onHealthStatus(String instanceIndex, HealthStatus status) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when connection to MetaTrader terminal terminated
   * @param instanceIndex index of an account instance connected
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onDisconnected(String instanceIndex) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when broker connection satus have changed
   * @param instanceIndex index of an account instance connected
   * @param connected is MetaTrader terminal is connected to broker
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onBrokerConnectionStatusChanged(String instanceIndex,
    boolean connected) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when MetaTrader terminal state synchronization is started
   * @param instanceIndex index of an account instance connected
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSynchronizationStarted(String instanceIndex) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when MetaTrader account information is updated
   * @param instanceIndex index of an account instance connected
   * @param accountInformation updated MetaTrader account information
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onAccountInformationUpdated(String instanceIndex,
    MetatraderAccountInformation accountInformation) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when the positions are replaced as a result of initial terminal state synchronization
   * @param instanceIndex index of an account instance connected
   * @param positions updated array of positions
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onPositionsReplaced(String instanceIndex, List<MetatraderPosition> positions) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when MetaTrader position is updated
   * @param instanceIndex index of an account instance connected
   * @param position updated MetaTrader position
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onPositionUpdated(String instanceIndex, MetatraderPosition position) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when MetaTrader position is removed
   * @param instanceIndex index of an account instance connected
   * @param positionId removed MetaTrader position id
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onPositionRemoved(String instanceIndex, String positionId) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when the orders are replaced as a result of initial terminal state synchronization
   * @param instanceIndex index of an account instance connected
   * @param orders updated array of orders
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onOrdersReplaced(String instanceIndex, List<MetatraderOrder> orders) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when MetaTrader order is updated
   * @param instanceIndex index of an account instance connected
   * @param order updated MetaTrader order
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onOrderUpdated(String instanceIndex, MetatraderOrder order) {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invoked when MetaTrader order is completed (executed or canceled)
   * @param instanceIndex index of an account instance connected
   * @param orderId completed MetaTrader order id
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onOrderCompleted(String instanceIndex, String orderId) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when a new MetaTrader history order is added
   * @param instanceIndex index of an account instance connected
   * @param historyOrder new MetaTrader history order
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onHistoryOrderAdded(String instanceIndex,
    MetatraderOrder historyOrder) {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invoked when a new MetaTrader history deal is added
   * @param instanceIndex index of an account instance connected
   * @param deal new MetaTrader history deal
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onDealAdded(String instanceIndex, MetatraderDeal deal) {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invoked when a synchronization of history deals on a MetaTrader account have finished
   * @param instanceIndex index of an account instance connected
   * @param synchronizationId synchronization request id
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onDealSynchronizationFinished(String instanceIndex,
    String synchronizationId) {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invoked when a synchronization of history orders on a MetaTrader account have finished
   * @param instanceIndex index of an account instance connected
   * @param synchronizationId synchronization request id
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onOrderSynchronizationFinished(String instanceIndex,
    String synchronizationId) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when a symbol specification was updated
   * @param instanceIndex index of an account instance connected
   * @param specification updated MetaTrader symbol specification
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSymbolSpecificationUpdated(String instanceIndex,
    MetatraderSymbolSpecification specification) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when a symbol specification was removed
   * @param instanceIndex index of an account instance connected
   * @param symbol removed symbol
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSymbolSpecificationRemoved(String instanceIndex, String symbol) {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invoked when a symbol specifications were updated
   * @param instanceIndex index of account instance connected
   * @param specifications updated specifications
   * @param removedSymbols removed symbols
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSymbolSpecificationsUpdated(String instanceIndex,
    List<MetatraderSymbolSpecification> specifications, List<String> removedSymbols) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when a symbol price was updated
   * @param instanceIndex index of an account instance connected
   * @param price updated MetaTrader symbol price
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSymbolPriceUpdated(String instanceIndex,
    MetatraderSymbolPrice price) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when prices for several symbols were updated
   * @param instanceIndex index of an account instance connected
   * @param prices updated MetaTrader symbol prices
   * @param equity account liquidation value
   * @param margin margin used
   * @param freeMargin free margin
   * @param marginLevel margin level calculated as % of equity/margin
   * @param accountCurrencyExchangeRate current exchange rate of account currency into USD
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSymbolPricesUpdated(String instanceIndex,
    List<MetatraderSymbolPrice> prices, Double equity, Double margin, Double freeMargin,
    Double marginLevel, Double accountCurrencyExchangeRate) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when symbol candles were updated
   * @param instanceIndex index of an account instance connected
   * @param candles updated MetaTrader symbol candles
   * @param equity account liquidation value
   * @param margin margin used
   * @param freeMargin free margin
   * @param marginLevel margin level calculated as % of equity/margin
   * @param accountCurrencyExchangeRate current exchange rate of account currency into USD
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onCandlesUpdated(String instanceIndex,
    List<MetatraderCandle> candles, Double equity, Double margin, Double freeMargin,
    Double marginLevel, Double accountCurrencyExchangeRate) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when symbol ticks were updated
   * @param instanceIndex index of an account instance connected
   * @param ticks updated MetaTrader symbol ticks
   * @param equity account liquidation value
   * @param margin margin used
   * @param freeMargin free margin
   * @param marginLevel margin level calculated as % of equity/margin
   * @param accountCurrencyExchangeRate current exchange rate of account currency into USD
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onTicksUpdated(String instanceIndex,
    List<MetatraderTick> ticks, Double equity, Double margin, Double freeMargin,
    Double marginLevel, Double accountCurrencyExchangeRate) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when order books were updated
   * @param instanceIndex index of an account instance connected
   * @param books updated MetaTrader order books
   * @param equity account liquidation value
   * @param margin margin used
   * @param freeMargin free margin
   * @param marginLevel margin level calculated as % of equity/margin
   * @param accountCurrencyExchangeRate current exchange rate of account currency into USD
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onBooksUpdated(String instanceIndex,
    List<MetatraderBook> books, Double equity, Double margin, Double freeMargin,
    Double marginLevel, Double accountCurrencyExchangeRate) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when subscription downgrade has occurred
   * @param instanceIndex index of an account instance connected
   * @param symbol symbol to update subscriptions for
   * @param updates array of market data subscription to update
   * @param unsubscriptions array of subscriptions to cancel
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onSubscriptionDowngraded(String instanceIndex, String symbol,
    List<MarketDataSubscription> updates, List<MarketDataUnsubscription> unsubscriptions) {
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Invoked when a stream for an instance index is closed
   * @param instanceIndex index of an account instance connected
   * @return completable future which resolves when the asynchronous event is processed
   */
  public CompletableFuture<Void> onStreamClosed(String instanceIndex) {
    return CompletableFuture.completedFuture(null);
  }
}