package metaapi.cloudsdk.lib.clients;

import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.RandomStringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.socket.client.IO;
import io.socket.client.Socket;
import metaapi.cloudsdk.lib.clients.errorHandler.*;
import metaapi.cloudsdk.lib.clients.errorHandler.InternalException;
import metaapi.cloudsdk.lib.clients.models.*;

/**
 * MetaApi websocket API client (see https://metaapi.cloud/docs/client/websocket/overview/)
 */
public class MetaApiWebsocketClient {

    private String url;
    private String token;
    private Socket socket;
    private ObjectMapper jsonMapper = JsonMapper.getInstance();
    private Map<String, CompletableFuture<JsonNode>> requestResolves;
    private boolean isSocketConnecting = false;
    private boolean connected = false;
    
    /**
     * Constructs MetaApi websocket API client instance
     * with default domain agiliumtrade.agiliumtrade.ai
     * @param token authorization token
     */
    public MetaApiWebsocketClient(String token) {
        this(token, "agiliumtrade.agiliumtrade.ai");
    }
    
    /**
     * @see MetaApiWebsocketClient#MetaApiWebsocketClient(String)
     * @param domain domain to connect to
     */
    public MetaApiWebsocketClient(String token, String domain) {
        this.url = "https://mt-client-api-v1." + domain;
        this.token = token;
        this.requestResolves = new HashMap<>();
    }
    
    /**
     * Patch server URL for use in unit tests
     * @param url patched server URL
     */
    public void setUrl(String url) {
        this.url = url;
    }
    
    /**
     * Connects to MetaApi server via socket.io protocol
     * @returns completable future which resolves when connection is established
     */
    public CompletableFuture<Void> connect() throws URISyntaxException {
        if (connected) return null;
        connected = true;
        requestResolves.clear();
        CompletableFuture<Void> result = new CompletableFuture<>();
        String url = this.url + "?auth-token=" + token;
        IO.Options socketOptions = new IO.Options();
        socketOptions.path = "/ws";
        socketOptions.reconnection = true;
        socketOptions.reconnectionDelay = 1000;
        socketOptions.reconnectionDelayMax = 5000;
        socketOptions.reconnectionAttempts = Integer.MAX_VALUE;
        isSocketConnecting = true;
        socket = IO.socket(url, socketOptions);
        socket.on(Socket.EVENT_CONNECT, (Object[] args) -> {
            isSocketConnecting = false;
            logMessage("MetaApi websocket client connected to the MetaApi server");
            if (!result.isDone()) result.complete(null);
            else fireReconnected();
            if (!connected) socket.close();
        });
        socket.on(Socket.EVENT_RECONNECT, (Object[] args) -> {
            fireReconnected();
        });
        socket.on(Socket.EVENT_CONNECT_ERROR, (Object[] args) -> {
            Exception error = (Exception) args[0];
            logMessage("MetaApi websocket client connection error " + error);
            if (!result.isDone()) result.completeExceptionally(error);
        });
        socket.on(Socket.EVENT_CONNECT_TIMEOUT, (Object[] args) -> {
            logMessage("MetaApi websocket client connection timeout");
            if (!result.isDone()) result.completeExceptionally(
                new TimeoutException("MetaApi websocket client connection timed out")
            );
        });
        socket.on(Socket.EVENT_DISCONNECT, (Object[] args) -> {
            String reason = (String) args[0];
            logMessage("MetaApi websocket client disconnected from the MetaApi server because of " + reason);
            try {
                reconnect();
            } catch (Exception e) {
                logMessage("MetaApi websocket reconnect error " + e);
            }
        });
        socket.on(Socket.EVENT_ERROR, (Object[] args) -> {
            Exception error = (Exception) args[0];
            logMessage("MetaApi websocket client error " + error);
            try {
                reconnect();
            } catch (Exception e) {
                logMessage("MetaApi websocket reconnect error " + e);
            }
        });
        socket.on("response", (Object[] args) -> {
            try {
                JsonNode data = jsonMapper.readTree((String) args[0]);
                CompletableFuture<JsonNode> requestResolve = requestResolves.remove(data.get("requestId").asText());
                if (requestResolve != null) requestResolve.complete(data);
            } catch (JsonProcessingException e) {
                logMessage("MetaApi websocket parse json response error " + e + " with response " + args[0]);
            }
        });
        socket.on("processingError", (Object[] args) -> {
            try {
                WebsocketError error = jsonMapper.readValue((String) args[0], WebsocketError.class);
                CompletableFuture<JsonNode> requestResolve = requestResolves.remove(error.requestId);
                if (requestResolve != null) requestResolve.completeExceptionally(convertError(error));
            } catch (JsonProcessingException e) {
                logMessage("MetaApi websocket parse processingError data error " + e + " with response " + args[0]);
            }
        });
        socket.connect();
        return result;
    }
    
    /**
     * Closes connection to MetaApi server
     */
    public void close() {
        if (!connected) return;
        isSocketConnecting = false;
        connected = false;
        socket.close();
        requestResolves.values().forEach((CompletableFuture<JsonNode> resolve) -> {
            resolve.completeExceptionally(new Exception("MetaApi connection closed"));
        });
        requestResolves.clear();
    }
    
    /**
     * Returns account information for a specified MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
     * @param accountId id of the MetaTrader account to return information for
     * @returns completable future resolving with account information
     */
    public CompletableFuture<MetatraderAccountInformation> getAccountInformation(String accountId) throws Exception {
        CompletableFuture<MetatraderAccountInformation> result = new CompletableFuture<>();
        ObjectNode request = JsonMapper.getInstance().createObjectNode();
        request.put("type", "getAccountInformation");
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(
                    response.get("accountInformation"),
                    MetatraderAccountInformation.class
                ));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e); 
            }
        });
        return result;
    }
    
    /**
     * Returns positions for a specified MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPositions/).
     * @param accountId id of the MetaTrader account to return information for
     * @returns completable future resolving with list of open positions
     */
    public CompletableFuture<List<MetatraderPosition>> getPositions(String accountId) throws Exception {
        CompletableFuture<List<MetatraderPosition>> result = new CompletableFuture<>();
        ObjectNode request = JsonMapper.getInstance().createObjectNode();
        request.put("type", "getPositions");
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(Arrays.asList(jsonMapper
                    .treeToValue(response.get("positions"), MetatraderPosition[].class
                )));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns specific position for a MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readPosition/).
     * @param accountId id of the MetaTrader account to return information for
     * @param positionId position id
     * @return completable future resolving with MetaTrader position found
     */
    public CompletableFuture<MetatraderPosition> getPosition(String accountId, String positionId) throws Exception {
        CompletableFuture<MetatraderPosition> result = new CompletableFuture<>();
        ObjectNode request = JsonMapper.getInstance().createObjectNode();
        request.put("type", "getPosition");
        request.put("positionId", positionId);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(response.get("position"), MetatraderPosition.class));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns open orders for a specified MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrders/).
     * @param accountId id of the MetaTrader account to return information for
     * @return completable future resolving with open MetaTrader orders
     */
    public CompletableFuture<List<MetatraderOrder>> getOrders(String accountId) throws Exception {
        CompletableFuture<List<MetatraderOrder>> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getOrders");
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(
                    Arrays.asList(jsonMapper.treeToValue(response.get("orders"), MetatraderOrder[].class
                )));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns specific open order for a MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrder/).
     * @param accountId id of the MetaTrader account to return information for
     * @param orderId order id (ticket number)
     * @return completable future resolving with metatrader order found
     */
    public CompletableFuture<MetatraderOrder> getOrder(String accountId, String orderId) throws Exception {
        CompletableFuture<MetatraderOrder> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getOrder");
        request.put("orderId", orderId);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(response.get("order"), MetatraderOrder.class));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns the history of completed orders for a specific ticket number (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByTicket/).
     * @param accountId id of the MetaTrader account to return information for
     * @param ticket ticket number (order id)
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTicket(
        String accountId, String ticket
    ) throws Exception {
        CompletableFuture<MetatraderHistoryOrders> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getHistoryOrdersByTicket");
        request.put("ticket", ticket);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderHistoryOrders history = new MetatraderHistoryOrders();
                history.historyOrders = Arrays.asList(jsonMapper
                    .treeToValue(response.get("historyOrders"), MetatraderOrder[].class));
                history.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(history);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns the history of completed orders for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByPosition/)
     * @param accountId id of the MetaTrader account to return information for
     * @param positionId position id
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByPosition(
        String accountId, String positionId
    ) throws Exception {
        CompletableFuture<MetatraderHistoryOrders> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getHistoryOrdersByPosition");
        request.put("positionId", positionId);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderHistoryOrders history = new MetatraderHistoryOrders();
                history.historyOrders = Arrays.asList(jsonMapper
                    .treeToValue(response.get("historyOrders"), MetatraderOrder[].class));
                history.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(history);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns the history of completed orders for a specific time range (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readHistoryOrdersByTimeRange/).
     * By default pagination offset is set to 0 and pagination limit is set to 1000.
     * @param accountId id of the MetaTrader account to return information for
     * @param startTime start of time range, inclusive
     * @param endTime end of time range, exclusive
     * @returns completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime
    ) throws Exception {
        return getHistoryOrdersByTimeRange(accountId, startTime, endTime, 0, 1000);
    }
    
    /**
     * @see #getHistoryOrdersByTimeRange(String, IsoTime, IsoTime)
     * @param offset pagination offset
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset
    ) throws Exception {
        return getHistoryOrdersByTimeRange(accountId, startTime, endTime, offset, 1000);
    }
    
    /**
     * @see #getHistoryOrdersByTimeRange(String, IsoTime, IsoTime, int)
     * @param limit pagination limit
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset, int limit
    ) throws Exception {
        CompletableFuture<MetatraderHistoryOrders> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getHistoryOrdersByTimeRange");
        request.put("startTime", startTime.getIsoString());
        request.put("endTime", endTime.getIsoString());
        request.put("offset", offset);
        request.put("limit", limit);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderHistoryOrders history = new MetatraderHistoryOrders();
                history.historyOrders = Arrays.asList(jsonMapper
                    .treeToValue(response.get("historyOrders"), MetatraderOrder[].class));
                history.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(history);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns history deals with a specific ticket number (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByTicket/).
     * @param accountId id of the MetaTrader account to return information for
     * @param ticket ticket number (deal id for MT5 or order id for MT4)
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTicket(String accountId, String ticket) throws Exception {
        CompletableFuture<MetatraderDeals> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getDealsByTicket");
        request.put("ticket", ticket);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderDeals deals = new MetatraderDeals();
                deals.deals = Arrays.asList(jsonMapper
                    .treeToValue(response.get("deals"), MetatraderDeal[].class));
                deals.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(deals);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns history deals for a specific position id (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByPosition/).
     * @param accountId id of the MetaTrader account to return information for
     * @param positionId position id
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByPosition(String accountId, String positionId) throws Exception {
        CompletableFuture<MetatraderDeals> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getDealsByPosition");
        request.put("positionId", positionId);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderDeals deals = new MetatraderDeals();
                deals.deals = Arrays.asList(jsonMapper
                    .treeToValue(response.get("deals"), MetatraderDeal[].class));
                deals.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(deals);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Returns history deals with for a specific time range (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveHistoricalData/readDealsByTimeRange/).
     * By default pagination offset is set to 0 and pagination limit is set to 1000.
     * @param accountId id of the MetaTrader account to return information for
     * @param startTime start of time range, inclusive
     * @param endTime end of time range, exclusive
     * @returns completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime
    ) throws Exception {
        return getDealsByTimeRange(accountId, startTime, endTime, 0, 1000);
    }
    
    /**
     * @see #getDealsByTimeRange(String, IsoTime, IsoTime)
     * @param offset pagination offset
     */
    public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset
    ) throws Exception {
        return getDealsByTimeRange(accountId, startTime, endTime, offset, 1000);
    }
    
    /**
     * @see #getDealsByTimeRange(String, IsoTime, IsoTime, int)
     * @param limit pagination limit
     */
    public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset, int limit
    ) throws Exception {
        CompletableFuture<MetatraderDeals> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getDealsByTimeRange");
        request.put("startTime", startTime.getIsoString());
        request.put("endTime", endTime.getIsoString());
        request.put("offset", offset);
        request.put("limit", limit);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                MetatraderDeals deals = new MetatraderDeals();
                deals.deals = Arrays.asList(jsonMapper
                    .treeToValue(response.get("deals"), MetatraderDeal[].class));
                deals.synchronizing = response.get("synchronizing").asBoolean();
                return result.complete(deals);
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Clears the order and transaction history of a specified account so that it can be synchronized from scratch (see
     * https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
     * @param accountId id of the MetaTrader account to remove history for
     * @return completable future resolving when the history is cleared
     */
    public CompletableFuture<Void> removeHistory(String accountId) throws Exception {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "removeHistory");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Execute a trade on a connected MetaTrader account (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param accountId id of the MetaTrader account to execute trade for
     * @param trade trade to execute (see docs for possible trade types)
     * @returns completable future resolving with trade result
     */
    public CompletableFuture<MetatraderTradeResponse> trade(String accountId, MetatraderTrade trade) throws Exception {
        CompletableFuture<MetatraderTradeResponse> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "trade");
        request.set("trade", jsonMapper.valueToTree(trade));
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(response.get("response"), MetatraderTradeResponse.class));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Subscribes to the Metatrader terminal events (see https://metaapi.cloud/docs/client/websocket/api/subscribe/).
     * @param accountId id of the MetaTrader account to subscribe to
     * @returns completable future which resolves when subscription started
     */
    public CompletableFuture<Void> subscribe(String accountId) throws Exception {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "subscribe");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Reconnects to the Metatrader terminal (see https://metaapi.cloud/docs/client/websocket/api/reconnect/).
     * @param accountId id of the MetaTrader account to reconnect
     * @returns completable future which resolves when reconnection started
     */
    public CompletableFuture<Void> reconnect(String accountId) throws Exception {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "reconnect");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Requests the terminal to start synchronization process. Use it if user synchronization mode is set to user for the
     * account (see https://metaapi.cloud/docs/client/websocket/synchronizing/synchronize/).
     * @param accountId id of the MetaTrader account to synchronize
     * @param startingHistoryOrderTime from what date to start synchronizing history orders from. If not specified,
     * the entire order history will be downloaded.
     * @param startingDealTime from what date to start deal synchronization from. If not specified, then all
     * history deals will be downloaded.
     * @returns completable future which resolves when synchronization started
     */
    public CompletableFuture<Void> synchronize(
        String accountId, Optional<IsoTime> startingHistoryOrderTime, Optional<IsoTime> startingDealTime
    ) throws Exception {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "synchronize");
        if (startingHistoryOrderTime.isPresent())
            request.put("startingHistoryOrderTime", startingHistoryOrderTime.get().getIsoString());
        if (startingDealTime.isPresent())
            request.put("startingDealTime", startingDealTime.get().getIsoString());
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Subscribes on market data of specified symbol (see
     * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
     * @param accountId id of the MetaTrader account
     * @param symbol symbol (e.g. currency pair or an index)
     * @returns completable future which resolves when subscription request was processed
     */
    public CompletableFuture<Void> subscribeToMarketData(String accountId, String symbol) throws Exception {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "subscribeToMarketData");
        request.put("symbol", symbol);
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Retrieves specification for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolSpecification/).
     * @param accountId id of the MetaTrader account to retrieve symbol specification for
     * @param symbol symbol to retrieve specification for
     * @returns completable future resolving with specification retrieved
     */
    public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(
        String accountId, String symbol
    ) throws Exception {
        CompletableFuture<MetatraderSymbolSpecification> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getSymbolSpecification");
        request.put("symbol", symbol);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(
                    response.get("specification"), MetatraderSymbolSpecification.class
                ));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Retrieves price for a symbol (see
     * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/getSymbolPrice/).
     * @param accountId id of the MetaTrader account to retrieve symbol price for
     * @param symbol symbol to retrieve price for
     * @returns completable future which resolves when price is retrieved
     */
    public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(
        String accountId, String symbol
    ) throws Exception {
        CompletableFuture<MetatraderSymbolPrice> result = new CompletableFuture<>();
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "getSymbolPrice");
        request.put("symbol", symbol);
        rpcRequest(accountId, request).handle((response, error) -> {
            if (error != null) return result.completeExceptionally(error);
            try {
                return result.complete(jsonMapper.treeToValue(
                    response.get("price"), MetatraderSymbolPrice.class
                ));
            } catch (JsonProcessingException e) {
                return result.completeExceptionally(e);
            }
        });
        return result;
    }

    private void reconnect() throws Exception {
        while (!socket.connected() && !isSocketConnecting && connected) {
            tryReconnect().get();
        }
    }
    
    private CompletableFuture<Void> tryReconnect() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                if (!socket.connected() && !isSocketConnecting && connected) {
                    isSocketConnecting = true;
                    socket.connect();
                }
                result.complete(null);
            } catch (InterruptedException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    private void fireReconnected() {
        // TODO: Notify listeners
    }
    
    private CompletableFuture<JsonNode> rpcRequest(String accountId, ObjectNode request) throws Exception {
        if (!connected) connect().get();
        String requestId = RandomStringUtils.randomAlphanumeric(32);
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        requestResolves.put(requestId, result);
        request.put("accountId", accountId);
        request.put("requestId", requestId);
        socket.emit("request", request.toString());
        return result;
    }

    private Exception convertError(WebsocketError error) {
        switch (error.error) {
            case "ValidationError": return new ValidationException(error.message, error.details.get());
            case "NotFoundError": return new NotFoundException(error.message);
            case "NotSynchronizedError": return new NotSynchronizedException(error.message);
            case "NotAuthenticatedError": return new NotConnectedException(error.message);
            case "UnauthorizedError": close(); return new UnauthorizedException(error.message);
            default: return new InternalException(error.message);
        }
    }
    
    private void logMessage(String message) {
        String currentIsoDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        System.out.println("[" + currentIsoDate + "] " + message);
    }
}