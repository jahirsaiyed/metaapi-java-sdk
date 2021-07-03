package cloud.metaapi.sdk.meta_api;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.ReconnectListener;
import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.TradeException;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataSubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataUnsubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderBook;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderCandle;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeals;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderHistoryOrders;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTick;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.meta_api.models.PendingTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade.ActionType;
import cloud.metaapi.sdk.clients.models.*;

/**
 * Exposes MetaApi MetaTrader API connection to consumers
 */
public class MetaApiConnection extends SynchronizationListener implements ReconnectListener {

  private static Logger logger = Logger.getLogger(MetaApiConnection.class);
  private MetaApiWebsocketClient websocketClient;
  private MetatraderAccount account;
  private ConnectionRegistry connectionRegistry;
  private IsoTime historyStartTime = null;
  private TerminalState terminalState;
  private HistoryStorage historyStorage;
  private ConnectionHealthMonitor healthMonitor;
  private Map<String, Subscriptions> subscriptions = new HashMap<>();
  private Map<Integer, State> stateByInstanceIndex = new HashMap<>();
  private boolean closed = false;

  private static class State {
    public int instanceIndex;
    public Set<String> ordersSynchronized = new HashSet<>();
    public Set<String> dealsSynchronized = new HashSet<>();
    public String shouldSynchronize = "";
    public Integer synchronizationRetryIntervalInSeconds;
    public boolean isSynchronized = false;
    public String lastDisconnectedSynchronizationId = "";
    public String lastSynchronizationId = "";
    public boolean disconnected = false;
  }
  
  private static class Subscriptions {
    List<MarketDataSubscription> subscriptions;
  }
  
  /**
   * Constructs MetaApi MetaTrader Api connection
   * @param websocketClient MetaApi websocket client
   * @param account MetaTrader account to connect to
   * @param historyStorage terminal history storage or {@code null}. 
   * By default an instance of MemoryHistoryStorage will be used.
   * @param connectionRegistry metatrader account connection registry
   */
  public MetaApiConnection(
    MetaApiWebsocketClient websocketClient,
    MetatraderAccount account,
    HistoryStorage historyStorage,
    ConnectionRegistry connectionRegistry
  ) {
    this(websocketClient, account, historyStorage, connectionRegistry, null);
  }
  
  /**
   * Constructs MetaApi MetaTrader Api connection
   * @param websocketClient MetaApi websocket client
   * @param account MetaTrader account to connect to
   * @param historyStorage terminal history storage or {@code null}. 
   * By default an instance of MemoryHistoryStorage will be used.
   * @param connectionRegistry metatrader account connection registry
   * @param historyStartTime history start sync time, or {@code null}
   */
  public MetaApiConnection(
    MetaApiWebsocketClient websocketClient,
    MetatraderAccount account,
    HistoryStorage historyStorage,
    ConnectionRegistry connectionRegistry,
    IsoTime historyStartTime
  ) {
    this.websocketClient = websocketClient;
    this.account = account;
    this.connectionRegistry = connectionRegistry;
    this.historyStartTime = historyStartTime;
    this.terminalState = new TerminalState();
    this.historyStorage = historyStorage != null 
      ? historyStorage : new MemoryHistoryStorage(account.getId(), connectionRegistry.getApplication());
    this.healthMonitor = new ConnectionHealthMonitor(this);
    websocketClient.addSynchronizationListener(account.getId(), this);
    websocketClient.addSynchronizationListener(account.getId(), this.terminalState);
    websocketClient.addSynchronizationListener(account.getId(), this.historyStorage);
    websocketClient.addSynchronizationListener(account.getId(), this.healthMonitor);
    websocketClient.addReconnectListener(this, account.getId());
  }
  
  /**
   * Returns account information (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
   * @return completable future resolving with account information
   */
  public CompletableFuture<MetatraderAccountInformation> getAccountInformation() {
    return websocketClient.getAccountInformation(account.getId());
  }
  
  /**
   * Returns positions (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPositions/).
   * @return completable future resolving with array of open positions
   */
  public CompletableFuture<List<MetatraderPosition>> getPositions() {
    return websocketClient.getPositions(account.getId());
  }
  
  /**
   * Returns specific position (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPosition/).
   * @param positionId position id
   * @return completable future resolving with MetaTrader position found
   */
  public CompletableFuture<MetatraderPosition> getPosition(String positionId) {
    return websocketClient.getPosition(account.getId(), positionId);
  }
  
  /**
   * Returns open orders (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrders/).
   * @return completable future resolving with open MetaTrader orders
   */
  public CompletableFuture<List<MetatraderOrder>> getOrders() {
    return websocketClient.getOrders(account.getId());
  }
  
  /**
   * Returns specific open order (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrder/).
   * @param orderId order id (ticket number)
   * @return completable future resolving with metatrader order found
   */
  public CompletableFuture<MetatraderOrder> getOrder(String orderId) {
    return websocketClient.getOrder(account.getId(), orderId);
  }
  
  /**
   * Returns the history of completed orders for a specific ticket number (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByTicket/).
   * @param ticket ticket number (order id)
   * @return completable future resolving with request results containing history orders found
   */
  public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTicket(String ticket) {
    return websocketClient.getHistoryOrdersByTicket(account.getId(), ticket);
  }
  
  /**
   * Returns the history of completed orders for a specific position id (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByPosition/)
   * @param positionId position id
   * @return completable future resolving with request results containing history orders found
   */
  public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByPosition(String positionId) {
    return websocketClient.getHistoryOrdersByPosition(account.getId(), positionId);
  }
  
  /**
   * Returns the history of completed orders for a specific time range (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByTimeRange/)
   * @param startTime start of time range, inclusive
   * @param endTime end of time range, exclusive
   * @param offset pagination offset
   * @param limit pagination limit
   * @return completable future resolving with request results containing history orders found
   */
  public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTimeRange(
    IsoTime startTime, IsoTime endTime, int offset, int limit
  ) {
    return websocketClient.getHistoryOrdersByTimeRange(account.getId(), startTime, endTime, offset, limit);
  }
  
  /**
   * Returns history deals with a specific ticket number (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByTicket/).
   * @param ticket ticket number (deal id for MT5 or order id for MT4)
   * @return completable future resolving with request results containing deals found
   */
  public CompletableFuture<MetatraderDeals> getDealsByTicket(String ticket) {
    return websocketClient.getDealsByTicket(account.getId(), ticket);
  }
  
  /**
   * Returns history deals for a specific position id (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByPosition/).
   * @param positionId position id
   * @return completable future resolving with request results containing deals found
   */
  public CompletableFuture<MetatraderDeals> getDealsByPosition(String positionId) {
    return websocketClient.getDealsByPosition(account.getId(), positionId);
  }
  
  /**
   * Returns history deals with for a specific time range (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByTimeRange/).
   * @param startTime start of time range, inclusive
   * @param endTime end of time range, exclusive
   * @param offset pagination offset
   * @param limit pagination limit
   * @return completable future resolving with request results containing deals found
   */
  public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
    IsoTime startTime, IsoTime endTime, int offset, int limit
  ) {
    return websocketClient.getDealsByTimeRange(account.getId(), startTime, endTime, offset, limit);
  }
  
  /**
   * Clears the order and transaction history of a specified application so that it can be synchronized from scratch 
   * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
   * @return completable future resolving when the history is cleared
   */
  public CompletableFuture<Void> removeHistory() {
    return removeHistory(null);
  }
  
  /**
   * Clears the order and transaction history of a specified application so that it can be synchronized from scratch 
   * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
   * @param application application to remove history for, or {@code null}
   * @return completable future resolving when the history is cleared
   */
  public CompletableFuture<Void> removeHistory(String application) {
    historyStorage.clear();
    return websocketClient.removeHistory(account.getId(), application);
  }
  
  /**
   * Clears the order and transaction history of a specified application and removes application (see
   * https://metaapi.cloud/docs/client/websocket/api/removeApplication/).
   * @return completable future resolving when the history is cleared and application is removed
   */
  public CompletableFuture<Void> removeApplication() {
    historyStorage.clear();
    return websocketClient.removeApplication(account.getId());
  }
  
  /**
   * Creates a market buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createMarketBuyOrder(
    String symbol, double volume, Double stopLoss, Double takeProfit, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a market sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createMarketSellOrder(
    String symbol, double volume, Double stopLoss, Double takeProfit, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a limit buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order limit price
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createLimitBuyOrder(
    String symbol, double volume, double openPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY_LIMIT;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a limit sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order limit price
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createLimitSellOrder(
    String symbol, double volume, double openPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL_LIMIT;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a stop buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order stop price
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createStopBuyOrder(
    String symbol, double volume, double openPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY_STOP;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a stop sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order stop price
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally
   * with {@link TradeException}, check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createStopSellOrder(
    String symbol, double volume, double openPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL_STOP;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a stop limit buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order stop price
   * @param stopLimitPrice the limit order price for the stop limit order
   * @param stopLoss stop loss price, or {@code null}
   * @param takeProfit take profit price, or {@code null}
   * @param options trade options, or {@code null}
   * @return completable future resolving with trade result or completing exceptionally
   * with {@link TradeException}, check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createStopLimitBuyOrder(
    String symbol, double volume, double openPrice, double stopLimitPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY_STOP_LIMIT;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLimitPrice = stopLimitPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Creates a stop limit sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param symbol symbol to trade
   * @param volume order volume
   * @param openPrice order stop price
   * @param stopLimitPrice the limit order price for the stop limit order
   * @param stopLoss stop loss price, or {@code null}
   * @param takeProfit take profit price, or {@code null}
   * @param options trade options, or {@code null}
   * @return completable future resolving with trade result or completing exceptionally
   * with {@link TradeException}, check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> createStopLimitSellOrder(
    String symbol, double volume, double openPrice, double stopLimitPrice,
    Double stopLoss, Double takeProfit, PendingTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL_STOP_LIMIT;
    trade.symbol = symbol;
    trade.volume = volume;
    trade.openPrice = openPrice;
    trade.stopLimitPrice = stopLimitPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Modifies a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param positionId position id to modify
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> modifyPosition(
    String positionId, Double stopLoss, Double takeProfit
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_MODIFY;
    trade.positionId = positionId;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Partially closes a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param positionId position id to modify
   * @param volume volume to close
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> closePositionPartially(
    String positionId, double volume, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_PARTIAL;
    trade.positionId = positionId;
    trade.volume = volume;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Fully closes a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param positionId position id to modify
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> closePosition(
    String positionId, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_CLOSE_ID;
    trade.positionId = positionId;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Fully closes a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param positionId position id to close by opposite position
   * @param oppositePositionId opposite position id to close
   * @param options optional trade options, or {@code null}
   * @return completable future resolving with trade result or completing exceptionally
   * with {@link TradeException}, check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> closeBy(
    String positionId, String oppositePositionId, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_CLOSE_BY;
    trade.positionId = positionId;
    trade.closeByPositionId = oppositePositionId;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Closes position by a symbol (see https://metaapi.cloud/docs/client/websocket/api/trade/)
   * @param symbol symbol to trade
   * @param options optional trade options or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> closePositionsBySymbol(
    String symbol, MarketTradeOptions options
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITIONS_CLOSE_SYMBOL;
    trade.symbol = symbol;
    if (options != null) copyModelProperties(options, trade);
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Modifies a pending order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param orderId order id (ticket number)
   * @param openPrice order stop price
   * @param stopLoss optional stop loss price or {@code null}
   * @param takeProfit optional take profit price or {@code null}
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> modifyOrder(
    String orderId, double openPrice, double stopLoss, double takeProfit
  ) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_MODIFY;
    trade.orderId = orderId;
    trade.openPrice = openPrice;
    trade.stopLoss = stopLoss;
    trade.takeProfit = takeProfit;
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Cancels order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
   * @param orderId order id (ticket number)
   * @return completable future resolving with trade result or completing exceptionally with {@link TradeException},
   * check error properties for error code details
   */
  public CompletableFuture<MetatraderTradeResponse> cancelOrder(String orderId) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_CANCEL;
    trade.orderId = orderId;
    return websocketClient.trade(account.getId(), trade);
  }
  
  /**
   * Reconnects to the Metatrader terminal (see https://metaapi.cloud/docs/client/websocket/api/reconnect/).
   * @return completable future which resolves when reconnection started
   */
  public CompletableFuture<Void> reconnect() {
    return websocketClient.reconnect(account.getId());
  }
  
  /**
   * Requests the terminal to start synchronization process
   * (see https://metaapi.cloud/docs/client/websocket/synchronizing/synchronize/)
   * @param instanceIndex instance index
   * @return completable future which resolves when synchronization started
   */
  public CompletableFuture<Void> synchronize(int instanceIndex) {
    return CompletableFuture.runAsync(() -> {
      IsoTime lastHistoryOrderTime = historyStorage.getLastHistoryOrderTime(instanceIndex).join();
      IsoTime startingHistoryOrderTime;
      if (historyStartTime == null || lastHistoryOrderTime.getDate().compareTo(historyStartTime.getDate()) > 0) {
        startingHistoryOrderTime = lastHistoryOrderTime;
      } else startingHistoryOrderTime = historyStartTime;
      IsoTime lastDealTime = historyStorage.getLastDealTime(instanceIndex).join();
      IsoTime startingDealTime;
      if (historyStartTime == null || lastDealTime.getDate().compareTo(historyStartTime.getDate()) > 0) {
        startingDealTime = lastDealTime;
      } else startingDealTime = historyStartTime;
      String synchronizationId = RandomStringUtils.randomAlphanumeric(32);
      getState(instanceIndex).lastSynchronizationId = synchronizationId;
      websocketClient.synchronize(account.getId(), instanceIndex, synchronizationId,
        startingHistoryOrderTime, startingDealTime).join();
    });
  }
  
  /**
   * Initializes meta api connection
   * @return completable future which resolves when meta api connection is initialized
   */
  public CompletableFuture<Void> initialize() {
    return historyStorage.initialize();
  }
  
  /**
   * Initiates subscription to MetaTrader terminal
   * @return completable future which resolves when subscription is initiated
   */
  public CompletableFuture<Void> subscribe() {
    websocketClient.ensureSubscribe(account.getId(), null);
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Subscribes on market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @return completable future which resolves when subscription request was processed
   */
  public CompletableFuture<Void> subscribeToMarketData(String symbol) {
    return subscribeToMarketData(symbol, new ArrayList<>(), 0);
  }
  
  /**
   * Subscribes on market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of market data subscription to create or update. Please
   * note that this feature is not fully implemented on server-side yet
   * @return completable future which resolves when subscription request was processed
   */
  public CompletableFuture<Void> subscribeToMarketData(String symbol,
    List<MarketDataSubscription> subscriptions) {
    return subscribeToMarketData(symbol, subscriptions, 0);
  }
  
  /**
   * Subscribes on market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of market data subscription to create or update. Please
   * note that this feature is not fully implemented on server-side yet
   * @param instanceIndex instance index
   * @return completable future which resolves when subscription request was processed
   */
  public CompletableFuture<Void> subscribeToMarketData(String symbol,
    List<MarketDataSubscription> subscriptions, int instanceIndex) {
    Subscriptions subscriptionsItem = new Subscriptions();
    subscriptionsItem.subscriptions = subscriptions;
    this.subscriptions.put(symbol, subscriptionsItem);
    return websocketClient.subscribeToMarketData(account.getId(), instanceIndex, symbol, subscriptions);
  }
  
  /**
   * Unsubscribes from market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/unsubscribeFromMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @return completable future which resolves when unsubscription request was processed
   */
  public CompletableFuture<Void> unsubscribeFromMarketData(String symbol) {
    return unsubscribeFromMarketData(symbol, new ArrayList<>(), 0);
  }
  
  /**
   * Unsubscribes from market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/unsubscribeFromMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of subscriptions to cancel
   * @return completable future which resolves when unsubscription request was processed
   */
  public CompletableFuture<Void> unsubscribeFromMarketData(String symbol,
    List<MarketDataUnsubscription> subscriptions) {
    return unsubscribeFromMarketData(symbol, subscriptions, 0);
  }
  
  /**
   * Unsubscribes from market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/unsubscribeFromMarketData/).
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of subscriptions to cancel
   * @param instanceIndex instance index
   * @return completable future which resolves when unsubscription request was processed
   */
  public CompletableFuture<Void> unsubscribeFromMarketData(String symbol,
    List<MarketDataUnsubscription> subscriptions, int instanceIndex) {
    if (subscriptions.size() == 0) {
      this.subscriptions.remove(symbol);
    } else if (this.subscriptions.containsKey(symbol)) {
      this.subscriptions.get(symbol).subscriptions = this.subscriptions.get(symbol).subscriptions
        .stream().filter(s -> !subscriptions.stream()
          .filter(s2 -> s.type.equals(s2.type)).findFirst().isPresent()
        ).collect(Collectors.toList());
      if (this.subscriptions.get(symbol).subscriptions.size() == 0) {
        this.subscriptions.remove(symbol);
      }
    }
    return websocketClient.unsubscribeFromMarketData(account.getId(), instanceIndex, symbol, subscriptions);
  }
  
  @Override
  public CompletableFuture<Void> onSubscriptionDowngraded(int instanceIndex, String symbol,
      List<MarketDataSubscription> updates, List<MarketDataUnsubscription> unsubscriptions) {
      return CompletableFuture.runAsync(() -> {
        List<MarketDataSubscription> subscriptions = this.subscriptions.containsKey(symbol)
            ? this.subscriptions.get(symbol).subscriptions : new ArrayList<>();
        if (unsubscriptions.size() != 0) {
          if (subscriptions.size() != 0) {
            for (MarketDataUnsubscription subscription : unsubscriptions) {
              subscriptions = subscriptions.stream().filter(s -> s.type.equals(subscription.type))
                .collect(Collectors.toList());
            }
          }
          unsubscribeFromMarketData(symbol, unsubscriptions);
        }
        if (updates.size() != 0) {
          if (subscriptions.size() != 0) {
            for (MarketDataSubscription subscription : updates) {
              subscriptions.stream().filter(s -> s.type.equals(subscription.type))
                .forEach(s -> s.intervalInMilliseconds = subscription.intervalInMilliseconds);
            }
          }
          subscribeToMarketData(symbol, updates);
        }
        if (subscriptions.size() != 0) {
          this.subscriptions.remove(symbol);
        }
      });
    }
  
  /**
   * Returns list of the symbols connection is subscribed to
   * @return list of the symbols connection is subscribed to
   */
  public List<String> getSubscribedSymbols() {
    return new ArrayList<>(subscriptions.keySet());
  }
  
  /**
   * Returns subscriptions for a symbol
   * @param symbol symbol to retrieve subscriptions for
   * @return list of market data subscriptions for the symbol, or {@code null}
   */
  public List<MarketDataSubscription> getSubscriptions(String symbol) {
    if (subscriptions.containsKey(symbol)) {
      return subscriptions.get(symbol).subscriptions;
    } else {
      return null;
    }
  }
  
  /**
   * Retrieves available symbols for an account (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbols/).
   * @return completable future which resolves with specification retrieved
   */
  public CompletableFuture<List<String>> getSymbols() {
    return websocketClient.getSymbols(account.getId());
  }
  
  /**
   * Retrieves specification for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbolSpecification/).
   * @param symbol symbol to retrieve specification for
   * @return completable future which resolves with specification retrieved
   */
  public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(String symbol) {
    return websocketClient.getSymbolSpecification(account.getId(), symbol);
  }
  
  /**
   * Retrieves latest price for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbolPrice/).
   * @param symbol symbol to retrieve price for
   * @return completable future which resolves with price retrieved
   */
  public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(String symbol) {
    return websocketClient.getSymbolPrice(account.getId(), symbol);
  }
  
  /**
   * Retrieves latest candle for a symbol and timeframe (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readCandle/).
   * @param symbol symbol to retrieve candle for
   * @param timeframe defines the timeframe according to which the candle must be generated.
   * Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h,
   * 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @return completable future which resolves when candle is retrieved
   */
  public CompletableFuture<MetatraderCandle> getCandle(String symbol, String timeframe) {
    return websocketClient.getCandle(account.getId(), symbol, timeframe);
  }
  
  /**
   * Retrieves latest tick for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readTick/).
   * @param symbol symbol to retrieve tick for
   * @return completable future which resolves when tick is retrieved
   */
  public CompletableFuture<MetatraderTick> getTick(String symbol) {
    return websocketClient.getTick(account.getId(), symbol);
  }
  
  /**
   * Retrieves latest order book for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readBook/).
   * @param symbol symbol to retrieve order book for
   * @return completable future which resolves when order book is retrieved
   */
  public CompletableFuture<MetatraderBook> getBook(String symbol) {
    return websocketClient.getBook(account.getId(), symbol);
  }
  
  /**
   * Sends client uptime stats to the server.
   * @param uptime uptime statistics to send to the server
   * @return completable future which resolves when uptime statistics is submitted
   */
  public CompletableFuture<Void> saveUptime(Map<String, Double> uptime) {
    return websocketClient.saveUptime(account.getId(), uptime);
  }
  
  /**
   * Returns local copy of terminal state
   * @return local copy of terminal state
   */
  public TerminalState getTerminalState() {
    return terminalState;
  }
  
  /**
   * Returns local history storage
   * @return local history storage
   */
  public HistoryStorage getHistoryStorage() {
    return historyStorage;
  }
  
  /**
   * Adds synchronization listener
   * @param listener synchronization listener to add
   */
  public void addSynchronizationListener(SynchronizationListener listener)  {
    websocketClient.addSynchronizationListener(account.getId(), listener);
  }
  
  /**
   * Removes synchronization listener for specific account
   * @param listener synchronization listener to remove
   */
  public void removeSynchronizationListener(SynchronizationListener listener) {
    websocketClient.removeSynchronizationListener(account.getId(), listener);
  }
  
  @Override
  public CompletableFuture<Void> onConnected(int instanceIndex, int replicas) {
    return CompletableFuture.runAsync(() -> {
      String key = RandomStringUtils.randomAlphanumeric(32);
      State state = getState(instanceIndex);
      state.shouldSynchronize = key;
      state.synchronizationRetryIntervalInSeconds = 1;
      state.isSynchronized = false;
      ensureSynchronized(instanceIndex, key);
      List<Integer> indices = new ArrayList<>(replicas);
      for (int i = 0; i < replicas; i++) {
        indices.add(i);
      }
      Map<Integer, State> newStateByInstanceIndex = new HashMap<>();
      for (Entry<Integer, State> e : stateByInstanceIndex.entrySet()) {
        if (indices.indexOf(e.getValue().instanceIndex) != -1) {
          newStateByInstanceIndex.put(e.getKey(), e.getValue());
        }
      }
      stateByInstanceIndex = newStateByInstanceIndex;
    });
  }

  @Override
  public CompletableFuture<Void> onDisconnected(int instanceIndex) {
    State state = getState(instanceIndex);
    state.lastDisconnectedSynchronizationId = state.lastSynchronizationId;
    state.lastSynchronizationId = null;
    state.shouldSynchronize = "";
    state.isSynchronized = false;
    state.disconnected = true;
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onDealSynchronizationFinished(int instanceIndex,
    String synchronizationId) {
    State state = getState(instanceIndex);
    state.dealsSynchronized.add(synchronizationId);
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onOrderSynchronizationFinished(int instanceIndex,
    String synchronizationId) {
    State state = getState(instanceIndex);
    state.ordersSynchronized.add(synchronizationId);
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onReconnected() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      logger.error("Failed to sleep thread", e);
    }
    return subscribe();
  }
  
  /**
   * Returns flag indicating status of state synchronization with MetaTrader terminal
   * @param instanceIndex index of an account instance connected, or {@code null}
   * @param synchronizationId optional synchronization request id, last synchronization 
   * request id will be used by default, or {@code null}
   * @return completable future resolving with a flag indicating status of state synchronization
   * with MetaTrader terminal
   */
  public CompletableFuture<Boolean> isSynchronized(Integer instanceIndex, String synchronizationId) {
    boolean result = false;
    for (State s : stateByInstanceIndex.values()) {
      if (instanceIndex != null && s.instanceIndex != instanceIndex) {
        continue;
      }
      if (synchronizationId == null) {
        synchronizationId = s.lastSynchronizationId;
      }
      boolean isSynchronized = s.ordersSynchronized.contains(synchronizationId) 
        && s.dealsSynchronized.contains(synchronizationId);
      result = result || isSynchronized;
    }
    return CompletableFuture.completedFuture(result);
  }
  
  /**
   * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
   * if application failed to synchronize with the teminal withing timeout allowed.
   * @return completable future which resolves when synchronization to MetaTrader terminal is completed
   */
  public CompletableFuture<Void> waitSynchronized() {
    return waitSynchronized(null);
  }
  
  /**
   * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
   * if application failed to synchronize with the teminal withing timeout allowed.
   * @param options synchronization options, or {@code null}
   * @return completable future which resolves when synchronization to MetaTrader terminal is completed
   */
  public CompletableFuture<Void> waitSynchronized(SynchronizationOptions options) {
    if (options == null) options = new SynchronizationOptions();
    SynchronizationOptions opts = options;
    return CompletableFuture.runAsync(() -> {
      Integer instanceIndex = opts.instanceIndex;
      String synchronizationId = opts.synchronizationId;
      int timeoutInSeconds = (opts.timeoutInSeconds != null ? opts.timeoutInSeconds : 300);
      int intervalInMilliseconds = (opts.intervalInMilliseconds != null ? opts.intervalInMilliseconds : 1000);
      String applicationPattern = (opts.applicationPattern != null ? opts.applicationPattern :
        (account.getApplication().equals("CopyFactory") ? "CopyFactory.*|RPC" : "RPC"));
      long startTime = Instant.now().getEpochSecond();
      long timeoutTime = startTime + timeoutInSeconds;
      try {
        boolean isSynchronized;
        while (!(isSynchronized = isSynchronized(instanceIndex, synchronizationId).get())
          && timeoutTime > Instant.now().getEpochSecond()) {
          Thread.sleep(intervalInMilliseconds);
        };
        State state = null;
        if (instanceIndex == null) {
          for (State s : stateByInstanceIndex.values()) {
            if (isSynchronized(s.instanceIndex, synchronizationId).join()) {
              state = s;
              instanceIndex = s.instanceIndex;
            }
          }
        } else {
          Integer finalInstanceIndex = instanceIndex;
          state = stateByInstanceIndex.values().stream()
            .filter(s -> s.instanceIndex == finalInstanceIndex).findFirst().orElse(null);
        }
        if (!isSynchronized) {
          throw new TimeoutException("Timed out waiting for account MetApi to synchronize to MetaTrader account " 
            + account.getId() + ", synchronization id " + (
              synchronizationId != null ? synchronizationId
              : (state != null && state.lastSynchronizationId != null ? state.lastSynchronizationId 
                : (state != null && state.lastDisconnectedSynchronizationId != null
                  ? state.lastDisconnectedSynchronizationId : null))
            )
          );
        }
        websocketClient.waitSynchronized(account.getId(), instanceIndex, applicationPattern,
          (long) timeoutInSeconds).get();
      } catch (Exception e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Closes the connection. The instance of the class should no longer be used after this method is invoked.
   * @return completable future resolving when connection is closed
   */
  public CompletableFuture<Void> close() {
    if (!closed) {
      stateByInstanceIndex.clear();
      websocketClient.unsubscribe(account.getId()).join();
      websocketClient.removeSynchronizationListener(account.getId(), this);
      websocketClient.removeSynchronizationListener(account.getId(), terminalState);
      websocketClient.removeSynchronizationListener(account.getId(), historyStorage);
      websocketClient.removeSynchronizationListener(account.getId(), healthMonitor);
      connectionRegistry.remove(account.getId());
      healthMonitor.stop();
      closed = true;
    }
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns synchronization status
   * @return synchronization status
   */
  public boolean isSynchronized() {
    return stateByInstanceIndex.values().stream().filter(state -> state.isSynchronized).findFirst().isPresent();
  }
  
  /**
   * Returns MetaApi account
   * @return MetaApi account
   */
  public MetatraderAccount getAccount() {
    return account;
  }
  
  /**
   * Returns connection health monitor instance
   * @return connection health monitor instance
   */
  public ConnectionHealthMonitor getHealthMonitor() {
    return healthMonitor;
  }
  
  private void ensureSynchronized(int instanceIndex, String key) {
    State state = getState(instanceIndex);
    if (state != null) {
      try {
        synchronize(instanceIndex).join();
        for (String symbol : subscriptions.keySet()) {
          subscribeToMarketData(symbol, subscriptions.get(symbol).subscriptions, instanceIndex);
        }
        state.isSynchronized = true;
        state.synchronizationRetryIntervalInSeconds = 1;
      } catch (CompletionException e) {
        logger.error("MetaApi websocket client for account " + account.getId() + ":"
          + instanceIndex + " failed to synchronize", e.getCause());
        if (state.shouldSynchronize.equals(key)) {
          Timer retryTimer = new Timer();
          MetaApiConnection self = this;
          retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
              self.ensureSynchronized(instanceIndex, key);
            }
          }, 1000 * state.synchronizationRetryIntervalInSeconds);
          state.synchronizationRetryIntervalInSeconds = Math.min(
            state.synchronizationRetryIntervalInSeconds * 2, 300);
        }
      }
    }
  }
  
  private State getState(int instanceIndex) {
    if (!stateByInstanceIndex.containsKey(instanceIndex)) {
      int stateInstanceIndex = instanceIndex;
      stateByInstanceIndex.put(instanceIndex, new State() {{
        instanceIndex = stateInstanceIndex;
        synchronizationRetryIntervalInSeconds = 1;
      }});
    }
    return stateByInstanceIndex.get(instanceIndex);
  }
  
  private void copyModelProperties(Object source, Object target) {
    Field[] publicFields = source.getClass().getFields();
    for (int i = 0; i < publicFields.length; ++i) {
      Field sourceField = publicFields[i];
      try {
        Field targetField = target.getClass().getField(sourceField.getName());
        targetField.set(target, sourceField.get(source));
      } catch (NoSuchFieldException e) {
        // Just pass this field
      } catch (Exception e) {
        logger.error("Cannot copy model property " + sourceField.getName(), e);
      }
    }
  }
}