package cloud.metaapi.sdk.clients.meta_api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.RetryOptions;
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.OptionsValidator;
import cloud.metaapi.sdk.clients.error_handler.*;
import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException.TooManyRequestsExceptionMetadata;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.ResponseTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.TradeTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.UpdateTimestamps;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataSubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataUnsubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderBook;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderCandle;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeals;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderHistoryOrders;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTick;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.util.Async;
import cloud.metaapi.sdk.util.Js;
import cloud.metaapi.sdk.util.JsonMapper;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.WebSocket;

/**
 * MetaApi websocket API client (see https://metaapi.cloud/docs/client/websocket/overview/)
 */
public class MetaApiWebsocketClient implements OutOfOrderListener {

  private static Logger logger = LogManager.getLogger(MetaApiWebsocketClient.class);
  protected static int resetDisconnectTimerTimeout = 60000;
  
  private String domain;
  private String region;
  private String hostname;
  private String url;
  private String token;
  private String application;
  private long requestTimeout;
  private long connectTimeout;
  private int retries;
  private int minRetryDelayInSeconds;
  private int maxRetryDelayInSeconds;
  private int maxAccountsPerInstance = 100;
  private long subscribeCooldownInSeconds;
  private boolean sequentialEventProcessing;
  private boolean useSharedClientApi;
  private HttpClient httpClient;
  private ObjectMapper jsonMapper = JsonMapper.getInstance();
  private Map<String, List<SynchronizationListener>> synchronizationListeners = new ConcurrentHashMap<>();
  private List<LatencyListener> latencyListeners = new LinkedList<>();
  private List<ReconnectListenerItem> reconnectListeners = new LinkedList<>();
  private Map<String, String> connectedHosts = new ConcurrentHashMap<>();
  protected List<SocketInstance> socketInstances = new ArrayList<>();
  protected Map<String, Integer> socketInstancesByAccounts = new ConcurrentHashMap<>();
  private SynchronizationThrottler.Options synchronizationThrottlerOpts;
  private SubscriptionManager subscriptionManager;
  private Map<String, Timer> statusTimers = new ConcurrentHashMap<>();
  private Map<String, List<Supplier<CompletableFuture<Void>>>> eventQueues = new ConcurrentHashMap<>();
  private SubscribeLock subscribeLock;
  private PacketOrderer packetOrderer;
  private PacketLogger packetLogger;
  
  private static class RequestResolve {
    public CompletableFuture<JsonNode> future;
    public String type;
  }
  
  private static class ReconnectListenerItem {
    public String accountId;
    public ReconnectListener listener;
  }
  
  /**
   * Websocket client options
   */
  public static class ClientOptions {
    /**
     * Application id. By default is {@code MetaApi}
     */
    public String application = "MetaApi";
    /**
     * Domain to connect to. By default is {@code agiliumtrade.agiliumtrade.ai}
     */
    public String domain = "agiliumtrade.agiliumtrade.ai";
    /**
     * Optional region to connect to
     */
    public String region;
    /**
     * Timeout for socket requests in milliseconds. By default is {@code 1 minute}
     */
    public long requestTimeout = 60000L;
    /**
     * Timeout for connecting to server in milliseconds. By default is {@code 1 minute}
     */
    public long connectTimeout = 60000L;
    /**
     * Packet ordering timeout in seconds. Default is {@code 1 minute}
     */
    public int packetOrderingTimeout = 60;
    /**
     * Synchronization throttler options
     */
    public SynchronizationThrottler.Options synchronizationThrottler = new SynchronizationThrottler.Options();
    /**
     * Packet logger options
     */
    public PacketLoggerOptions packetLogger = new PacketLoggerOptions();
    /**
     * Retry options
     */
    public RetryOptions retryOpts = new RetryOptions();
    /**
     * Event processing options
     */
    public EventProcessingOptions eventProcessing = new EventProcessingOptions();
    /**
     * Option to use a shared server
     */
    public boolean useSharedClientApi = false;
  }
  
  /**
   * Options for processing websocket client events
   */
  public static class EventProcessingOptions {
    /**
     * An option to process synchronization events after finishing previous ones
     */
    public boolean sequentialProcessing = false;
  }
  
  /**
   * Packet logger options
   */
  public static class PacketLoggerOptions extends PacketLogger.LoggerOptions {}
  
  /**
   * Constructs MetaApi websocket API client instance
   * @param httpClient HTTP client
   * @param token authorization token
   * @param opts websocket client options
   * @throws IOException if packet logger is enabled and failed to create the log directory
   * @throws ValidationException if options are invalid
   */
  public MetaApiWebsocketClient(HttpClient httpClient, String token, ClientOptions opts)
    throws IOException, ValidationException {
    OptionsValidator validator = new OptionsValidator();
    validator.validateNonZeroInt(opts.packetOrderingTimeout, "packetOrderingTimeout");
    validator.validateNonZeroLong(opts.requestTimeout, "requestTimeout");
    validator.validateNonZeroLong(opts.connectTimeout, "requestTimeout");
    validator.validateNonZeroInt(opts.retryOpts.minDelayInSeconds, "retryOpts.minDelayInSeconds");
    validator.validateNonZeroInt(opts.retryOpts.maxDelayInSeconds, "retryOpts.maxDelayInSeconds");
    validator.validateNonZeroLong(opts.retryOpts.subscribeCooldownInSeconds, "retryOpts.subscribeCooldownInSeconds");
    
    this.httpClient = httpClient;
    this.application = opts.application;
    this.domain = opts.domain;
    this.region = opts.region;
    this.hostname = "mt-client-api-v1";
    this.url = "https://" + this.hostname + "." + this.domain;
    this.token = token;
    this.requestTimeout = opts.requestTimeout;
    this.connectTimeout = opts.connectTimeout;
    this.retries = opts.retryOpts.retries;
    this.minRetryDelayInSeconds = opts.retryOpts.minDelayInSeconds;
    this.maxRetryDelayInSeconds = opts.retryOpts.maxDelayInSeconds;
    this.subscribeCooldownInSeconds = opts.retryOpts.subscribeCooldownInSeconds;
    this.sequentialEventProcessing = opts.eventProcessing.sequentialProcessing;
    this.useSharedClientApi = opts.useSharedClientApi;
    this.synchronizationThrottlerOpts = opts.synchronizationThrottler;
    this.subscriptionManager = new SubscriptionManager(this);
    this.packetOrderer = new PacketOrderer(this, opts.packetOrderingTimeout);
    if (opts.packetLogger.enabled) {
      this.packetLogger = new PacketLogger(opts.packetLogger);
      this.packetLogger.start();
    }
  }
  
  /**
   * Restarts the account synchronization process on an out of order packet
   * @param accountId account id
   * @param instanceIndex instance index
   * @param expectedSequenceNumber expected s/n
   * @param actualSequenceNumber actual s/n
   * @param packet packet data
   * @param receivedAt time the packet was received at
   */
  public void onOutOfOrderPacket(String accountId, int instanceIndex, long expectedSequenceNumber, 
    long actualSequenceNumber, JsonNode packet, IsoTime receivedAt) {
    logger.error("MetaApi websocket client received an out of order packet type " +
      packet.get("type").asText() + " for account id " + accountId + ":" + instanceIndex +
      ". Expected s/n " + expectedSequenceNumber + " does not match the actual of " + actualSequenceNumber);
    ensureSubscribe(accountId, instanceIndex);
  }
  
  /**
   * Patch server URL for use in unit tests
   * @param url patched server URL
   */
  public void setUrl(String url) {
    this.url = url;
  }
  
  /**
   * Returns the list of socket instance dictionaries
   * @return list of socket instance dictionaries
   */
  public List<SocketInstance> getSocketInstances() {
    return socketInstances;
  }
  
  /**
   * Returns the map of socket instances by account ids
   * @return map of socket instances by account ids
   */
  public Map<String, Integer> getSocketInstancesByAccounts() {
    return socketInstancesByAccounts;
  }
  
  /**
   * Returns the list of subscribed account ids
   * @return list of subscribed account ids
   */
  public List<String> getSubscribedAccountIds() {
    return getSubscribedAccountIds(null);
  }
  
  /**
   * Returns the list of subscribed account ids
   * @param socketInstanceIndex socket instance index
   * @return list of subscribed account ids
   */
  public List<String> getSubscribedAccountIds(Integer socketInstanceIndex) {
    List<String> connectedIds = new ArrayList<>();
    for (String instanceId : new ArrayList<>(connectedHosts.keySet())) {
      String accountId = instanceId.split(":")[0];
      if (!connectedIds.contains(accountId) && socketInstancesByAccounts.containsKey(accountId)
          && (socketInstancesByAccounts.get(accountId) == socketInstanceIndex || 
          socketInstanceIndex == null)) {
        connectedIds.add(accountId);
      }
    }
    return connectedIds;
  }
  
  /**
   * Returns websocket client connection status
   * @param socketInstanceIndex socket instance index
   * @return websocket client connection status
   */
  public boolean isConnected(Integer socketInstanceIndex) {
    SocketInstance instance = (socketInstanceIndex != null && socketInstances.size() > socketInstanceIndex)
      ? socketInstances.get(socketInstanceIndex) : null;
    return (instance != null && instance.socket != null && instance.socket.connected()) || false;
  }
  
  /**
   * Returns list of accounts assigned to instance
   * @param socketInstanceIndex socket instance index
   * @return list of accounts assigned to instance
   */
  public List<String> getAssignedAccounts(int socketInstanceIndex) {
    List<String> accountIds = new ArrayList<>();
    socketInstancesByAccounts.keySet().stream().forEach(key -> {
      if (socketInstancesByAccounts.get(key) == socketInstanceIndex) {
        accountIds.add(key);
      }
    });
    return accountIds;
  }

  private static class SubscribeLock {
    public IsoTime recommendedRetryTime;
    public int lockedAtAccounts;
    public long lockedAtTime;
    public String type;
  }
  
  /**
   * Locks subscription for a socket instance based on TooManyRequestsError metadata
   * @param socketInstanceIndex socket instance index
   * @param metadata TooManyRequestsException metadata
   * @return completable future resolving when the operation is completed
   */
  public CompletableFuture<Void> lockSocketInstance(int socketInstanceIndex,
    TooManyRequestsExceptionMetadata metadata) {
    if (metadata.type.equals("LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER")) {
      subscribeLock = new SubscribeLock() {{
        recommendedRetryTime = metadata.recommendedRetryTime;
        lockedAtAccounts = getSubscribedAccountIds().size();
        lockedAtTime = Date.from(Instant.now()).getTime();
      }};
      return CompletableFuture.completedFuture(null);
    } else {
      List<String> subscribedAccounts = getSubscribedAccountIds(socketInstanceIndex);
      if (subscribedAccounts.size() == 0) {
        SocketInstance socketInstance = socketInstances.get(socketInstanceIndex);
        socketInstance.socket.close();
        return reconnect(String.valueOf(socketInstanceIndex));
      } else {
        SocketInstance instance = socketInstances.get(socketInstanceIndex);
        instance.subscribeLock = new SubscribeLock() {{
          recommendedRetryTime = metadata.recommendedRetryTime;
          type = metadata.type;
          lockedAtAccounts = subscribedAccounts.size();
        }};
        return CompletableFuture.completedFuture(null);
      }
    }
  }
  
  protected static class SocketInstance {
    public int id;
    public boolean connected;
    public Map<String, RequestResolve> requestResolves;
    public boolean resolved;
    public CompletableFuture<Void> connectResult;
    public String sessionId;
    public boolean isReconnecting;
    public Socket socket;
    public SynchronizationThrottler synchronizationThrottler;
    public SubscribeLock subscribeLock;
    public double clientId;
    public Boolean firstConnect;
  }
  
  /**
   * Connects to MetaApi server via socket.io protocol
   * @return completable future which resolves when connection is established
   */
  public CompletableFuture<Void> connect() {
    return Async.supply(() -> {
      CompletableFuture<Void> result = new CompletableFuture<>();
      try {
        int socketInstanceIndex = socketInstances.size();
        String serverUrl = getServerUrl();
        MetaApiWebsocketClient self = this;
        SocketInstance instance = new SocketInstance() {{
          id = socketInstanceIndex;
          connected = false;
          requestResolves = new ConcurrentHashMap<>();
          resolved = false;
          connectResult = result;
          sessionId = RandomStringUtils.randomAlphanumeric(32);
          isReconnecting = false;
          socket = null;
          synchronizationThrottler = new SynchronizationThrottler(self, socketInstanceIndex,
            synchronizationThrottlerOpts);
          subscribeLock = null;
          clientId = Math.random();
        }};
        instance.connected = true;
        socketInstances.add(instance);
        instance.synchronizationThrottler.start();
        instance.firstConnect = true;
        createSocket(instance, serverUrl, result);
        Socket socketInstance = instance.socket;
        if (socketInstances.size() == 1) {
          packetOrderer.start();
        }
        socketInstance.connect();
      } catch (Exception e) {
        result.completeExceptionally(e);
      }
      return result.join();
    });
  }
  
  private void createSocket(SocketInstance instance, String uri, CompletableFuture<Void> result)
    throws URISyntaxException {
    IO.Options socketOptions = new IO.Options();
    socketOptions.path = "/ws";
    socketOptions.reconnection = true;
    socketOptions.reconnectionDelay = 1000;
    socketOptions.reconnectionDelayMax = 5000;
    socketOptions.reconnectionAttempts = Integer.MAX_VALUE;
    socketOptions.timeout = connectTimeout;
    socketOptions.transports = new String[] { WebSocket.NAME };
    instance.socket = IO.socket(uri, socketOptions);
    Socket socketInstance = instance.socket;
    
    // Adding headers and query during every connection
    socketInstance.io().on(Manager.EVENT_TRANSPORT, (Object[] socketEventArgs) -> {
      Transport transport = (Transport) socketEventArgs[0];
      transport.query.put("auth-token", token);
      transport.query.put("clientId", String.valueOf(instance.clientId));
      transport.query.put("protocol", "2");
      transport.on(Transport.EVENT_REQUEST_HEADERS, (Object[] transportEventArgs) -> {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) transportEventArgs[0];
        headers.put("Client-Id", Arrays.asList(String.valueOf(instance.clientId)));
      });
    });
    
    socketInstance.on(Socket.EVENT_CONNECT, (Object[] args) -> {
      Async.run(() -> {
        boolean isSharedClientApi = uri.equals(this.url);
        logger.info("MetaApi websocket client connected to the MetaApi server via " 
          + uri + " " + (isSharedClientApi ? "shared" : "dedicated") + " server");
        if (instance.id == 0 && (instance.firstConnect != null && instance.firstConnect) && !isSharedClientApi) {
          logger.info("Please note that it can take up to 3 minutes for your dedicated server to start for the " +
          "first time. During this time it is OK if you see some connection errors.");
          instance.firstConnect = false;
        }
        instance.isReconnecting = false;
        if (result != null && !result.isDone()) {
          result.complete(null);
        } else {
          fireReconnected(instance.id).exceptionally((e) -> {
            logger.error("Failed to notify reconnect listeners", e);
            return null;
          }).join();
        }
        if (!instance.connected) {
          instance.socket.close();
        }
      });
    });
    socketInstance.on(Socket.EVENT_RECONNECT, (Object[] args) -> {
      try {
        instance.isReconnecting = false;
        fireReconnected(instance.id);
      } catch (Exception e) {
        logger.error("Failed to notify reconnect listeners", e);
      }
    });
    socketInstance.on(Socket.EVENT_CONNECT_ERROR, (Object[] args) -> {
      Exception error = (Exception) args[0];
      logger.error("MetaApi websocket client connection error", error);
      instance.isReconnecting = false;
      if (result != null && !result.isDone()) {
        result.completeExceptionally(error);
      }
    });
    socketInstance.on(Socket.EVENT_CONNECT_TIMEOUT, (Object[] args) -> {
      logger.info("MetaApi websocket client connection timeout");
      instance.isReconnecting = false;
      if (result != null && !result.isDone()) {
        result.completeExceptionally(new TimeoutException("MetaApi websocket client connection timed out"));
      }
    });
    socketInstance.on(Socket.EVENT_DISCONNECT, (Object[] args) -> {
      instance.synchronizationThrottler.onDisconnect();
      String reason = (String) args[0];
      logger.info("MetaApi websocket client disconnected from the MetaApi server because of " + reason);
      instance.isReconnecting = false;
      try {
        reconnect(instance.id);
      } catch (Exception e) {
        logger.error("MetaApi websocket reconnect error", e);
      }
    });
    socketInstance.on(Socket.EVENT_ERROR, (Object[] args) -> {
      Exception error = (Exception) args[0];
      logger.error("MetaApi websocket client error", error);
      instance.isReconnecting = false;
      try {
        reconnect(instance.id);
      } catch (Exception e) {
        logger.error("MetaApi websocket reconnect error ", e);
      }
    });
    socketInstance.on("response", (Object[] args) -> {
      try {
        JsonNode uncheckedData = jsonMapper.readTree(args[0].toString());
        if (uncheckedData.isTextual()) {
          uncheckedData = jsonMapper.readTree(uncheckedData.asText());
        }
        final JsonNode data = uncheckedData;
        RequestResolve requestResolve = data.has("requestId")
          ? instance.requestResolves.remove(data.get("requestId").asText()) : null;
        if (requestResolve != null) {
          Async.run(() -> {
            requestResolve.future.complete(data);
            if (data.has("timestamps") && requestResolve.type != null) {
              for (LatencyListener listener : latencyListeners) {
                Async.run(() -> {
                  try {
                    String accountId = data.get("accountId").asText();
                    if (requestResolve.type.equals("trade")) {
                      TradeTimestamps timestamps = jsonMapper.treeToValue(data.get("timestamps"),
                        TradeTimestamps.class);
                      timestamps.clientProcessingFinished = new IsoTime();
                      listener.onTrade(accountId, timestamps).join();
                    } else {
                      ResponseTimestamps timestamps = jsonMapper.treeToValue(data.get("timestamps"),
                        ResponseTimestamps.class);
                      timestamps.clientProcessingFinished = new IsoTime();
                      listener.onResponse(accountId, requestResolve.type, timestamps).join();
                    }
                  } catch (Throwable err) {
                    throw new CompletionException(err);
                  }
                }).exceptionally(err -> {
                  logger.error("Failed to process onResponse event for account " +
                    data.get("accountId").toString() + ", request type" +
                    requestResolve.type, err);
                  return null;
                });
              }
            }
          });
        }
      } catch (JsonProcessingException e) {
        logger.error("MetaApi websocket parse json response error", e);
      }
    });
    socketInstance.on("processingError", (Object[] args) -> {
      try {
        WebsocketError error = jsonMapper.readValue(args[0].toString(), WebsocketError.class);
        RequestResolve requestResolve = instance.requestResolves.remove(error.requestId);
        if (requestResolve != null) {
          requestResolve.future.completeExceptionally(convertError(error));
        }
      } catch (Exception e) {
        logger.error("MetaApi websocket parse processingError data error", e);
      }
    });
    socketInstance.on("synchronization", (Object[] args) -> {
      try {
        ObjectNode packet = (ObjectNode) jsonMapper.readTree(args[0].toString());
        if (packet.isTextual()) {
          packet = (ObjectNode) jsonMapper.readTree(packet.asText());
        }
        String synchronizationId = packet.has("synchronizationId")
          ? packet.get("synchronizationId").asText() : null;
        if (synchronizationId == null || instance.synchronizationThrottler.getActiveSynchronizationIds()
          .contains(synchronizationId)) {
          if (packetLogger != null) {
            packetLogger.logPacket(packet);
          }
        } else {
          packet.put("type", "noop");
        }
        queuePacket(packet);
      } catch (JsonProcessingException e) {
        logger.error("Failed to parse incoming synchronization packet", e);
      }
    });
  }
  
  /**
   * Closes connection to MetaApi server
   */
  public void close() {
    socketInstances.forEach(instance -> {
      if (instance.connected) {
        instance.socket.close();
        for (RequestResolve resolve : new ArrayList<>(instance.requestResolves.values())) {
          resolve.future.completeExceptionally(new Exception("MetaApi connection closed"));
        }
        instance.requestResolves.clear();
      }
    });
    synchronizationListeners.clear();
    latencyListeners.clear();
    socketInstancesByAccounts.clear();
    socketInstances.clear();
    packetOrderer.stop();
  }
  
  /**
   * Returns account information for a specified MetaTrader account (see
   * https://metaapi.cloud/docs/client/websocket/api/readTradingTerminalState/readAccountInformation/).
   * @param accountId id of the MetaTrader account to return information for
   * @return completable future resolving with account information
   */
  public CompletableFuture<MetatraderAccountInformation> getAccountInformation(String accountId) {
    ObjectNode request = JsonMapper.getInstance().createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getAccountInformation");
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(
          response.get("accountInformation"),
          MetatraderAccountInformation.class
        );
      } catch (JsonProcessingException e) {
        throw new CompletionException(e); 
      }
    });
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    request.put("application", "RPC");
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
    return removeHistory(accountId, null);
  }
  
  /**
   * Clears the order and transaction history of a specified application so that it can be synchronized from scratch
   * (see https://metaapi.cloud/docs/client/websocket/api/removeHistory/).
   * @param accountId id of the MetaTrader account to remove history for
   * @param application application to remove history for, or {@code null}
   * @return completable future resolving when the history is cleared
   */
  public CompletableFuture<Void> removeHistory(String accountId, String application) {
    ObjectNode request = jsonMapper.createObjectNode();
    if (application != null) request.put("application", application);
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
    return Async.supply(() -> {
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
   * Creates a task that ensures the account gets subscribed to the server
   * @param accountId account id to subscribe
   * @param instanceNumber instance index number, or {@code null}
   * @return completable future resolving when the operation is completed
   */
  public CompletableFuture<Void> ensureSubscribe(String accountId, Integer instanceNumber) {
    return subscriptionManager.subscribe(accountId, instanceNumber, false);
  }
  
  /**
   * Subscribes to the Metatrader terminal events (see https://metaapi.cloud/docs/client/websocket/api/subscribe/).
   * @param accountId id of the MetaTrader account to subscribe to
   * @return completable future which resolves when subscription started
   */
  public CompletableFuture<Void> subscribe(String accountId) {
    return subscribe(accountId, null);
  }
  
  /**
   * Subscribes to the Metatrader terminal events (see https://metaapi.cloud/docs/client/websocket/api/subscribe/).
   * @param accountId id of the MetaTrader account to subscribe to
   * @param instanceNumber instance index number, or {@code null}
   * @return completable future which resolves when subscription started
   */
  public CompletableFuture<Void> subscribe(String accountId, Integer instanceNumber) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "subscribe");
    if (instanceNumber != null) {
      request.put("instanceIndex", instanceNumber);
    }
    return rpcRequest(accountId, request).thenApply(response -> null);
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
   * @param instanceIndex instance index
   * @param host name of host to synchronize with
   * @param synchronizationId synchronization request id
   * @param startingHistoryOrderTime from what date to start synchronizing history orders from. If not specified,
   * the entire order history will be downloaded.
   * @param startingDealTime from what date to start deal synchronization from. If not specified, then all
   * history deals will be downloaded.
   * @return completable future which resolves when synchronization started
   */
  public CompletableFuture<Boolean> synchronize(String accountId, Integer instanceIndex, String host,
      String synchronizationId, IsoTime startingHistoryOrderTime, IsoTime startingDealTime) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("requestId", synchronizationId);
    request.put("type", "synchronize");
    if (startingHistoryOrderTime != null)
      request.put("startingHistoryOrderTime", startingHistoryOrderTime.getIsoString());
    if (startingDealTime != null)
      request.put("startingDealTime", startingDealTime.getIsoString());
    request.put("instanceIndex", instanceIndex);
    request.put("host", host);
    SynchronizationThrottler syncThrottler = socketInstances.get(socketInstancesByAccounts.get(accountId))
      .synchronizationThrottler;
    return syncThrottler.scheduleSynchronize(accountId, request);
  }
  
  /**
   * Waits for server-side terminal state synchronization to complete.
   * (see https://metaapi.cloud/docs/client/websocket/synchronizing/waitSynchronized/).
   * @param accountId id of the MetaTrader account to synchronize
   * @param instanceNumber instance index number
   * @param applicationPattern MetaApi application regular expression pattern, or {@code null}, default is .*
   * @param timeoutInSeconds timeout in seconds, or {@code null}, default is 300 seconds
   * @return completable future which resolves when synchronization started
   */
  public CompletableFuture<Void> waitSynchronized(String accountId, Integer instanceNumber,
      String applicationPattern, Long timeoutInSeconds) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "waitSynchronized");
    if (applicationPattern != null) request.put("applicationPattern", applicationPattern);
    request.put("timeoutInSeconds", timeoutInSeconds);
    request.put("instanceIndex", instanceNumber);
    return rpcRequest(accountId, request, timeoutInSeconds + 1).thenApply((response) -> null);
  }
  
  /**
   * Subscribes on market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/subscribeToMarketData/).
   * @param accountId id of the MetaTrader account
   * @param instanceNumber instance index number
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of market data subscription to create or update
   * @return completable future which resolves when subscription request was processed
   */
  public CompletableFuture<Void> subscribeToMarketData(String accountId, Integer instanceNumber,
      String symbol, List<MarketDataSubscription> subscriptions) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "subscribeToMarketData");
    request.put("symbol", symbol);
    request.set("subscriptions", jsonMapper.valueToTree(subscriptions));
    request.put("instanceIndex", instanceNumber);
    return rpcRequest(accountId, request).thenApply((response) -> null);
  }
  
  /**
   * Unsubscribes from market data of specified symbol (see
   * https://metaapi.cloud/docs/client/websocket/marketDataStreaming/unsubscribeFromMarketData/).
   * @param accountId id of the MetaTrader account
   * @param instanceNumber instance index number
   * @param symbol symbol (e.g. currency pair or an index)
   * @param subscriptions array of subscriptions to cancel
   * @return completable future which resolves when unsubscription request was processed
   */
  public CompletableFuture<Void> unsubscribeFromMarketData(String accountId, int instanceNumber,
      String symbol, List<MarketDataUnsubscription> subscriptions) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "unsubscribeFromMarketData");
    request.put("symbol", symbol);
    request.set("subscriptions", jsonMapper.valueToTree(subscriptions));
    request.put("instanceIndex", instanceNumber);
    return rpcRequest(accountId, request).thenApply((response) -> null);
  }
  
  /**
   * Retrieves symbols available on an account (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbols/).
   * @param accountId id of the MetaTrader account to retrieve symbols for
   * @return completable future which resolves when symbols are retrieved
   */
  public CompletableFuture<List<String>> getSymbols(String accountId) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getSymbols");
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return Arrays.asList(jsonMapper.treeToValue(response.get("symbols"), String[].class));
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Retrieves specification for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbolSpecification/).
   * @param accountId id of the MetaTrader account to retrieve symbol specification for
   * @param symbol symbol to retrieve specification for
   * @return completable future resolving with specification retrieved
   */
  public CompletableFuture<MetatraderSymbolSpecification> getSymbolSpecification(String accountId, String symbol) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getSymbolSpecification");
    request.put("symbol", symbol);
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(response.get("specification"), MetatraderSymbolSpecification.class);
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Retrieves price for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readSymbolPrice/).
   * @param accountId id of the MetaTrader account to retrieve symbol price for
   * @param symbol symbol to retrieve price for
   * @return completable future which resolves when price is retrieved
   */
  public CompletableFuture<MetatraderSymbolPrice> getSymbolPrice(String accountId, String symbol) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getSymbolPrice");
    request.put("symbol", symbol);
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(response.get("price"), MetatraderSymbolPrice.class);
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Retrieves price for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readCandle/).
   * @param accountId id of the MetaTrader account to retrieve candle for
   * @param symbol symbol to retrieve candle for
   * @param timeframe defines the timeframe according to which the candle must be generated. Allowed values for
   * MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h, 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values
   * for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @return completable future which resolves when candle is retrieved
   */
  public CompletableFuture<MetatraderCandle> getCandle(String accountId, String symbol, String timeframe) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getCandle");
    request.put("symbol", symbol);
    request.put("timeframe", timeframe);
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(response.get("candle"), MetatraderCandle.class);
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Retrieves latest tick for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readTick/).
   * @param accountId id of the MetaTrader account to retrieve symbol tick for
   * @param symbol symbol to retrieve tick for
   * @return completable future which resolves when tick is retrieved
   */
  public CompletableFuture<MetatraderTick> getTick(String accountId, String symbol) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getTick");
    request.put("symbol", symbol);
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(response.get("tick"), MetatraderTick.class);
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Retrieves latest order book for a symbol (see
   * https://metaapi.cloud/docs/client/websocket/api/retrieveMarketData/readBook/).
   * @param accountId id of the MetaTrader account to retrieve symbol order book for
   * @param symbol symbol to retrieve order book for
   * @return promise which resolves when order book is retrieved
   */
  public CompletableFuture<MetatraderBook> getBook(String accountId, String symbol) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("application", "RPC");
    request.put("type", "getBook");
    request.put("symbol", symbol);
    return rpcRequest(accountId, request).thenApply(response -> {
      try {
        return jsonMapper.treeToValue(response.get("book"), MetatraderBook.class);
      } catch (JsonProcessingException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Sends client uptime stats to the server.
   * @param accountId id of the MetaTrader account to retrieve symbol price for
   * @param uptime uptime statistics to send to the server
   * @return completable future which resolves when uptime statistics is submitted
   */
  public CompletableFuture<Void> saveUptime(String accountId, Map<String, Double> uptime) {
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "saveUptime");
    request.set("uptime", jsonMapper.valueToTree(uptime));
    return rpcRequest(accountId, request).thenApply((response) -> null);
  }
  
  /**
   * Unsubscribe from account (see
   * https://metaapi.cloud/docs/client/websocket/api/synchronizing/unsubscribe).
   * @param accountId id of the MetaTrader account to retrieve symbol price for
   * @return completable future which resolves when socket unsubscribed
   */
  public CompletableFuture<JsonNode> unsubscribe(String accountId) {
    subscriptionManager.cancelAccount(accountId);
    ObjectNode request = jsonMapper.createObjectNode();
    request.put("type", "unsubscribe");
    return rpcRequest(accountId, request).handle((response, err) -> {
      if (err != null && !(err.getCause() instanceof NotFoundException)) {
        throw new CompletionException(err);
      }
      socketInstancesByAccounts.remove(accountId);
      return response;
    });
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
   * Adds latency listener
   * @param listener latency listener to add
   */
  public void addLatencyListener(LatencyListener listener) {
    latencyListeners.add(listener);
  }
  
  /**
   * Removes latency listener
   * @param listener latency listener to remove
   */
  public void removeLatencyListener(LatencyListener listener) {
    latencyListeners.remove(listener);
  }
  
  /**
   * Adds reconnect listener
   * @param listener reconnect listener to add
   * @param accountId account id of listener
   */
  public void addReconnectListener(ReconnectListener listener, String accountId) {
    String aid = accountId;
    ReconnectListener l = listener;
    reconnectListeners.add(new ReconnectListenerItem() {{ accountId = aid; listener = l; }});
  }
  
  /**
   * Removes reconnect listener
   * @param listener listener to remove
   */
  public void removeReconnectListener(ReconnectListener listener) {
    reconnectListeners.removeIf(item -> item.listener == listener);
  }
  
  /**
   * Removes all listeners. Intended for use in unit tests.
   */
  public void removeAllListeners() {
    synchronizationListeners.clear();
    reconnectListeners.clear();
  }
  
  /**
   * Queues an account packet for processing
   * @param packet packet to process
   */
  public void queuePacket(JsonNode packet) {
    String accountId = packet.get("accountId").asText();
    List<JsonNode> packets = packetOrderer.restoreOrder(packet).stream()
      .filter(p -> !p.get("type").asText().equals("noop"))
      .collect(Collectors.toList());
    if (sequentialEventProcessing && packet.has("sequenceNumber")) {
      List<Supplier<CompletableFuture<Void>>> events = packets.stream().map(packetItem -> {
        return new Supplier<CompletableFuture<Void>>() {
          @Override
          public CompletableFuture<Void> get() {
            return processSynchronizationPacket(packetItem);
          }
        };
      }).collect(Collectors.toList());
      if (!eventQueues.containsKey(accountId)) {
        eventQueues.put(accountId, new ArrayList<>(events));
        callAccountEvents(accountId);
      } else {
        eventQueues.get(accountId).addAll(events);
      }
    } else {
      packets.forEach(packetItem -> processSynchronizationPacket(packetItem));
    }
  }
  
  /**
   * Queues account event for processing
   * @param accountId account id
   * @param event event to execute
   */
  public void queueEvent(String accountId, Supplier<CompletableFuture<Void>> event) {
    if (sequentialEventProcessing) {
      if (!eventQueues.containsKey(accountId)) {
        List<Supplier<CompletableFuture<Void>>> events = new ArrayList<>();
        events.add(event);
        eventQueues.put(accountId, events);
        callAccountEvents(accountId);
      } else {
        eventQueues.get(accountId).add(event);
      }
    } else {
      event.get();
    }
  }
  
  private CompletableFuture<Void> callAccountEvents(String accountId) {
    return Async.run(() -> {
      if (eventQueues.containsKey(accountId)) {
        while (eventQueues.get(accountId).size() > 0) {
          eventQueues.get(accountId).get(0).get().join();
          eventQueues.get(accountId).remove(0);
        }
        eventQueues.remove(accountId);
      }
    });
  }
  
  private CompletableFuture<Void> reconnect(int socketInstanceIndex) {
    return Async.run(() -> {
      try {
        if (socketInstances.size() > socketInstanceIndex) {
          SocketInstance instance = socketInstances.get(socketInstanceIndex);
          while (!instance.socket.connected() && !instance.isReconnecting && instance.connected) {
            tryReconnect(socketInstanceIndex);
          }
        }
      } catch (Throwable err) {
        throw new CompletionException(err);
      }
    });
  }
  
  private void tryReconnect(int socketInstanceIndex) throws Exception {
    SocketInstance instance = socketInstances.get(socketInstanceIndex);
    Thread.sleep(1000);
    if (!instance.socket.connected() && !instance.isReconnecting && instance.connected) {
      instance.sessionId = RandomStringUtils.randomAlphanumeric(32);
      instance.socket.close();
      instance.clientId = Math.random();
      instance.isReconnecting = true;
      createSocket(instance, getServerUrl(), null);
      instance.socket.connect();
    }
  }
  
  private CompletableFuture<JsonNode> rpcRequest(String accountId, ObjectNode request) {
    return rpcRequest(accountId, request, null);
  }
  
  protected CompletableFuture<JsonNode> rpcRequest(String accountId, ObjectNode request, Long timeoutInSeconds) {
    return Async.supply(() -> {
      try {
        Integer socketInstanceIndex = null;
        if (socketInstancesByAccounts.containsKey(accountId)) {
          socketInstanceIndex = socketInstancesByAccounts.get(accountId);
        } else {
          while (subscribeLock != null && ((subscribeLock.recommendedRetryTime.getDate().getTime() > Date.from(Instant.now()).getTime()
            && getSubscribedAccountIds().size() < subscribeLock.lockedAtAccounts) || 
            (subscribeLock.lockedAtTime + subscribeCooldownInSeconds * 1000 > 
            Date.from(Instant.now()).getTime() && getSubscribedAccountIds().size() >= subscribeLock.lockedAtAccounts))) {
            Thread.sleep(1000);
          }
          for (int index = 0; index < socketInstances.size(); index++) {
            int accountCounter = getAssignedAccounts(index).size();
            SocketInstance instance = socketInstances.get(index);
            if (instance.subscribeLock != null) {
              if (instance.subscribeLock.type.equals("LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER_PER_SERVER") && 
              (instance.subscribeLock.recommendedRetryTime.getDate().getTime() > Date.from(Instant.now()).getTime() || 
              getSubscribedAccountIds(index).size() >= instance.subscribeLock.lockedAtAccounts)) {
                continue;
              }
              if (instance.subscribeLock.type.equals("LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_SERVER") && 
              instance.subscribeLock.recommendedRetryTime.getDate().getTime() > Date.from(Instant.now()).getTime() &&
              getSubscribedAccountIds(index).size() >= instance.subscribeLock.lockedAtAccounts) {
                continue;
              }
            }
            if (accountCounter < maxAccountsPerInstance) {
              socketInstanceIndex = index;
              break;
            }
          }
          if (socketInstanceIndex == null) {
            socketInstanceIndex = socketInstances.size();
            connect().join();
          }
          socketInstancesByAccounts.put(accountId, socketInstanceIndex);
        }
        SocketInstance instance = socketInstances.get(socketInstanceIndex);
        if (!instance.connected) {
          connect().join();
        } else if (!isConnected(socketInstanceIndex)) {
          instance.connectResult.join();
        }
        if (request.get("type").asText().equals("subscribe")) {
          request.put("sessionId", instance.sessionId);
        }
        if (Arrays.asList("trade", "subscribe").indexOf(request.get("type").asText()) != -1) {
          return makeRequest(accountId, request, timeoutInSeconds);
        }
        int retryCounter = 0;
        while (true) {
          try {
            return makeRequest(accountId, request, timeoutInSeconds);
          } catch (Throwable err) {
            if (err instanceof TooManyRequestsException) {
              int calcRetryCounter = retryCounter;
              int calcRequestTime = 0;
              while (calcRetryCounter < retries) {
                calcRetryCounter++;
                calcRequestTime += Math.min(Math.pow(2, calcRetryCounter) * minRetryDelayInSeconds,
                  maxRetryDelayInSeconds) * 1000;
              }
              long retryTime = ((TooManyRequestsException) err).metadata.recommendedRetryTime.getDate().getTime();
              if (Date.from(Instant.now()).getTime() + calcRequestTime > retryTime && retryCounter < retries) {
                if (Date.from(Instant.now()).getTime() < retryTime) {
                  Thread.sleep(retryTime - Date.from(Instant.now()).getTime());
                }
                retryCounter++;
              } else {
                throw err;
              }
            } else if (Arrays.asList(NotSynchronizedException.class, TimeoutException.class, NotConnectedException.class,
              InternalException.class).indexOf(err.getClass()) != -1 && retryCounter < retries) {
              Thread.sleep((long) (Math.min(Math.pow(2, retryCounter) * minRetryDelayInSeconds,
                maxRetryDelayInSeconds) * 1000));
              retryCounter++;
            } else {
              throw err;
            }
            if (!socketInstancesByAccounts.containsKey(accountId)) {
              throw err;
            }
          }
        }
      } catch (Throwable err) {
        throw new CompletionException(err);
      }
    });
  }
  
  private JsonNode makeRequest(String accountId, ObjectNode request, Long timeoutInSeconds) throws Throwable {
    SocketInstance socketInstance = socketInstances.get(socketInstancesByAccounts.get(accountId));
    String requestId = request.has("requestId") ? request.get("requestId").asText() : UUID.randomUUID().toString();
    try {
      ObjectNode timestamps = jsonMapper.createObjectNode();
      timestamps.put("clientProcessingStarted", new IsoTime(Date.from(Instant.now())).toString());
      request.set("timestamps", timestamps);
      RequestResolve resolve = new RequestResolve() {{
        future = new CompletableFuture<>();
        type = request.get("type").asText();
      }};
      socketInstance.requestResolves.put(requestId, resolve);
      request.put("accountId", accountId);
      if (!request.has("application")) {
        request.put("application", application);
      }
      if (!request.has("requestId")) {
        request.put("requestId", requestId);
      }
      socketInstance.socket.emit("request", new JSONObject(jsonMapper.writeValueAsString(request)));
      if (timeoutInSeconds != null) {
        return resolve.future.get(timeoutInSeconds, TimeUnit.SECONDS);
      } else {
        return resolve.future.get(requestTimeout, TimeUnit.MILLISECONDS);
      }
    } catch (java.util.concurrent.TimeoutException e) {
      throw new TimeoutException("MetaApi websocket client request " + requestId 
          + " of type " + request.get("type").asText() + " timed out. Please make sure your account is "
          + "connected to broker before retrying your request.");
    } catch (InterruptedException | JsonProcessingException | JSONException e) {
      throw e;
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }
  
  private Exception convertError(WebsocketError error) {
    switch (error.error) {
      case "ValidationError":
        Object details = null;
        try {
          if (error.details != null) {
            details = JsonMapper.getInstance().treeToValue(error.details, Object.class);
          }
        } catch (JsonProcessingException e) {
          logger.error("Failed to parse validation error details: " + error.details, e);
        }
        return new ValidationException(error.message, details);
      case "NotFoundError": return new NotFoundException(error.message);
      case "NotSynchronizedError": return new NotSynchronizedException(error.message);
      case "TimeoutError": return new TimeoutException(error.message);
      case "NotAuthenticatedError": return new NotConnectedException(error.message);
      case "TradeError": return new TradeException(error.message, error.numericCode, error.stringCode);
      case "UnauthorizedError": close(); return new UnauthorizedException(error.message);
      case "TooManyRequestsError":
        TooManyRequestsExceptionMetadata metadata = null;
        try {
          if (error.metadata != null) {
            metadata = JsonMapper.getInstance().treeToValue(error.metadata, TooManyRequestsExceptionMetadata.class);
          }
        } catch (JsonProcessingException e) {
          logger.error("Failed to parse too many requests error metadata: " + error.metadata, e);
        }
        return new TooManyRequestsException(error.message, metadata);
      default: return new InternalException(error.message);
    }
  }
  
  private CompletableFuture<Void> processSynchronizationPacket(JsonNode data) {
    return Async.run(() -> {
      try {
        String accountId = data.get("accountId").asText();
        Integer socketInstanceIndex = socketInstancesByAccounts.get(accountId);
        SocketInstance socketInstance = (socketInstanceIndex != null && socketInstances.size() > socketInstanceIndex)
            ? socketInstances.get(socketInstanceIndex) : null;
        String synchronizationId = data.has("synchronizationId") ? data.get("synchronizationId").asText() : null;
        if (synchronizationId != null && socketInstance != null) {
          socketInstance.synchronizationThrottler.updateSynchronizationId(synchronizationId);
        }
        int instanceNumber = data.has("instanceIndex") ? data.get("instanceIndex").asInt() : 0;
        String host = data.has("host") ? data.get("host").asText() : null;
        String instanceId = accountId + ":" + instanceNumber + ":" + (Js.or(host, 0));
        String instanceIndex = instanceNumber + ":" + (Js.or(host, 0));
        List<SynchronizationListener> listeners = synchronizationListeners.containsKey(accountId)
          ? synchronizationListeners.get(accountId) : new ArrayList<>();
          
        Supplier<Boolean> isOnlyActiveInstance = () -> {
          List<String> activeInstanceIds = connectedHosts.keySet().stream().filter(instance ->
            instance.startsWith(accountId + ":" + instanceNumber)).collect(Collectors.toList());
          return activeInstanceIds.size() == 0 || activeInstanceIds.size() == 1 && activeInstanceIds.get(0).equals(instanceId);
        };
        
        Function<Boolean, CompletableFuture<Void>> onDisconnected = (isTimeout) -> {
          return Async.run(() -> {
            if (connectedHosts.containsKey(instanceId)) {
              if (isOnlyActiveInstance.get()) {
                List<CompletableFuture<Void>> onDisconnectedFutures = new ArrayList<>();
                if (!isTimeout) {
                  onDisconnectedFutures.add(subscriptionManager.onDisconnected(accountId, instanceNumber));
                }
                for (SynchronizationListener listener : listeners) {
                  onDisconnectedFutures.add(listener.onDisconnected(instanceIndex).exceptionally(e -> {
                    logger.error(accountId + ":" + instanceIndex + ": Failed to notify "
                      + "listener about disconnected event", e);
                    return null;
                  }));
                }
                CompletableFuture.allOf(onDisconnectedFutures.toArray(new CompletableFuture<?>[0])).join();
              } else {
                List<CompletableFuture<Void>> onStreamClosedFutures = new ArrayList<>();
                packetOrderer.onStreamClosed(instanceId);
                socketInstance.synchronizationThrottler.removeIdByParameters(accountId, instanceNumber, host);
                for (SynchronizationListener listener : listeners) {
                  onStreamClosedFutures.add(listener.onStreamClosed(instanceIndex).exceptionally(e -> {
                    logger.error(accountId + ":" + instanceIndex + ": Failed to notify "
                      + "listener about stream closed event", e);
                    return null;
                  }));
                }
                CompletableFuture.allOf(onStreamClosedFutures.toArray(new CompletableFuture<?>[0])).join();
              }
              connectedHosts.remove(instanceId);
            }
          });
        };
        
        Runnable cancelDisconnectTimer = () -> {
          if (statusTimers.containsKey(instanceId)) {
            statusTimers.get(instanceId).cancel();
          }
        };
        
        Runnable resetDisconnectTimer = () -> {
          cancelDisconnectTimer.run();
          statusTimers.put(instanceId, Js.setTimeout(() -> {
            if (isOnlyActiveInstance.get()) {
              subscriptionManager.onTimeout(accountId, instanceNumber);
            }
            queueEvent(accountId, () -> onDisconnected.apply(true));
          }, resetDisconnectTimerTimeout));
        };
        
        String type = data.get("type").asText();
        if (type.equals("authenticated")) {
          resetDisconnectTimer.run();
          if (!data.has("sessionId") || data.get("sessionId").asText().equals(socketInstance.sessionId)) {
            connectedHosts.put(instanceId, "" + host);
            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              completableFutures.add(listener.onConnected(instanceIndex, data.get("replicas").asInt())
                .exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about connected event", e);
                return null;
              }));
            }
            subscriptionManager.cancelSubscribe(accountId + ":" + instanceNumber);
            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
          }
        } else if (type.equals("disconnected")) {
          cancelDisconnectTimer.run();
          onDisconnected.apply(false).get();
        } else if (type.equals("synchronizationStarted")) {
          List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            completableFutures.add(listener.onSynchronizationStarted(instanceIndex).exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about synchronization "
                + "started event", e);
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
              completableFutures.add(listener.onAccountInformationUpdated(instanceIndex, 
                  accountInformation).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify "
                  + "listener about accountInformation event", e);
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
                completableFutures.add(listener.onDealAdded(instanceIndex, deal).exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about deals event", e);
                  return null;
                }));
              }
              CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
            }
          }
        } else if (type.equals("orders")) {
          List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
          MetatraderOrder[] orders = data.hasNonNull("orders")
            ? jsonMapper.treeToValue(data.get(type), MetatraderOrder[].class)
            : new MetatraderOrder[0];
          for (SynchronizationListener listener : listeners) {
            completableFutures.add(listener.onOrdersReplaced(instanceIndex, Arrays.asList(orders))
                .exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about orders event", e);
              return null;
            }));
          }
          CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
        } else if (type.equals("historyOrders")) {
          if (data.hasNonNull(type)) {
            MetatraderOrder[] historyOrders = jsonMapper.treeToValue(data.get(type), MetatraderOrder[].class);
            for (MetatraderOrder historyOrder : historyOrders) {
              List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
              for (SynchronizationListener listener : listeners) {
                completableFutures.add(listener.onHistoryOrderAdded(instanceIndex, historyOrder)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify "
                    + "listener about historyOrders event", e);
                  return null;
                }));
              }
              CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
            }
          }
        } else if (type.equals("positions")) {
          List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
          MetatraderPosition[] positions = data.hasNonNull("positions")
            ? jsonMapper.treeToValue(data.get(type), MetatraderPosition[].class)
            : new MetatraderPosition[0];
          for (SynchronizationListener listener : listeners) {
            completableFutures.add(listener.onPositionsReplaced(instanceIndex, Arrays.asList(positions))
                .exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about positions event", e);
              return null;
            }));
          }
          CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
        } else if (type.equals("update")) {
          if (data.hasNonNull("accountInformation")) {
            MetatraderAccountInformation accountInformation = jsonMapper
              .treeToValue(data.get("accountInformation"), MetatraderAccountInformation.class);
            List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              completableFutures.add(listener.onAccountInformationUpdated(instanceIndex,
                  accountInformation).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onPositionUpdated(instanceIndex, position)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onPositionRemoved(instanceIndex, positionId)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onOrderUpdated(instanceIndex, order)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onOrderCompleted(instanceIndex, orderId)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onHistoryOrderAdded(instanceIndex, historyOrder)
                    .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
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
                completableFutures.add(listener.onDealAdded(instanceIndex, deal).exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about update event", e);
                  return null;
                }));
              }
              CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
            }
          }
          if (data.has("timestamps")) {
            UpdateTimestamps timestamps = jsonMapper.treeToValue(data.get("timestamps"),
              UpdateTimestamps.class);
            timestamps.clientProcessingFinished = new IsoTime();
            List<CompletableFuture<Void>> onUpdateFutures = new ArrayList<>();
            for (LatencyListener listener : latencyListeners) {
              onUpdateFutures.add(listener.onUpdate(accountId, timestamps).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify latency "
                  + "listener about update event", e);
                return null;
              }));
            }
            CompletableFuture.allOf(onUpdateFutures.toArray(new CompletableFuture<?>[0])).get();
          }
        } else if (type.equals("dealSynchronizationFinished")) {
          List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            if (socketInstance != null) {
              socketInstance.synchronizationThrottler.removeSynchronizationId(synchronizationId);
            }
            completableFutures.add(listener.onDealSynchronizationFinished(instanceIndex,
                data.get("synchronizationId").asText()).exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                + "about dealSynchronizationFinished event", e);
              return null;
            }));
          }
          CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
        } else if (type.equals("orderSynchronizationFinished")) {
          List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            completableFutures.add(listener.onOrderSynchronizationFinished(instanceIndex,
                data.get("synchronizationId").asText()).exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                + "about orderSynchronizationFinished event", e);
              return null;
            }));
          }
          CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture<?>[0])).get();
        } else if (type.equals("status")) {
          if (!connectedHosts.containsKey(instanceId)) {
            if (statusTimers.containsKey(instanceId) && data.has("authenticated") && data.get("authenticated").asBoolean() && 
              (subscriptionManager.isDisconnectedRetryMode(accountId, instanceNumber) || 
              !subscriptionManager.isAccountSubscribing(accountId, instanceNumber))) {
              subscriptionManager.cancelSubscribe(accountId + ":" + instanceNumber);
              Thread.sleep(10);
              logger.info("It seems like we are not connected to a running API server yet, "
                + "retrying subscription for account " + instanceId);
              ensureSubscribe(accountId, instanceNumber);
            }
          } else {
            resetDisconnectTimer.run();
            List<CompletableFuture<Void>> onBrokerConnectionStatusChangedFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              onBrokerConnectionStatusChangedFutures.add(listener
                  .onBrokerConnectionStatusChanged(instanceIndex, data.get("connected").asBoolean())
                  .exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                  + "about brokerConnectionStatusChanged event", e);
                return null;
              }));
            }
            CompletableFuture.allOf(onBrokerConnectionStatusChangedFutures.toArray(new CompletableFuture<?>[0])).get();
            if (data.hasNonNull("healthStatus")) {
              List<CompletableFuture<Void>> onHealthStatusFutures = new ArrayList<>();
              for (SynchronizationListener listener : listeners) {
                onHealthStatusFutures.add(listener.onHealthStatus(instanceIndex, 
                    jsonMapper.treeToValue(data.get("healthStatus"), SynchronizationListener.HealthStatus.class))
                  .exceptionally(e -> {
                  logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                    + "about server-side healthStatus event", e);
                  return null;
                }));
              }
              CompletableFuture.allOf(onHealthStatusFutures.toArray(new CompletableFuture<?>[0])).get();
            }
          }
        } else if (type.equals("downgradeSubscription")) {
          logger.info(accountId + ":" + instanceIndex + ": Market data subscriptions for symbol " + data.get("symbol") 
            + " were downgraded by " + "the server due to rate limits. Updated subscriptions: " + data.get("updates")
            + ", removed subscriptions: " + data.get("unsubscriptions") 
            + ". Please read https://metaapi.cloud/docs/client/rateLimiting/ for more details.");
          List<CompletableFuture<Void>> onSubscriptionDowngradeFutures = new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            onSubscriptionDowngradeFutures.add(listener.onSubscriptionDowngraded(instanceIndex, data.get("symbol").asText(),
              data.has("updates")
                ? Arrays.asList(jsonMapper.treeToValue(data.get("updates"), MarketDataSubscription[].class))
                : new ArrayList<>(),
              data.has("unsubscriptions")
                ? Arrays.asList(jsonMapper.treeToValue(data.get("unsubscriptions"), MarketDataUnsubscription[].class))
                : new ArrayList<>()
            ).exceptionally(e -> {
              logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                + "about subscription downgrade event", e);
              return null;
            }));
          }
          CompletableFuture.allOf(onSubscriptionDowngradeFutures.toArray(new CompletableFuture<?>[0])).get();
        } else if (type.equals("specifications")) {
          List<CompletableFuture<Void>> onSymbolSpecificationsUpdatedFutures = new ArrayList<>();
          List<MetatraderSymbolSpecification> specifications = data.hasNonNull("specifications")
            ? Arrays.asList(jsonMapper.treeToValue(data.get("specifications"), MetatraderSymbolSpecification[].class))
            : new ArrayList<>();
          List<String> removedSymbols = data.hasNonNull("removedSymbols")
            ? Arrays.asList(jsonMapper.treeToValue(data.get("removedSymbols"), String[].class))
            : new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            onSymbolSpecificationsUpdatedFutures.add(
              listener.onSymbolSpecificationsUpdated(instanceIndex, specifications, removedSymbols)
                  .exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                  + "about specifications updated event", e);
                return null;
              }));
          }
          CompletableFuture.allOf(onSymbolSpecificationsUpdatedFutures.toArray(new CompletableFuture<?>[0])).get();
          for (MetatraderSymbolSpecification specification : specifications) {
            List<CompletableFuture<Void>> onSymbolSpecificationUpdatedFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              onSymbolSpecificationUpdatedFutures.add(listener.onSymbolSpecificationUpdated(instanceIndex,
                  specification).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                  + "about specification updated event", e);
                return null;
              }));
            }
            CompletableFuture.allOf(onSymbolSpecificationUpdatedFutures.toArray(new CompletableFuture<?>[0])).get();
          }
          for (String removedSymbol : removedSymbols) {
            List<CompletableFuture<Void>> onSymbolSpecificationRemovedFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              onSymbolSpecificationRemovedFutures.add(listener.onSymbolSpecificationRemoved(instanceIndex,
                  removedSymbol).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener "
                  + "about specifications removed event", e);
                return null;
              }));
            }
            CompletableFuture.allOf(onSymbolSpecificationRemovedFutures.toArray(new CompletableFuture<?>[0])).get();
          }
        } else if (type.equals("prices")) {
          List<MetatraderSymbolPrice> prices = Arrays.asList(data.hasNonNull("prices")
            ? jsonMapper.treeToValue(data.get("prices"), MetatraderSymbolPrice[].class)
            : new MetatraderSymbolPrice[0]);
          List<MetatraderCandle> candles = Arrays.asList(data.hasNonNull("candles")
            ? jsonMapper.treeToValue(data.get("candles"), MetatraderCandle[].class)
            : new MetatraderCandle[0]);
          List<MetatraderTick> ticks = Arrays.asList(data.hasNonNull("ticks")
            ? jsonMapper.treeToValue(data.get("ticks"), MetatraderTick[].class)
            : new MetatraderTick[0]);
          List<MetatraderBook> books = Arrays.asList(data.hasNonNull("books")
            ? jsonMapper.treeToValue(data.get("books"), MetatraderBook[].class)
            : new MetatraderBook[0]);
          List<CompletableFuture<Void>> onPricesUpdatedFutures = new ArrayList<>();
          for (SynchronizationListener listener : listeners) {
            if (prices.size() != 0) {
              onPricesUpdatedFutures.add(listener.onSymbolPricesUpdated(instanceIndex, prices, 
                data.has("equity") ? data.get("equity").asDouble() : null,
                data.has("margin") ? data.get("margin").asDouble() : null, 
                data.has("freeMargin") ? data.get("freeMargin").asDouble() : null,
                data.has("marginLevel") ? data.get("marginLevel").asDouble() : null,
                data.has("accountCurrencyExchangeRate")
                  ? data.get("accountCurrencyExchangeRate").asDouble() : null
                ).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about prices event", e);
                return null;
              }));
            }
            if (candles.size() != 0) {
              onPricesUpdatedFutures.add(listener.onCandlesUpdated(instanceIndex, candles, 
                data.has("equity") ? data.get("equity").asDouble() : null,
                data.has("margin") ? data.get("margin").asDouble() : null, 
                data.has("freeMargin") ? data.get("freeMargin").asDouble() : null,
                data.has("marginLevel") ? data.get("marginLevel").asDouble() : null,
                data.has("accountCurrencyExchangeRate")
                  ? data.get("accountCurrencyExchangeRate").asDouble() : null
                ).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about candles event", e);
                return null;
              }));
            }
            if (ticks.size() != 0) {
              onPricesUpdatedFutures.add(listener.onTicksUpdated(instanceIndex, ticks, 
                data.has("equity") ? data.get("equity").asDouble() : null,
                data.has("margin") ? data.get("margin").asDouble() : null, 
                data.has("freeMargin") ? data.get("freeMargin").asDouble() : null,
                data.has("marginLevel") ? data.get("marginLevel").asDouble() : null,
                data.has("accountCurrencyExchangeRate")
                  ? data.get("accountCurrencyExchangeRate").asDouble() : null
                ).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about ticks event", e);
                return null;
              }));
            }
            if (books.size() != 0) {
              onPricesUpdatedFutures.add(listener.onBooksUpdated(instanceIndex, books, 
                data.has("equity") ? data.get("equity").asDouble() : null,
                data.has("margin") ? data.get("margin").asDouble() : null, 
                data.has("freeMargin") ? data.get("freeMargin").asDouble() : null,
                data.has("marginLevel") ? data.get("marginLevel").asDouble() : null,
                data.has("accountCurrencyExchangeRate")
                  ? data.get("accountCurrencyExchangeRate").asDouble() : null
                ).exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about books event", e);
                return null;
              }));
            }
          }
          CompletableFuture.allOf(onPricesUpdatedFutures.toArray(new CompletableFuture<?>[0])).get();
          for (MetatraderSymbolPrice price : prices) {
            List<CompletableFuture<Void>> onPriceUpdatedFutures = new ArrayList<>();
            for (SynchronizationListener listener : listeners) {
              onPriceUpdatedFutures.add(listener.onSymbolPriceUpdated(instanceIndex, price)
                  .exceptionally(e -> {
                logger.error(accountId + ":" + instanceIndex + ": Failed to notify listener about price event", e);
                return null;
              }));
            }
            CompletableFuture.allOf(onPriceUpdatedFutures.toArray(new CompletableFuture<?>[0])).get();
          }
          for (MetatraderSymbolPrice price : prices) {
            if (price.timestamps != null) {
              price.timestamps.clientProcessingFinished = new IsoTime(Date.from(Instant.now()));
              List<CompletableFuture<Void>> onSymbolPriceFutures = new ArrayList<>();
              for (LatencyListener listener : latencyListeners) {
                onSymbolPriceFutures.add(listener.onSymbolPrice(accountId, price.symbol, price.timestamps)
                  .exceptionally(e -> {
                    logger.error(accountId + ":" + instanceIndex + ": Failed to notify latency listener "
                      + "about price event", e);
                    return null;
                  }));
              }
              CompletableFuture.allOf(onSymbolPriceFutures.toArray(new CompletableFuture<?>[0])).get();
            }
          }
        }
      } catch (JsonProcessingException | InterruptedException | ExecutionException e) {
        logger.error("Failed to process incoming synchronization packet", e);
      }
    });
  }
  
  private CompletableFuture<Void> fireReconnected(int socketInstanceIndex) {
    return Async.run(() -> {
      List<ReconnectListenerItem> reconnectListeners = new ArrayList<>();
      for (ReconnectListenerItem listener : this.reconnectListeners) {
        if (socketInstancesByAccounts.getOrDefault(listener.accountId, -1) == socketInstanceIndex) {
          reconnectListeners.add(listener);
        }
      }
      List<String> reconnectAccountIds = reconnectListeners.stream()
        .map(listener -> listener.accountId).collect(Collectors.toList());
      subscriptionManager.onReconnected(socketInstanceIndex, reconnectAccountIds);
      packetOrderer.onReconnected(reconnectAccountIds);

      for (ReconnectListenerItem listener : reconnectListeners) {
        queueEvent(listener.accountId, () -> listener.listener.onReconnected().exceptionally(err -> {
          logger.error("[" + new IsoTime() + "] Failed to notify reconnect listener", err);
          return null;
        }));
      }
    });
  }
  
  protected String getServerUrl() throws Exception {
    boolean isDefaultRegion = this.region == null;
    if (this.region != null) {
      HttpRequestOptions opts = new HttpRequestOptions("https://mt-provisioning-api-v1." + this.domain +
        "/users/current/regions", Method.GET);
      opts.getHeaders().put("auth-token", token);
      List<String> regions = Arrays.asList(httpClient.requestJson(opts, String[].class).join());
      if (!regions.contains(this.region)) {
        String errorMessage = "The region \"" + this.region + "\" you are trying to connect to does not exist " +
          "or is not available to you. Please specify a correct region name in the region MetaApi constructor option.";
        logger.error(errorMessage);
        throw new NotFoundException(errorMessage);
      }
      if (this.region.equals(regions.get(0))) {
        isDefaultRegion = true;
      }
    }
    
    String url;
    if (this.useSharedClientApi) {
      if (isDefaultRegion) {
        url = this.url;
      } else {
        url = "https://" + this.hostname + "." + this.region + "." + this.domain;
      }
    } else {
      HttpRequestOptions opts = new HttpRequestOptions("https://mt-provisioning-api-v1." + 
        domain + "/users/current/servers/mt-client-api", Method.GET);
      opts.getHeaders().put("auth-token", token);
      JsonNode response = jsonMapper.readTree(httpClient.request(opts).join());
      if (isDefaultRegion) {
        url = response.get("url").asText();
      } else {
        url = "https://" + response.get("hostname").asText() + "." + this.region + "." +
          response.get("domain").asText();
      }
    }
    return url;
  }
}