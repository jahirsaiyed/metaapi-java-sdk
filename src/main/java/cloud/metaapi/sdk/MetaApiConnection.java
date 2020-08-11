package cloud.metaapi.sdk;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import cloud.metaapi.sdk.clients.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.ReconnectListener;
import cloud.metaapi.sdk.clients.SynchronizationListener;
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.clients.models.MetatraderTrade.ActionType;

/**
 * Exposes MetaApi MetaTrader API connection to consumers
 */
public class MetaApiConnection extends SynchronizationListener implements ReconnectListener {

    private MetaApiWebsocketClient websocketClient;
    private MetatraderAccount account;
    private boolean isSynchronized = false;
    private Optional<TerminalState> terminalState;
    private Optional<HistoryStorage> historyStorage;
    
    /**
     * Constructs MetaApi MetaTrader Api connection
     * @param websocketClient MetaApi websocket client
     * @param account MetaTrader account to connect to
     * @param local terminal history storage. Use for accounts in user synchronization mode. By default
     * an instance of MemoryHistoryStorage will be used.
     */
    public MetaApiConnection(
        MetaApiWebsocketClient websocketClient,
        MetatraderAccount account,
        Optional<HistoryStorage> historyStorage
    ) {
        this.websocketClient = websocketClient;
        this.account = account;
        if (account.getSynchronizationMode().equals("user")) {
            this.terminalState = Optional.of(new TerminalState());
            this.historyStorage = Optional.of(historyStorage.orElseGet(() -> new MemoryHistoryStorage()));
            websocketClient.addSynchronizationListener(account.getId(), this);
            websocketClient.addSynchronizationListener(account.getId(), this.terminalState.get());
            websocketClient.addSynchronizationListener(account.getId(), this.historyStorage.get());
            websocketClient.addReconnectListener(this);
        } else {
            this.terminalState = Optional.empty();
            this.historyStorage = Optional.empty();
        }
    }
    
    /**
     * Returns account information (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
     * @returns completable future resolving with account information
     */
    public CompletableFuture<MetatraderAccountInformation> getAccountInformation() throws Exception {
        return websocketClient.getAccountInformation(account.getId());
    }
    
    /**
     * Returns positions (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPositions/).
     * @returns completable future resolving with array of open positions
     */
    public CompletableFuture<List<MetatraderPosition>> getPositions() throws Exception {
        return websocketClient.getPositions(account.getId());
    }
    
    /**
     * Returns specific position (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPosition/).
     * @param positionId position id
     * @return completable future resolving with MetaTrader position found
     */
    public CompletableFuture<MetatraderPosition> getPosition(String positionId) throws Exception {
        return websocketClient.getPosition(account.getId(), positionId);
    }
    
    /**
     * Returns open orders (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrders/).
     * @return completable future resolving with open MetaTrader orders
     */
    public CompletableFuture<List<MetatraderOrder>> getOrders() throws Exception {
        return websocketClient.getOrders(account.getId());
    }
    
    /**
     * Returns specific open order (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrder/).
     * @param orderId order id (ticket number)
     * @return completable future resolving with metatrader order found
     */
    public CompletableFuture<MetatraderOrder> getOrder(String orderId) throws Exception {
        return websocketClient.getOrder(account.getId(), orderId);
    }
    
    /**
     * Returns the history of completed orders for a specific ticket number (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByTicket/).
     * @param ticket ticket number (order id)
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTicket(String ticket) throws Exception {
        return websocketClient.getHistoryOrdersByTicket(account.getId(), ticket);
    }
    
    /**
     * Returns the history of completed orders for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByPosition/)
     * @param positionId position id
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByPosition(String positionId) throws Exception {
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
    ) throws Exception {
        return websocketClient.getHistoryOrdersByTimeRange(account.getId(), startTime, endTime, offset, limit);
    }
    
    /**
     * Returns history deals with a specific ticket number (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByTicket/).
     * @param ticket ticket number (deal id for MT5 or order id for MT4)
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTicket(String ticket) throws Exception {
        return websocketClient.getDealsByTicket(account.getId(), ticket);
    }
    
    /**
     * Returns history deals for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByPosition/).
     * @param positionId position id
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByPosition(String positionId) throws Exception {
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
    ) throws Exception {
        return websocketClient.getDealsByTimeRange(account.getId(), startTime, endTime, offset, limit);
    }
    
    /**
     * Clears the order and transaction history of a specified account so that it can be synchronized from scratch 
     * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
     * @return completable future resolving when the history is cleared
     */
    public CompletableFuture<Void> removeHistory() throws Exception {
        return websocketClient.removeHistory(account.getId());
    }
    
    /**
     * Creates a market buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createMarketBuyOrder(
        String symbol, double volume, Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Creates a market sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createMarketSellOrder(
        String symbol, double volume, Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Creates a limit buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param openPrice order limit price
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createLimitBuyOrder(
        String symbol, double volume, double openPrice,
        Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY_LIMIT;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.openPrice = Optional.of(openPrice);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Creates a limit sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param openPrice order limit price
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createLimitSellOrder(
        String symbol, double volume, double openPrice,
        Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL_LIMIT;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.openPrice = Optional.of(openPrice);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Creates a stop buy order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param openPrice order stop price
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createStopBuyOrder(
        String symbol, double volume, double openPrice,
        Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY_STOP;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.openPrice = Optional.of(openPrice);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Creates a stop sell order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param volume order volume
     * @param openPrice order stop price
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> createStopSellOrder(
        String symbol, double volume, double openPrice,
        Optional<Double> stopLoss, Optional<Double> takeProfit,
        Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL_STOP;
        trade.symbol = Optional.of(symbol);
        trade.volume = Optional.of(volume);
        trade.openPrice = Optional.of(openPrice);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Modifies a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param positionId position id to modify
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> modifyPosition(
        String positionId, Optional<Double> stopLoss, Optional<Double> takeProfit
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_MODIFY;
        trade.positionId = Optional.of(positionId);
        trade.stopLoss = stopLoss;
        trade.takeProfit = takeProfit;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Partially closes a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param positionId position id to modify
     * @param volume volume to close
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> closePositionPartially(
        String positionId, double volume, Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_PARTIAL;
        trade.positionId = Optional.of(positionId);
        trade.volume = Optional.of(volume);
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Fully closes a position (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param positionId position id to modify
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> closePosition(
        String positionId, Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_CLOSE_ID;
        trade.positionId = Optional.of(positionId);
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Closes position by a symbol. Available on MT5 netting accounts only. (see
     * https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param symbol symbol to trade
     * @param comment optional order comment. The sum of the line lengths of the comment and the clientId
     * must be less than or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     * @param clientId optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. You can use this field to bind
     * your trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> closePositionBySymbol(
        String symbol, Optional<String> comment, Optional<String> clientId
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_CLOSE_SYMBOL;
        trade.symbol = Optional.of(symbol);
        trade.comment = comment;
        trade.clientId = clientId;
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Modifies a pending order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param orderId order id (ticket number)
     * @param openPrice order stop price
     * @param stopLoss optional stop loss price
     * @param takeProfit optional take profit price
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> modifyOrder(
        String orderId, double openPrice, double stopLoss, double takeProfit
    ) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_MODIFY;
        trade.orderId = Optional.of(orderId);
        trade.openPrice = Optional.of(openPrice);
        trade.stopLoss = Optional.of(stopLoss);
        trade.takeProfit = Optional.of(takeProfit);
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Cancels order (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param orderId order id (ticket number)
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> cancelOrder(String orderId) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_CANCEL;
        trade.orderId = Optional.of(orderId);
        return websocketClient.trade(account.getId(), trade);
    }
    
    /**
     * Reconnects to the Metatrader terminal (see https://metaapi.cloud/docs/client/websocket/api/reconnect/).
     * @returns completable future which resolves when reconnection started
     */
    public CompletableFuture<Void> reconnect() throws Exception {
        return websocketClient.reconnect(account.getId());
    }
    
    /**
     * Requests the terminal to start synchronization process. Use it if user synchronization mode is set to user for the
     * account (see https://metaapi.cloud/docs/client/websocket/synchronizing/synchronize/). Use only for user
     * synchronization mode.
     * @returns completable future which resolves when synchronization started
     */
    public CompletableFuture<Void> synchronize() throws Exception {
        if (!account.getSynchronizationMode().equals("user")) return CompletableFuture.completedFuture(null);
        Optional<IsoTime> startingHistoryOrderTime = Optional.of(historyStorage.get().getLastHistoryOrderTime().get());
        Optional<IsoTime> startingDealTime = Optional.of(historyStorage.get().getLastDealTime().get());
        return websocketClient.synchronize(account.getId(), startingHistoryOrderTime, startingDealTime);
    }
    
    /**
     * Initiates subscription to MetaTrader terminal
     * @returns completable future which resolves when subscription is initiated
     */
    public CompletableFuture<Void> subscribe() throws Exception {
        return websocketClient.subscribe(account.getId());
    }
    
    /**
     * Subscribes on market data of specified symbol (see
     * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
     * @param symbol symbol (e.g. currency pair or an index)
     * @returns completable future which resolves when subscription request was processed
     */
    public CompletableFuture<Void> subscribeToMarketData(String symbol) throws Exception {
        return websocketClient.subscribeToMarketData(account.getId(), symbol);
    }
    
    /**
     * Retrieves specification for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolSpecification/).
     * @param symbol symbol to retrieve specification for
     * @returns completable future which resolves with specification retrieved
     */
    public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(String symbol) throws Exception {
        return websocketClient.getSymbolSpecification(account.getId(), symbol);
    }
    
    /**
     * Retrieves specification for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolPrice/).
     * @param symbol symbol to retrieve price for
     * @returns completable future which resolves with price retrieved
     */
    public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(String symbol) throws Exception {
        return websocketClient.getSymbolPrice(account.getId(), symbol);
    }
    
    /**
     * Returns local copy of terminal state. Use this method for accounts in user synchronization mode
     * @returns local copy of terminal state
     */
    public Optional<TerminalState> getTerminalState() {
        return terminalState;
    }
    
    /**
     * Returns local history storage. Use this method for accounts in user synchronization mode
     * @returns local history storage
     */
    public Optional<HistoryStorage> getHistoryStorage() {
        return historyStorage;
    }
    
    /**
     * Adds synchronization listener. Use this method for accounts in user synchronization mode
     * @param listener synchronization listener to add
     */
    public void addSynchronizationListener(SynchronizationListener listener) {
        if (account.getSynchronizationMode().equals("user")) {
            websocketClient.addSynchronizationListener(account.getId(), listener);
        }
    }
    
    /**
     * Removes synchronization listener for specific account. Use this method for accounts in user synchronization mode
     * @param listener synchronization listener to remove
     */
    public void removeSynchronizationListener(SynchronizationListener listener) {
        if (account.getSynchronizationMode().equals("user")) {
            websocketClient.removeSynchronizationListener(account.getId(), listener);
        }
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
        isSynchronized = false;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onDealSynchronizationFinished() {
        isSynchronized = true;
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
     * @return completable future resolving with a flag indicating status of state synchronization with MetaTrader
     * terminal
     */
    public CompletableFuture<Boolean> isSynchronized() throws Exception {
        if (account.getSynchronizationMode().equals("user")) return CompletableFuture.completedFuture(isSynchronized);
        return CompletableFuture.completedFuture(
            getDealsByTimeRange(new IsoTime(Date.from(Instant.now())), new IsoTime(Date.from(Instant.now())), 0, 1000)
        .get().synchronizing);
    }
    
    /**
     * Waits until synchronization to MetaTrader terminal is completed. Completes exceptionally with TimeoutError 
     * if application failed to synchronize with the teminal withing timeout allowed.
     * @param timeoutInSeconds wait timeout in seconds
     * @param intervalInMilliseconds interval between account reloads while waiting for a change
     * @return completable future which resolves when synchronization to MetaTrader terminal is completed
     */
    public CompletableFuture<Void> waitSynchronized(int timeoutInSeconds, int intervalInMilliseconds) {
        long startTime = Instant.now().getEpochSecond();
        long timeoutTime = startTime + timeoutInSeconds;
        return CompletableFuture.runAsync(() -> {
            try {
                while (!isSynchronized().get() && timeoutTime > Instant.now().getEpochSecond()) {
                    Thread.sleep(intervalInMilliseconds);
                };
                if (!isSynchronized().get()) throw new TimeoutException(
                    "Timed out waiting for account MetApi to synchronize to MetaTrader account " + account.getId()
                );
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Closes the connection. The instance of the class should no longer be used after this method is invoked.
     */
    void close() {
        if (account.getSynchronizationMode().equals("user")) {
            websocketClient.removeSynchronizationListener(account.getId(), this);
            websocketClient.removeSynchronizationListener(account.getId(), terminalState.get());
            websocketClient.removeSynchronizationListener(account.getId(), historyStorage.get());
        }
    }
}