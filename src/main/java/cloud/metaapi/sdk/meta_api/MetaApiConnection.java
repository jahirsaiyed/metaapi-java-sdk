package cloud.metaapi.sdk.meta_api;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.ReconnectListener;
import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.TradeException;
import cloud.metaapi.sdk.clients.meta_api.models.MarketTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeals;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderHistoryOrders;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.meta_api.models.PendingTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade.ActionType;
import cloud.metaapi.sdk.clients.models.*;

/**
 * Exposes MetaApi MetaTrader API connection to consumers
 */
public class MetaApiConnection extends SynchronizationListener implements ReconnectListener {

    private static Logger logger = Logger.getLogger(MetaApiConnection.class);
    private MetaApiWebsocketClient websocketClient;
    private MetatraderAccount account;
    private HashSet<String> ordersSynchronized = new HashSet<>();
    private HashSet<String> dealsSynchronized = new HashSet<>();
    private String lastSynchronizationId = null;
    private TerminalState terminalState = null;
    private HistoryStorage historyStorage = null;
    
    /**
     * Constructs MetaApi MetaTrader Api connection
     * @param websocketClient MetaApi websocket client
     * @param account MetaTrader account to connect to
     * @param historyStorage terminal history storage or {@code null}. By default an instance of MemoryHistoryStorage
     * will be used.
     */
    public MetaApiConnection(
        MetaApiWebsocketClient websocketClient, MetatraderAccount account, HistoryStorage historyStorage
    ) {
        this.websocketClient = websocketClient;
        this.account = account;
        this.terminalState = new TerminalState();
        this.historyStorage = historyStorage != null ? historyStorage : new MemoryHistoryStorage(account.getId());
        websocketClient.addSynchronizationListener(account.getId(), this);
        websocketClient.addSynchronizationListener(account.getId(), this.terminalState);
        websocketClient.addSynchronizationListener(account.getId(), this.historyStorage);
        websocketClient.addReconnectListener(this);
    }
    
    /**
     * Returns account information (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
     * @returns completable future resolving with account information
     */
    public CompletableFuture<MetatraderAccountInformation> getAccountInformation() {
        return websocketClient.getAccountInformation(account.getId());
    }
    
    /**
     * Returns positions (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPositions/).
     * @returns completable future resolving with array of open positions
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
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTicket(String ticket) {
        return websocketClient.getHistoryOrdersByTicket(account.getId(), ticket);
    }
    
    /**
     * Returns the history of completed orders for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByPosition/)
     * @param positionId position id
     * @returns completable future resolving with request results containing history orders found
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
     * @returns completable future resolving with request results containing history orders found
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
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTicket(String ticket) {
        return websocketClient.getDealsByTicket(account.getId(), ticket);
    }
    
    /**
     * Returns history deals for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByPosition/).
     * @param positionId position id
     * @returns completable future resolving with request results containing deals found
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
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
        IsoTime startTime, IsoTime endTime, int offset, int limit
    ) {
        return websocketClient.getDealsByTimeRange(account.getId(), startTime, endTime, offset, limit);
    }
    
    /**
     * Clears the order and transaction history of a specified account so that it can be synchronized from scratch 
     * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
     * @return completable future resolving when the history is cleared
     */
    public CompletableFuture<Void> removeHistory() {
        historyStorage.reset();
        return websocketClient.removeHistory(account.getId());
    }
    
    /**
     * Creates a market buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param stopLoss optional stop loss price or {@code null}
     * @param takeProfit optional take profit price or {@code null}
     * @param options optional trade options or {@code null}
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
     * check error properties for error code details
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
     * Modifies a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param positionId position id to modify
     * @param stopLoss optional stop loss price or {@code null}
     * @param takeProfit optional take profit price or {@code null}
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * Closes position by a symbol (see https://metaapi.cloud/docs/client/websocket/api/trade/)
     * @param symbol symbol to trade
     * @param options optional trade options or {@code null}
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future resolving with trade result or completing exceptionally with {@link TradeException},
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
     * @returns completable future which resolves when reconnection started
     */
    public CompletableFuture<Void> reconnect() {
        return websocketClient.reconnect(account.getId());
    }
    
    /**
     * Requests the terminal to start synchronization process
     * (see https://metaapi.cloud/docs/client/websocket/synchronizing/synchronize/)
     * @returns completable future which resolves when synchronization started
     */
    public CompletableFuture<Void> synchronize() {
        return CompletableFuture.runAsync(() -> {
            IsoTime startingHistoryOrderTime = historyStorage.getLastHistoryOrderTime().join();
            IsoTime startingDealTime = historyStorage.getLastDealTime().join();
            lastSynchronizationId = UUID.randomUUID().toString();
            websocketClient.synchronize(
                account.getId(), lastSynchronizationId,
                startingHistoryOrderTime, startingDealTime
            ).join();
        });
    }
    
    /**
     * Initializes meta api connection
     * @return completable future which resolves when meta api connection is initialized
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            historyStorage.loadData().join();
        });
    }
    
    /**
     * Initiates subscription to MetaTrader terminal
     * @returns completable future which resolves when subscription is initiated
     */
    public CompletableFuture<Void> subscribe() {
        return websocketClient.subscribe(account.getId());
    }
    
    /**
     * Subscribes on market data of specified symbol (see
     * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
     * @param symbol symbol (e.g. currency pair or an index)
     * @returns completable future which resolves when subscription request was processed
     */
    public CompletableFuture<Void> subscribeToMarketData(String symbol) {
        return CompletableFuture.runAsync(() -> {
            websocketClient.subscribeToMarketData(account.getId(), symbol).join();
        });
    }
    
    /**
     * Retrieves specification for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolSpecification/).
     * @param symbol symbol to retrieve specification for
     * @returns completable future which resolves with specification retrieved
     */
    public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(String symbol) {
        return websocketClient.getSymbolSpecification(account.getId(), symbol);
    }
    
    /**
     * Retrieves specification for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolPrice/).
     * @param symbol symbol to retrieve price for
     * @returns completable future which resolves with price retrieved
     */
    public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(String symbol) {
        return websocketClient.getSymbolPrice(account.getId(), symbol);
    }
    
    /**
     * Returns local copy of terminal state
     * @returns local copy of terminal state
     */
    public TerminalState getTerminalState() {
        return terminalState;
    }
    
    /**
     * Returns local history storage
     * @returns local history storage
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
    public CompletableFuture<Void> onConnected() {
        try {
            return synchronize();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public CompletableFuture<Void> onDisconnected() {
        lastSynchronizationId = null;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onDealSynchronizationFinished(String synchronizationId) {
        return CompletableFuture.runAsync(() -> {
            dealsSynchronized.add(synchronizationId);
            historyStorage.updateStorage().join();
        });
    }
    
    @Override
    public CompletableFuture<Void> onOrderSynchronizationFinished(String synchronizationId) {
        ordersSynchronized.add(synchronizationId);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onReconnected() {
        try {
            return subscribe();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }
    
    /**
     * Returns flag indicating status of state synchronization with MetaTrader terminal
     * @param synchronizationId optional synchronization request id, last synchronization 
     * request id will be used by default
     * @return completable future resolving with a flag indicating status of state synchronization
     * with MetaTrader terminal
     */
    public CompletableFuture<Boolean> isSynchronized(String synchronizationId) {
        if (synchronizationId == null) synchronizationId = lastSynchronizationId;
        String finalSynchronizationId = synchronizationId;
        return CompletableFuture.supplyAsync(() -> {
            if (   ordersSynchronized.contains(finalSynchronizationId) 
                && dealsSynchronized.contains(finalSynchronizationId))
            {
                try {
                    return !getDealsByTimeRange(
                        new IsoTime(Date.from(Instant.now())),
                        new IsoTime(Date.from(Instant.now())), 0, 1000
                    ).get().synchronizing;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TimeoutException) return false;
                    throw new CompletionException(e.getCause());
                } catch (InterruptedException e) {
                    throw new CompletionException(e);
                }

            }
            return false;
        });
    }
    
    /**
     * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
     * if application failed to synchronize with the teminal withing timeout allowed. Last synchronization request
     * id will be used as synchronization id. Wait timeout in seconds is 5m and interval between account reloads
     * while waiting for a change is 1s.
     * @return completable future which resolves when synchronization to MetaTrader terminal is completed
     */
    public CompletableFuture<Void> waitSynchronized() {
        return waitSynchronized(null, null, null);
    }
    
    /**
     * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
     * if application failed to synchronize with the teminal withing timeout allowed. Wait timeout in seconds is 5m
     * and interval between account reloads while waiting for a change is 1s.
     * @param synchronizationId optional synchronization id, last synchronization request id will be used by default
     * @return completable future which resolves when synchronization to MetaTrader terminal is completed
     */
    public CompletableFuture<Void> waitSynchronized(String synchronizationId) {
        return waitSynchronized(synchronizationId, null, null);
    }
    
    /**
     * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
     * if application failed to synchronize with the teminal withing timeout allowed.
     * @param synchronizationId optional synchronization id, last synchronization request id will be used by default
     * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
     * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
     * @return completable future which resolves when synchronization to MetaTrader terminal is completed
     */
    public CompletableFuture<Void> waitSynchronized(
        String synchronizationId, Integer timeoutInSeconds, Integer intervalInMilliseconds
    ) {
        long startTime = Instant.now().getEpochSecond();
        long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
        return CompletableFuture.runAsync(() -> {
            try {
                while (!isSynchronized(synchronizationId).get() && timeoutTime > Instant.now().getEpochSecond()) {
                    Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
                };
                if (!isSynchronized(synchronizationId).get()) throw new TimeoutException(
                    "Timed out waiting for account MetApi to synchronize to MetaTrader account " 
                    + account.getId() + ", synchronization id " + (synchronizationId != null
                        ? synchronizationId : lastSynchronizationId)
                );
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Closes the connection. The instance of the class should no longer be used after this method is invoked.
     */
    public void close() {
        websocketClient.removeSynchronizationListener(account.getId(), this);
        websocketClient.removeSynchronizationListener(account.getId(), terminalState);
        websocketClient.removeSynchronizationListener(account.getId(), historyStorage);
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