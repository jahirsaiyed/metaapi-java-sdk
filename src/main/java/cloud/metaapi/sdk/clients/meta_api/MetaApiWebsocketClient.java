package cloud.metaapi.sdk.clients.meta_api;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeals;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderHistoryOrders;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.util.JsonMapper;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.WebSocket;

/**
 * MetaApi websocket API client (see https://metaapi.cloud/docs/client/websocket/overview/)
 */
public class MetaApiWebsocketClient {

    private static Logger logger = Logger.getLogger(MetaApiWebsocketClient.class);
    
    private String url;
    private String token;
    private Socket socket;
    private String application;
    private long requestTimeout;
    private long connectTimeout;
    private ObjectMapper jsonMapper = JsonMapper.getInstance();
    private Map<String, CompletableFuture<JsonNode>> requestResolves;
    private Map<String, List<SynchronizationListener>> synchronizationListeners;
    private List<ReconnectListener> reconnectListeners;
    private CompletableFuture<Void> connectFuture = null;
    private boolean isSocketConnecting = false;
    private boolean connected = false;
    
    /**
     * Constructs MetaApi websocket API client instance with default parameters
     * @param token authorization token
     */
    public MetaApiWebsocketClient(String token) {
        this(token, null, null, null, null);
    }
    
    /**
     * Constructs MetaApi websocket API client instance with default parameters
     * @param token authorization token
     * @param application application id or {@code null}. By default is {@code MetaApi}
     */
    public MetaApiWebsocketClient(String token, String application) {
        this(token, application, null, null, null);
    }
    
    /**
     * Constructs MetaApi websocket API client instance
     * @param token authorization token
     * @param application application id or {@code null}. By default is {@code MetaApi}
     * @param domain domain to connect to {@code null}. By default is {@code agiliumtrade.agiliumtrade.ai}
     * @param requestTimeout timeout for socket requests in milliseconds or {@code null}. By default is {@code 1 minute}
     * @param connectTimeout timeout for connecting to server in milliseconds or {@code null}. By default is {@code 1 minute}
     */
    public MetaApiWebsocketClient(String token, String application, String domain, Long requestTimeout, Long connectTimeout) {
        this.application = (application != null ? application : "MetaApi");
        this.url = "https://mt-client-api-v1." + (domain != null ? domain : "agiliumtrade.agiliumtrade.ai");
        this.token = token;
        this.requestResolves = new HashMap<>();
        this.synchronizationListeners = new HashMap<>();
        this.reconnectListeners = new LinkedList<>();
        this.requestTimeout = (requestTimeout != null ? requestTimeout : 60000);
        this.connectTimeout = (connectTimeout != null ? connectTimeout : 60000);
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
     * @return completable future which resolves when connection is established
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.supplyAsync(() -> {
            if (connected) return null;
            connected = true;
            requestResolves.clear();
            CompletableFuture<Void> result = new CompletableFuture<>();
            connectFuture = result;
            
            String url = this.url + "?auth-token=" + token;
            IO.Options socketOptions = new IO.Options();
            socketOptions.path = "/ws";
            socketOptions.reconnection = true;
            socketOptions.reconnectionDelay = 1000;
            socketOptions.reconnectionDelayMax = 5000;
            socketOptions.reconnectionAttempts = Integer.MAX_VALUE;
            socketOptions.timeout = connectTimeout;
            socketOptions.transports = new String[] { WebSocket.NAME };
            isSocketConnecting = true;
            try {
                socket = IO.socket(url, socketOptions);
                
                // Every EVENT_TRANSPORT during one session the same clientId must be used
                Random random = new Random();
                float clientId = random.nextFloat();

                // Adding extra headers
                socket.io().on(Manager.EVENT_TRANSPORT, (Object[] socketEventArgs) -> {
                    Transport transport = (Transport) socketEventArgs[0];
                    transport.on(Transport.EVENT_REQUEST_HEADERS, (Object[] transportEventArgs) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, List<String>> headers = (Map<String, List<String>>) transportEventArgs[0];
                        headers.put("Client-id", Arrays.asList(String.valueOf(clientId)));
                    });
                });
                
                socket.on(Socket.EVENT_CONNECT, (Object[] args) -> {
                    isSocketConnecting = false;
                    logger.info("MetaApi websocket client connected to the MetaApi server");
                    if (!result.isDone()) result.complete(null);
                    else fireReconnected().exceptionally((e) -> {
                        logger.error("Failed to notify reconnect listeners", e);
                        return null;
                    }).join();
                    if (!connected) socket.close();
                });
                socket.on(Socket.EVENT_RECONNECT, (Object[] args) -> {
                    try {
                        fireReconnected();
                    } catch (Exception e) {
                        logger.error("Failed to notify reconnect listeners", e);
                    }
                });
                socket.on(Socket.EVENT_CONNECT_ERROR, (Object[] args) -> {
                    Exception error = (Exception) args[0];
                    logger.error("MetaApi websocket client connection error", error);
                    if (!result.isDone()) result.completeExceptionally(error);
                });
                socket.on(Socket.EVENT_CONNECT_TIMEOUT, (Object[] args) -> {
                    logger.info("MetaApi websocket client connection timeout");
                    if (!result.isDone()) result.completeExceptionally(
                        new TimeoutException("MetaApi websocket client connection timed out")
                    );
                });
                socket.on(Socket.EVENT_DISCONNECT, (Object[] args) -> {
                    String reason = (String) args[0];
                    logger.info("MetaApi websocket client disconnected from the MetaApi server because of " + reason);
                    try {
                        reconnect();
                    } catch (Exception e) {
                        logger.error("MetaApi websocket reconnect error", e);
                    }
                });
                socket.on(Socket.EVENT_ERROR, (Object[] args) -> {
                    Exception error = (Exception) args[0];
                    logger.error("MetaApi websocket client error", error);
                    try {
                        reconnect();
                    } catch (Exception e) {
                        logger.error("MetaApi websocket reconnect error ", e);
                    }
                });
                socket.on("response", (Object[] args) -> {
                    try {
                        JsonNode data = jsonMapper.readTree(args[0].toString());
                        CompletableFuture<JsonNode> requestResolve = requestResolves.remove(data.get("requestId").asText());
                        if (requestResolve != null) requestResolve.complete(data);
                    } catch (JsonProcessingException e) {
                        logger.error("MetaApi websocket parse json response error", e);
                    }
                });
                socket.on("processingError", (Object[] args) -> {
                    try {
                        WebsocketError error = jsonMapper.readValue(args[0].toString(), WebsocketError.class);
                        CompletableFuture<JsonNode> requestResolve = requestResolves.remove(error.requestId);
                        if (requestResolve != null) requestResolve.completeExceptionally(convertError(error));
                    } catch (Exception e) {
                        logger.error("MetaApi websocket parse processingError data error", e);
                    }
                });
                socket.on("synchronization", (Object[] args) -> {
                    processSynchronizationPacket(args[0].toString());
                });
                socket.connect();
            } catch (URISyntaxException e) {
                result.completeExceptionally(e);
            }
            return result.join();
        });
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
        synchronizationListeners.clear();
    }
    
    /**
     * Returns account information for a specified MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
     * @param accountId id of the MetaTrader account to return information for
     * @return completable future resolving with account information
     */
    public CompletableFuture<MetatraderAccountInformation> getAccountInformation(String accountId) {
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
     * @return completable future resolving with list of open positions
     */
    public CompletableFuture<List<MetatraderPosition>> getPositions(String accountId) {
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
    public CompletableFuture<MetatraderPosition> getPosition(String accountId, String positionId) {
        ObjectNode request = JsonMapper.getInstance().createObjectNode();
        request.put("type", "getPosition");
        request.put("positionId", positionId);
        return rpcRequest(accountId, request).thenApply(response -> {
            try {
                return jsonMapper.treeToValue(response.get("position"), MetatraderPosition.class);
            } catch (JsonProcessingException e) {
            	throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Returns open orders for a specified MetaTrader account (see
     * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readOrders/).
     * @param accountId id of the MetaTrader account to return information for
     * @return completable future resolving with open MetaTrader orders
     */
    public CompletableFuture<List<MetatraderOrder>> getOrders(String accountId) {
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
    public CompletableFuture<MetatraderOrder> getOrder(String accountId, String orderId) {
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
     * @return completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTicket(String accountId, String ticket) {
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
     * @return completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByPosition(String accountId, String positionId) {
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
     * @param accountId id of the MetaTrader account to return information for
     * @param startTime start of time range, inclusive
     * @param endTime end of time range, exclusive
     * @param offset pagination offset
     * @param limit pagination limit
     * @return completable future resolving with request results containing history orders found
     */
    public CompletableFuture<MetatraderHistoryOrders> getHistoryOrdersByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset, int limit
    ) {
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
     * @return completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTicket(String accountId, String ticket) {
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
     * @return completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByPosition(String accountId, String positionId) {
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
     * @param accountId id of the MetaTrader account to return information for
     * @param startTime start of time range, inclusive
     * @param endTime end of time range, exclusive
     * @param offset pagination offset
     * @param limit pagination limit
     * @return completable future resolving with request results containing deals found
     */
    public CompletableFuture<MetatraderDeals> getDealsByTimeRange(
        String accountId, IsoTime startTime, IsoTime endTime, int offset, int limit
    ) {
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
     * Clears the order and transaction history of a specified application so that it can be synchronized from scratch
     * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
     * @param accountId id of the MetaTrader account to remove history for
     * @return completable future resolving when the history is cleared
     */
    public CompletableFuture<Void> removeHistory(String accountId) {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "removeHistory");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Clears the order and transaction history of a specified application and removes the application (see
     * https://metaapi.cloud/docs/client/websocket/api/removeApplication/).
     * @param accountId id of the MetaTrader account to remove history and application for
     * @return completable future resolving when the history is cleared
     */
    public CompletableFuture<Void> removeApplication(String accountId) {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "removeApplication");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Execute a trade on a connected MetaTrader account (see https://metaapi.cloud/docs/client/websocket/api/trade/).
     * @param accountId id of the MetaTrader account to execute trade for
     * @param trade trade to execute (see docs for possible trade types)
     * @return completable future resolving with trade result, or in case of trade error can be completed exceptionally
     * with {@link TradeException}, check error properties for error code details
     */
    public CompletableFuture<MetatraderTradeResponse> trade(String accountId, MetatraderTrade trade) {
        return CompletableFuture.supplyAsync(() -> {
            ObjectNode request = jsonMapper.createObjectNode();
            request.put("type", "trade");
            request.set("trade", jsonMapper.valueToTree(trade));
            return rpcRequest(accountId, request).thenApply(response -> {
                try {
                    MetatraderTradeResponse tradeResponse = jsonMapper
                        .treeToValue(response.get("response"), MetatraderTradeResponse.class);
                    if (Arrays.asList("ERR_NO_ERROR", "TRADE_RETCODE_PLACED", "TRADE_RETCODE_DONE", 
                        "TRADE_RETCODE_DONE_PARTIAL", "TRADE_RETCODE_NO_CHANGES").contains(tradeResponse.stringCode)
                    ) {
                        return tradeResponse;
                    } else {
                        throw new TradeException(
                            tradeResponse.message,
                            tradeResponse.numericCode,
                            tradeResponse.stringCode
                        );
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).join();
        });
    }
    
    /**
     * Subscribes to the Metatrader terminal events (see https://metaapi.cloud/docs/client/websocket/api/subscribe/).
     * @param accountId id of the MetaTrader account to subscribe to
     * @return completable future which resolves when subscription started
     */
    public CompletableFuture<Void> subscribe(String accountId) {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "subscribe");
        return rpcRequest(accountId, request)
            .exceptionally(exception -> {
                logger.error("MetaApi websocket client failed to receive subscribe response "
                    + "(this usually does not mean an error)", exception);
                throw new CompletionException(exception);
            }).thenApply(response -> null);
    }
    
    /**
     * Reconnects to the Metatrader terminal (see https://metaapi.cloud/docs/client/websocket/api/reconnect/).
     * @param accountId id of the MetaTrader account to reconnect
     * @return completable future which resolves when reconnection started
     */
    public CompletableFuture<Void> reconnect(String accountId) {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("type", "reconnect");
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Requests the terminal to start synchronization process 
     * (see https://metaapi.cloud/docs/client/websocket/synchronizing/synchronize/).
     * @param accountId id of the MetaTrader account to synchronize
     * @param synchronizationId synchronization request id
     * @param startingHistoryOrderTime from what date to start synchronizing history orders from. If not specified,
     * the entire order history will be downloaded.
     * @param startingDealTime from what date to start deal synchronization from. If not specified, then all
     * history deals will be downloaded.
     * @return completable future which resolves when synchronization started
     */
    public CompletableFuture<Void> synchronize(
        String accountId, String synchronizationId, IsoTime startingHistoryOrderTime, IsoTime startingDealTime
    ) {
        ObjectNode request = jsonMapper.createObjectNode();
        request.put("requestId", synchronizationId);
        request.put("type", "synchronize");
        if (startingHistoryOrderTime != null)
            request.put("startingHistoryOrderTime", startingHistoryOrderTime.getIsoString());
        if (startingDealTime != null)
            request.put("startingDealTime", startingDealTime.getIsoString());
        return rpcRequest(accountId, request).thenApply((response) -> null);
    }
    
    /**
     * Subscribes on market data of specified symbol (see
     * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
     * @param accountId id of the MetaTrader account
     * @param symbol symbol (e.g. currency pair or an index)
     * @return completable future which resolves when subscription request was processed
     */
    public CompletableFuture<Void> subscribeToMarketData(String accountId, String symbol) {
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
     * @return completable future resolving with specification retrieved
     */
    public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(String accountId, String symbol) {
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
     * @return completable future which resolves when price is retrieved
     */
    public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(String accountId, String symbol) {
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
    
    /**
     * Adds synchronization listener for specific account
     * @param accountId account id
     * @param listener synchronization listener to add
     */
    public void addSynchronizationListener(String accountId, SynchronizationListener listener) {
        List<SynchronizationListener> listeners = synchronizationListeners.get(accountId);
        if (listeners == null) {
            listeners = new LinkedList<>();
            synchronizationListeners.put(accountId, listeners);
        }
        listeners.add(listener);
    }
    
    /**
     * Removes synchronization listener for specific account
     * @param accountId account id
     * @param listener synchronization listener to remove
     */
    public void removeSynchronizationListener(String accountId, SynchronizationListener listener) {
        List<SynchronizationListener> listeners = synchronizationListeners.get(accountId);
        if (listeners != null) listeners.remove(listener);
    }
    
    /**
     * Adds reconnect listener
     * @param listener reconnect listener to add
     */
    public void addReconnectListener(ReconnectListener listener) {
        reconnectListeners.add(listener);
    }
    
    /**
     * Removes reconnect listener
     * @param listener listener to remove
     */
    public void removeReconnectListener(ReconnectListener listener) {
        reconnectListeners.remove(listener);
    }
    
    /**
     * Removes all listeners. Intended for use in unit tests.
     */
    public void removeAllListeners() {
        synchronizationListeners.clear();
        reconnectListeners.clear();
    }
    
    private void reconnect() throws InterruptedException, ExecutionException  {
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
    
    private CompletableFuture<JsonNode> rpcRequest(String accountId, ObjectNode request) {
        String requestId = request.has("requestId") ? request.get("requestId").asText() : UUID.randomUUID().toString();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!connected) connect().join();
                else connectFuture.join();
                CompletableFuture<JsonNode> result = new CompletableFuture<>();
                requestResolves.put(requestId, result);
                request.put("accountId", accountId);
                request.put("application", this.application);
                if (!request.has("requestId")) request.put("requestId", requestId);
                socket.emit("request", new JSONObject(jsonMapper.writeValueAsString(request)));
                return result.get(requestTimeout, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
            	throw new CompletionException(new TimeoutException("MetaApi websocket client request " 
                    + requestId + " of type " + request.get("type").asText() + " timed out"));
            } catch (InterruptedException | JsonProcessingException | JSONException e) {
                throw new CompletionException(e);
            } catch (ExecutionException e) {
            	throw new CompletionException(e.getCause());
            }
        });
    }
    
    private Exception convertError(WebsocketError error) {
        switch (error.error) {
            case "ValidationError": return new ValidationException(error.message, error.details);
            case "NotFoundError": return new NotFoundException(error.message);
            case "NotSynchronizedError": return new NotSynchronizedException(error.message);
            case "NotAuthenticatedError": return new NotConnectedException(error.message);
            case "TradeError": return new TradeException(error.message, error.numericCode, error.stringCode);
            case "UnauthorizedError": close(); return new UnauthorizedException(error.message);
            default: return new InternalException(error.message);
        }
    }
    
    private CompletableFuture<Void> processSynchronizationPacket(String packet) {
        return CompletableFuture.runAsync(() -> {
            try {
                JsonNode data = jsonMapper.readTree(packet);
                List<SynchronizationListener> listeners = synchronizationListeners.get(data.get("accountId").asText());
                if (listeners == null || listeners.isEmpty()) return;
                String type = data.get("type").asText();
                if (type.equals("authenticated")) {
                    List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                    for (SynchronizationListener listener : listeners) {
                        completableFutures.add(listener.onConnected().exceptionally(e -> {
                            logger.error("Failed to notify listener about connected event", e);
                            return null;
                        }));
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                } else if (type.equals("disconnected")) {
                    List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                    for (SynchronizationListener listener : listeners) {
                        completableFutures.add(listener.onDisconnected().exceptionally(e -> {
                            logger.error("Failed to notify listener about " + type + " event", e);
                            return null;
                        }));
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                } else if (type.equals("accountInformation")) {
                    if (data.hasNonNull(type)) {
                        MetatraderAccountInformation accountInformation = jsonMapper
                            .treeToValue(data.get(type), MetatraderAccountInformation.class);
                        List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                        for (SynchronizationListener listener : listeners) {
                            completableFutures.add(listener.onAccountInformationUpdated(accountInformation)
                                .exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                        }
                        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                    }
                } else if (type.equals("deals")) {
                    if (data.hasNonNull(type)) {
                        MetatraderDeal[] deals = jsonMapper.treeToValue(data.get(type), MetatraderDeal[].class);
                        for (MetatraderDeal deal : deals) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onDealAdded(deal).exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("orders")) {
                    if (data.hasNonNull(type)) {
                        MetatraderOrder[] orders = jsonMapper.treeToValue(data.get(type), MetatraderOrder[].class);
                        for (MetatraderOrder order : orders) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onOrderUpdated(order).exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("historyOrders")) {
                    if (data.hasNonNull(type)) {
                        MetatraderOrder[] historyOrders = jsonMapper.treeToValue(data.get(type), MetatraderOrder[].class);
                        for (MetatraderOrder historyOrder : historyOrders) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onHistoryOrderAdded(historyOrder).exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("positions")) {
                    if (data.hasNonNull(type)) {
                        MetatraderPosition[] positions = jsonMapper.treeToValue(data.get(type), MetatraderPosition[].class);
                        for (MetatraderPosition position : positions) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onPositionUpdated(position).exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("update")) {
                    if (data.hasNonNull("accountInformation")) {
                        MetatraderAccountInformation accountInformation = jsonMapper
                            .treeToValue(data.get("accountInformation"), MetatraderAccountInformation.class);
                        List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                        for (SynchronizationListener listener : listeners) {
                            completableFutures.add(listener.onAccountInformationUpdated(accountInformation)
                                .exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                        }
                        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                    }
                    if (data.hasNonNull("updatedPositions")) {
                        MetatraderPosition[] positions = jsonMapper
                            .treeToValue(data.get("updatedPositions"), MetatraderPosition[].class);
                        for (MetatraderPosition position : positions) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onPositionUpdated(position).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                    if (data.hasNonNull("removedPositionIds")) {
                        String[] removedPositionIds = jsonMapper
                            .treeToValue(data.get("removedPositionIds"), String[].class);
                        for (String positionId : removedPositionIds) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onPositionRemoved(positionId).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                    if (data.hasNonNull("updatedOrders")) {
                        MetatraderOrder[] updatedOrders = jsonMapper
                            .treeToValue(data.get("updatedOrders"), MetatraderOrder[].class);
                        for (MetatraderOrder order : updatedOrders) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onOrderUpdated(order).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                    if (data.hasNonNull("completedOrderIds")) {
                        String[] completedOrderIds = jsonMapper
                            .treeToValue(data.get("completedOrderIds"), String[].class);
                        for (String orderId : completedOrderIds) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onOrderCompleted(orderId).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                    if (data.hasNonNull("historyOrders")) {
                        MetatraderOrder[] historyOrders = jsonMapper
                            .treeToValue(data.get("historyOrders"), MetatraderOrder[].class);
                        for (MetatraderOrder historyOrder : historyOrders) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onHistoryOrderAdded(historyOrder).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                    if (data.hasNonNull("deals")) {
                        MetatraderDeal[] deals = jsonMapper
                            .treeToValue(data.get("deals"), MetatraderDeal[].class);
                        for (MetatraderDeal deal : deals) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onDealAdded(deal).exceptionally(e -> {
                                    logger.error("Failed to notify listener about update event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("dealSynchronizationFinished")) {
                    List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                    for (SynchronizationListener listener : listeners) {
                        completableFutures.add(listener.onDealSynchronizationFinished(data.get("synchronizationId").asText())
                            .exceptionally(e -> {
                                logger.error("Failed to notify listener about " + type + " event", e);
                                return null;
                            }));
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                } else if (type.equals("orderSynchronizationFinished")) {
                    List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                    for (SynchronizationListener listener : listeners) {
                        completableFutures.add(listener.onOrderSynchronizationFinished(data.get("synchronizationId").asText())
                            .exceptionally(e -> {
                                logger.error("Failed to notify listener about " + type + " event", e);
                                return null;
                            }));
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                } else if (type.equals("status")) {
                    List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                    for (SynchronizationListener listener : listeners) {
                        completableFutures.add(listener.onBrokerConnectionStatusChanged(data.get("connected").asBoolean())
                            .exceptionally(e -> {
                                logger.error("Failed to notify listener about brokerConnectionStatusChanged event", e);
                                return null;
                            }));
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                } else if (type.equals("specifications")) {
                    if (data.hasNonNull(type)) {
                        MetatraderSymbolSpecification[] specifications = jsonMapper
                            .treeToValue(data.get(type), MetatraderSymbolSpecification[].class);
                        for (MetatraderSymbolSpecification specification : specifications) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onSymbolSpecificationUpdated(specification)
                                    .exceptionally(e -> {
                                        logger.error("Failed to notify listener about " + type + " event", e);
                                        return null;
                                    }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                } else if (type.equals("prices")) {
                    if (data.hasNonNull(type)) {
                        MetatraderSymbolPrice[] prices = jsonMapper
                            .treeToValue(data.get(type), MetatraderSymbolPrice[].class);
                        for (MetatraderSymbolPrice price : prices) {
                            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
                            for (SynchronizationListener listener : listeners) {
                                completableFutures.add(listener.onSymbolPriceUpdated(price).exceptionally(e -> {
                                    logger.error("Failed to notify listener about " + type + " event", e);
                                    return null;
                                }));
                            }
                            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
                        }
                    }
                }
            } catch (JsonProcessingException | InterruptedException | ExecutionException e) {
                logger.error("Failed to process incoming synchronization packet", e);
            }
        });
    }
    
    private CompletableFuture<Void> fireReconnected() {
        return CompletableFuture.runAsync(() -> {
            reconnectListeners.forEach((action) -> {});
            for (ReconnectListener listener : reconnectListeners) {
                listener.onReconnected().join();
            }
        });
    }
}