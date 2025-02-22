package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.corundumstudio.socketio.listener.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.RetryOptions;
import cloud.metaapi.sdk.clients.SocketClient;
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.*;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.ResponseTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.SymbolPriceTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.TradeTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.UpdateTimestamps;
import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener.HealthStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataSubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataUnsubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderBook;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderBookEntry;
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
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade.*;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.util.Js;
import cloud.metaapi.sdk.util.JsonMapper;
import io.github.artsok.RepeatedIfExceptionsTest;

import com.corundumstudio.socketio.*;

/**
 * Tests {@link MetaApiWebsocketClient}
 */
class MetaApiWebsocketClientTest {

  private static ObjectMapper jsonMapper = JsonMapper.getInstance();
  private static SocketIOServer io;
  private static SocketClient server;
  private static int initResetDisconnectTimerTimeout;
  private MetaApiWebsocketClient client;
  private SubscriptionManager clientSubscriptionManager;
  private PacketOrderer clientPacketOrderer;
  private HttpClient httpClient;
  
  // Some variables that cannot be written from request callbacks 
  // if they are local test variables
  private boolean requestReceived = false;
  private MetatraderTrade actualTrade = null;
  private ResponseTimestamps timestamps = null;
  private int requestCounter = 0;
  private int connectAmount = 0;
  private double clientId = 0;
  
  @BeforeAll
  static void setUpBeforeClass() throws IOException, IllegalAccessException {
    initResetDisconnectTimerTimeout = MetaApiWebsocketClient.resetDisconnectTimerTimeout;
    Configuration serverConfiguration = new Configuration();
    serverConfiguration.setPort(6784);
    serverConfiguration.setContext("/ws");
    io = new SocketIOServer(serverConfiguration);
    io.addConnectListener(new ConnectListener() {
      @Override
      public void onConnect(SocketIOClient socket) {
        server = new SocketClient(io, socket);
        if (!socket.getHandshakeData().getSingleUrlParam("auth-token").equals("token")) {
          socket.sendEvent("UnauthorizedError", "Authorization token invalid");
          socket.disconnect();
        }
      }
    });
    io.start();
  }
  
  @AfterAll
  static void tearDownAfterClass() {
  	io.stop();
  }

  @BeforeEach
  void setUp() throws Throwable {
    MetaApiWebsocketClient.resetDisconnectTimerTimeout = initResetDisconnectTimerTimeout;
    httpClient = Mockito.mock(HttpClient.class);
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 6000L;
      connectTimeout = 6000L;
      retryOpts = new RetryOptions() {{
        retries = 3;
        minDelayInSeconds = 1;
        maxDelayInSeconds = 3;
      }};
      useSharedClientApi = true;
    }});
    client.setUrl("http://localhost:6784");
    client.socketInstancesByAccounts = Maps.newHashMap("accountId", 0);
    client.connect().join();
    SynchronizationThrottler clientSyncThrottler = Mockito
      .spy(client.socketInstances.get(0).synchronizationThrottler);
    Mockito.doReturn(Lists.emptyList()).when(clientSyncThrottler).getActiveSynchronizationIds();
    client.socketInstances.get(0).synchronizationThrottler = clientSyncThrottler;
    
    clientSubscriptionManager = (SubscriptionManager) FieldUtils.readField(client, "subscriptionManager", true);
    clientSubscriptionManager = Mockito.spy(clientSubscriptionManager);
    FieldUtils.writeField(client, "subscriptionManager", clientSubscriptionManager, true);
    
    spyClientPacketOrderer();
  }

  @AfterEach
  void tearDown() {
  	io.removeAllListeners("request");
    client.removeAllListeners();
    client.close();
  }
  
  
  /**
   * Tests {@link MetaApiWebsocketClient#connect()}
   */
  @Test
  void testSendsClientIdWhenConnects() {
    String clientId = server.getHandshakeData().getSingleUrlParam("clientId");
    assertTrue(clientId != null);
    assertTrue(Double.valueOf(clientId) >= 0 && Double.valueOf(clientId) < 1);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#tryReconnect()}
   */
  @Test
  void testChangesClientIdOnReconnect() throws InterruptedException {
    clientId = Double.valueOf(server.getHandshakeData().getSingleUrlParam("clientId"));
    connectAmount = 0;
    client.close();
    
    io.addConnectListener(new ConnectListener() {
      @Override
      public void onConnect(SocketIOClient connected) {
        server = new SocketClient(io, connected);
        connectAmount++;
        double headerClientId = Double.valueOf(server.getHandshakeData().getHttpHeaders().get("client-id"));
        double queryClientId = Double.valueOf(server.getHandshakeData().getSingleUrlParam("clientId"));
        assertEquals(headerClientId, queryClientId);
        assertNotEquals(clientId, headerClientId);
        assertNotEquals(clientId, queryClientId);
        clientId = queryClientId;
        if (connectAmount == 1) {
          server.disconnect();
        }
      }
    });
    client.connect().join();
    Thread.sleep(2000);
    assertTrue(connectAmount >= 2);
  }
  
  /**
    * Tests {@link MetaApiWebsocketClient#getServerUrl}
    */
  @ParameterizedTest
  @MethodSource("provideMetatraderPosition")
  void testConnectsToDedicatedServer(MetatraderPosition position) throws Exception {
    Mockito.when(httpClient.request(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture("{\"url\": \"http://localhost:6784\"}"));
    List<MetatraderPosition> positions = Arrays.asList(position);
    server.disconnect();
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 15000L;
      connectTimeout = 15000L;
      useSharedClientApi = false;
      retryOpts = new RetryOptions() {{
        retries = 3;
        minDelayInSeconds = 1;
        maxDelayInSeconds = 3;
      }};
    }});
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getPositions") 
            && request.get("accountId").asText().equals("accountId")
            && request.get("application").asText().equals("RPC")
          ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("positions", jsonMapper.valueToTree(positions));
          client.sendEvent("response", response.toString());
        }
      }
    });
    List<MetatraderPosition> actual = client.getPositions("accountId").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(positions);
    Mockito.verify(httpClient).request(Mockito.any());
  }

  /**
    * Tests {@link MetaApiWebsocketClient#getServerUrl}
    */
  @Test
  void testThrowsErrorIfRegionNotFound() throws Exception {
    client.close();
    Mockito.when(httpClient.requestJson(Mockito.any(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<String[]>>() {
      public CompletableFuture<String[]> answer(InvocationOnMock invocation) throws Throwable {
        HttpRequestOptions opts = invocation.getArgument(0, HttpRequestOptions.class);
        if (opts.getUrl().equals("https://mt-provisioning-api-v1.project-stock.agiliumlabs.cloud/users/current/" +
          "regions")) {
          return CompletableFuture.completedFuture(new String[] {"canada", "us-west"});
        }
        return CompletableFuture.completedFuture(null);
      }
    });
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      region = "wrong";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 15000L;
      connectTimeout = 15000L;
      useSharedClientApi = false;
    }});
    try {
      client.getServerUrl();
      Assertions.fail("Not found error expected");
    } catch (Exception err) {
      assertTrue(err instanceof NotFoundException);
    }
  }
  
  /**
   * Test {@link MetaApiWebsocketClient#getServerUrl}
   */
  @Test
  void testConnectsToLegacyUrlIfDefaultRegionSelected() throws Exception {
    client.close();
    Mockito.when(httpClient.requestJson(Mockito.any(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<String[]>>() {
      public CompletableFuture<String[]> answer(InvocationOnMock invocation) throws Throwable {
        HttpRequestOptions opts = invocation.getArgument(0, HttpRequestOptions.class);
        if (opts.getUrl().equals("https://mt-provisioning-api-v1.project-stock.agiliumlabs.cloud/users/current/" +
          "regions")) {
          return CompletableFuture.completedFuture(new String[] {"canada", "us-west"});
        }
        return CompletableFuture.completedFuture(null);
      }
    });
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      region = "canada";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 15000L;
      connectTimeout = 15000L;
      useSharedClientApi = true;
    }});
    String url = client.getServerUrl();
    assertEquals(url, "https://mt-client-api-v1.project-stock.agiliumlabs.cloud");
  };
  
  /**
   * Tests {@link MetaApiWebsocketClient#getServerUrl}
   */
  @Test
  void testConnectsToSharedSelectedRegion() throws Exception {
    client.close();
    Mockito.when(httpClient.requestJson(Mockito.any(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<String[]>>() {
      public CompletableFuture<String[]> answer(InvocationOnMock invocation) throws Throwable {
        HttpRequestOptions opts = invocation.getArgument(0, HttpRequestOptions.class);
        if (opts.getUrl().equals("https://mt-provisioning-api-v1.project-stock.agiliumlabs.cloud/users/current/" +
          "regions")) {
          return CompletableFuture.completedFuture(new String[] {"canada", "us-west"});
        }
        return CompletableFuture.completedFuture(null);
      }
    });
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      region = "us-west";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 15000L;
      connectTimeout = 15000L;
      useSharedClientApi = true;
    }});
    String url = client.getServerUrl();
    assertEquals(url, "https://mt-client-api-v1.us-west.project-stock.agiliumlabs.cloud");
  };
  
  /**
   * Test {@link MetaApiWebsocketClient#getServerUrl}
   */
  @Test
  void testConnectsToDedicatedSelectedRegion() throws Exception {
    client.close();
    Mockito.when(httpClient.requestJson(Mockito.any(), Mockito.any()))
    .thenAnswer(new Answer<CompletableFuture<String[]>>() {
      public CompletableFuture<String[]> answer(InvocationOnMock invocation) throws Throwable {
        HttpRequestOptions opts = invocation.getArgument(0, HttpRequestOptions.class);
        if (opts.getUrl().equals("https://mt-provisioning-api-v1.project-stock.agiliumlabs.cloud/users/current/" +
          "regions")) {
          return CompletableFuture.completedFuture(new String[] {"canada", "us-west"});
        }
        return CompletableFuture.completedFuture(null);
      }
    });
    Mockito.when(httpClient.request(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture("{"
        + "\"url\": \"http://localhost:8081\","
        + "\"hostname\": \"mt-client-api-dedicated\","
        + "\"domain\": \"project-stock.agiliumlabs.cloud\""
      + "}"));
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      region = "us-west";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 15000L;
      connectTimeout = 15000L;
      useSharedClientApi = false;
    }});
    String url = client.getServerUrl();
    assertEquals(url, "https://mt-client-api-dedicated.us-west.project-stock.agiliumlabs.cloud");
  };

  /**
   * Tests {@link MetaApiWebsocketClient#getAccountInformation(String)}
   */
  @ParameterizedTest
  @MethodSource("provideAccountInformation")
  void testRetrievesMetaTraderAccountInformationFromApi(MetatraderAccountInformation expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getAccountInformation") 
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("RPC")
         ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("accountInformation", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderAccountInformation actual = client.getAccountInformation("accountId").get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getPositions(String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderPosition")
  void testRetrievesMetaTraderPositionsFromApi(MetatraderPosition position) throws Exception {
    List<MetatraderPosition> expected = Lists.list(position);
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getPositions") 
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("positions", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    List<MetatraderPosition> actual = client.getPositions("accountId").get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderPosition")
  void testRetrievesMetaTraderPositionFromApiById(MetatraderPosition expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (	request.get("type").asText().equals("getPosition") 
           && request.get("accountId").asText().equals("accountId")
           && request.get("positionId").asText().equals(expected.id)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("position", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderPosition actual = client.getPosition("accountId", expected.id).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getOrders(String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderOrder")
  void testRetrievesMetaTraderOrdersFromApi(MetatraderOrder order) throws Exception {
    List<MetatraderOrder> expected = Lists.list(order);
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getOrders")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("orders", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    List<MetatraderOrder> actual = client.getOrders("accountId").get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getOrder(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderOrder")
  void testRetrievesMetaTraderOrderFromApiById(MetatraderOrder expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getOrder")
           && request.get("accountId").asText().equals("accountId")
           && request.get("orderId").asText().equals(expected.id)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("order", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderOrder actual = client.getOrder("accountId", expected.id).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getHistoryOrdersByTicket(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderHistoryOrders")
  void testRetrievesMetaTraderHistoryOrdersFromApiByTicket(MetatraderHistoryOrders expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getHistoryOrdersByTicket")
           && request.get("accountId").asText().equals("accountId")
           && request.get("ticket").asText().equals(expected.historyOrders.get(0).id)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("historyOrders", jsonMapper.valueToTree(expected.historyOrders));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderHistoryOrders actual = client
      .getHistoryOrdersByTicket("accountId", expected.historyOrders.get(0).id).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getHistoryOrdersByPosition(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderHistoryOrders")
  void testRetrievesMetaTraderHistoryOrdersFromApiByPosition(MetatraderHistoryOrders expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getHistoryOrdersByPosition")
           && request.get("accountId").asText().equals("accountId")
           && request.get("positionId").asText().equals(expected.historyOrders.get(0).positionId)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("historyOrders", jsonMapper.valueToTree(expected.historyOrders));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderHistoryOrders actual = client
      .getHistoryOrdersByPosition("accountId", expected.historyOrders.get(0).positionId).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getHistoryOrdersByTimeRange(String, IsoTime, IsoTime, int, int)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderHistoryOrders")
  void testRetrievesMetaTraderHistoryOrdersFromApiByTimeRange(MetatraderHistoryOrders expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getHistoryOrdersByTimeRange")
           && request.get("accountId").asText().equals("accountId")
           && request.get("startTime").asText().equals("2020-04-15T02:45:00Z")
           && request.get("endTime").asText().equals("2020-04-15T02:46:00Z")
           && request.get("offset").asInt() == 1 && request.get("limit").asInt() == 100
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("historyOrders", jsonMapper.valueToTree(expected.historyOrders));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderHistoryOrders actual = client.getHistoryOrdersByTimeRange(
      "accountId", new IsoTime("2020-04-15T02:45:00.000Z"),
      new IsoTime("2020-04-15T02:46:00.000Z"), 1, 100
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getDealsByTicket(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderDeals")
  void testRetrievesMetaTraderDealsFromApiByTicket(MetatraderDeals expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
      	JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getDealsByTicket")
           && request.get("accountId").asText().equals("accountId")
           && request.get("ticket").asText().equals(expected.deals.get(0).orderId)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("deals", jsonMapper.valueToTree(expected.deals));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderDeals actual = client.getDealsByTicket("accountId", expected.deals.get(0).orderId).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getDealsByPosition(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderDeals")
  void testRetrievesMetaTraderDealsFromApiByPosition(MetatraderDeals expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getDealsByPosition")
           && request.get("accountId").asText().equals("accountId")
           && request.get("positionId").asText().equals(expected.deals.get(0).positionId)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("deals", jsonMapper.valueToTree(expected.deals));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderDeals actual = client.getDealsByPosition("accountId", expected.deals.get(0).positionId).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getDealsByTimeRange(String, IsoTime, IsoTime, int, int)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderDeals")
  void testRetrievesMetaTraderDealsFromApiByTimeRange(MetatraderDeals expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getDealsByTimeRange")
           && request.get("accountId").asText().equals("accountId")
           && request.get("startTime").asText().equals("2020-04-15T02:45:00Z")
           && request.get("endTime").asText().equals("2020-04-15T02:46:00Z")
           && request.get("offset").asInt() == 1 && request.get("limit").asInt() == 100
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("deals", jsonMapper.valueToTree(expected.deals));
          response.put("synchronizing", expected.synchronizing);
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderDeals actual = client.getDealsByTimeRange(
      "accountId", new IsoTime("2020-04-15T02:45:00.000Z"),
      new IsoTime("2020-04-15T02:46:00.000Z"), 1, 100
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#removeHistory(String)}
   */
  @Test
  void testRemovesHistoryFromApi() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("removeHistory")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("app")
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.removeHistory("accountId", "app").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#removeApplication(String)}
   */
  @Test
  void testRemovesApplicationFromApi() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("removeApplication")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.removeApplication("accountId").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
   */
  @Test
  void testExecutesATradeViaApi() throws Exception {
    actualTrade = null;
    MetatraderTrade expectedTrade = new MetatraderTrade();
    expectedTrade.actionType = ActionType.ORDER_TYPE_SELL;
    expectedTrade.symbol = "AUDNZD";
    expectedTrade.volume = 0.07;
    MetatraderTradeResponse expectedTradeResponse = new MetatraderTradeResponse();
    expectedTradeResponse.numericCode = 10009;
    expectedTradeResponse.stringCode = "TRADE_RETCODE_DONE";
    expectedTradeResponse.orderId = "46870472";
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("trade")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          actualTrade = jsonMapper.treeToValue(request.get("trade"), MetatraderTrade.class);
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("response", jsonMapper.valueToTree(expectedTradeResponse));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderTradeResponse actualTradeResponse = client.trade("accountId", expectedTrade).get();
    
    // Somehow this assert causes infinity hanging if it is located in the callback 
    assertThat(actualTrade).usingRecursiveComparison().isEqualTo(expectedTrade);
    
    assertThat(actualTradeResponse).usingRecursiveComparison().isEqualTo(expectedTradeResponse);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
   */
  @Test
  void testReceivesTradeErrorFromApi() throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL;
    trade.symbol = "AUDNZD";
    trade.volume = 0.07;
    MetatraderTradeResponse expectedTradeResponse = new MetatraderTradeResponse();
    expectedTradeResponse.numericCode = 10006;
    expectedTradeResponse.stringCode = "TRADE_RETCODE_REJECT";
    expectedTradeResponse.message = "Request rejected";
    expectedTradeResponse.orderId = "46870472";
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("trade")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("response", jsonMapper.valueToTree(expectedTradeResponse));
          client.sendEvent("response", response.toString());
        }
      }
    });
    try {
      client.trade("accountId", trade).get();
      throw new ExecutionException(new Exception("Trade error expected"));
    } catch (ExecutionException err) {
      assertTrue(err.getCause() instanceof TradeException);
      TradeException tradeException = (TradeException) err.getCause();
      assertEquals("Request rejected", tradeException.getMessage());
      assertEquals("TRADE_RETCODE_REJECT", tradeException.stringCode);
      assertEquals(10006, tradeException.numericCode);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#subscribe(String)}
   */
  @Test
  void testConnectsToMetatarderTerminal() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("subscribe")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.subscribe("accountId", 1).get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#subscribe(String)}
   */
  @Test
  void testCreatesNewInstanceWhenAccountLimitIsReached() throws Exception {
    assertEquals(1, client.getSocketInstances().size());
    for (int i = 0; i < 100; i++) {
      client.socketInstancesByAccounts.put("accountId" + i, 0);
    }

    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("subscribe")
           && request.get("accountId").asText().equals("accountId101")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.subscribe("accountId101", 1).join();
    Thread.sleep(200);
    assertEquals(2, client.getSocketInstances().size());
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#subscribe(String)}
   */
  @Test
  void testReturnsErrorIfConnectToMetatarderTerminalFailed() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("subscribe")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          requestReceived = true;
        }
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("id", 1);
        response.put("error", "NotAuthenticatedError");
        response.put("message", "Error message");
        response.set("requestId", request.get("requestId"));
        client.sendEvent("processingError", response.toString());
      }
    });
    boolean success = true;
    try {
      client.subscribe("accountId").get();
      success = false;
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof NotConnectedException);
    }
    assertTrue(success);
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#reconnect(String)}
   */
  @Test
  void testReconnectsToMetatraderTerminal() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("reconnect")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.reconnect("accountId").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getSymbols}
   */
  @Test
  void testRetrievesSymbolsFromApi() {
    List<String> symbols = Arrays.asList("EURUSD");
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getSymbols")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("symbols", jsonMapper.valueToTree(symbols));
          client.sendEvent("response", response.toString());
        }
      }
    });
    List<String> actual = client.getSymbols("accountId").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(symbols);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getSymbolSpecification(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolSpecification")
  void testRetrievesSymbolSpecificationFromApi(MetatraderSymbolSpecification expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getSymbolSpecification")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals(expected.symbol)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("specification", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderSymbolSpecification actual = client.getSymbolSpecification("accountId", expected.symbol).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getSymbolPrice(String, String)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolPrice")
  void testRetrievesSymbolPriceFromApi(MetatraderSymbolPrice expected) throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getSymbolPrice")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals(expected.symbol)
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("price", jsonMapper.valueToTree(expected));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderSymbolPrice actual = client.getSymbolPrice("accountId", expected.symbol).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getCandle(String, String, String)}
   */
  @Test
  void testRetrievesCandleFromApi() throws Exception {
    MetatraderCandle candle = new MetatraderCandle() {{
      symbol = "AUDNZD";
      timeframe = "15m";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      open = 1.03297;
      high = 1.06309;
      low = 1.02705;
      close = 1.043;
      tickVolume = 1435;
      spread = 17;
      volume = 345;
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getCandle")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("AUDNZD")
           && request.get("application").asText().equals("RPC")
           && request.get("timeframe").asText().equals("15m")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("candle", jsonMapper.valueToTree(candle));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderCandle actual = client.getCandle("accountId", "AUDNZD", "15m").get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(candle);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getTick(String, String)}
   */
  @Test
  void testRetrievesLatestTickFromApi() {
    MetatraderTick tick = new MetatraderTick() {{
      symbol = "AUDNZD";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      bid = 1.05297;
      ask = 1.05309;
      last = 0.5298;
      volume = 0.13;
      side = "buy";
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getTick")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("AUDNZD")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("tick", jsonMapper.valueToTree(tick));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderTick actual = client.getTick("accountId", "AUDNZD").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(tick);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getBook(String, String)B}
   */
  @Test
  void testRetrievesLatestOrderBookFromApi() {
    MetatraderBook book = new MetatraderBook() {{
      symbol = "AUDNZD";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      book = Arrays.asList(new MetatraderBookEntry() {{
        type = MetatraderBookEntry.BookType.BOOK_TYPE_SELL;
        price = 1.05309;
        volume = 5.67;
      }}, new MetatraderBookEntry() {{
        type = MetatraderBookEntry.BookType.BOOK_TYPE_BUY;
        price = 1.05297;
        volume = 3.45;
      }});
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getBook")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("AUDNZD")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("book", jsonMapper.valueToTree(book));
          client.sendEvent("response", response.toString());
        }
      }
    });
    MetatraderBook actual = client.getBook("accountId", "AUDNZD").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(book);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#saveUptime(String, Map)}
   */
  @Test
  void testSendsUptimeStatsToTheServer() {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("saveUptime")
           && request.get("accountId").asText().equals("accountId")
           && request.get("uptime").get("1h").asDouble() == 100
           && request.get("application").asText().equals("application")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    Map<String, Double> uptime = Maps.newHashMap("1h", 100.0);
    client.saveUptime("accountId", uptime).join();
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#unsubscribe(String)}
   */
  @Test
  void testUnsubscribesFromAccountData() {
    requestReceived = false;
    ObjectNode response = jsonMapper.createObjectNode();
    response.put("type", "response");
    response.put("accountId", "accountId");
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("unsubscribe")
           && request.get("accountId").asText().equals("accountId")
        ) {
          requestReceived = true;
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.unsubscribe("accountId").join();
    assertTrue(requestReceived);
    assertFalse(client.socketInstancesByAccounts.containsKey("accountId"));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#unsubscribe(String)}
   */
  @Test
  void testIgnoresNotFoundExceptionOnUnsubscribe() {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("id", 1);
        response.put("error", "ValidationError");
        response.put("message", "Validation failed");
        ObjectNode details = jsonMapper.createObjectNode();
        details.put("parameter", "volume");
        details.put("message", "Required value.");
        response.set("details", jsonMapper.valueToTree(Arrays.asList(details)));
        response.set("requestId", request.get("requestId"));
        client.sendEvent("processingError", response.toString());
      }
    });
    try {
      client.unsubscribe("accountId").join();
      throw new Exception("ValidationException extected");
    } catch (Throwable err) {
      assertTrue(err.getCause().getCause() instanceof ValidationException);
      Map<String, String> details = new ConcurrentHashMap<>();
      details.put("parameter", "volume");
      details.put("message", "Required value.");
      assertThat(((ValidationException) err.getCause().getCause()).details)
        .usingRecursiveComparison().isEqualTo(Arrays.asList(details));
    }
    io.removeAllListeners("request");
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("id", 1);
        response.put("error", "NotFoundError");
        response.put("message", "Account not found");
        response.set("requestId", request.get("requestId"));
        client.sendEvent("processingError", response.toString());
      }
    });
    client.unsubscribe("accountId").join();
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
   */
  @Test
  void testHandlesValidationErrorWithObjectDetails() throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL;
    trade.symbol = "AUDNZD";
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode error = jsonMapper.createObjectNode();
        error.put("id", 1);
        error.put("error", "ValidationError");
        error.put("message", "Validation failed");
        error.put("requestId", request.get("requestId").asText());
        ArrayNode details = jsonMapper.createArrayNode();
        ObjectNode detail = jsonMapper.createObjectNode();
        detail.put("parameter", "volume");
        detail.put("message", "Required value");
        details.add(detail);
        error.set("details", details);
        client.sendEvent("processingError", error.toString());
      }
    });
    try {
      client.trade("accountId", trade).get();
      throw new Exception("ValidationError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof ValidationException);
      ValidationException validationError = (ValidationException) error.getCause();
      List<Map<String, String>> expectedDetails = new ArrayList<>();
      Map<String, String> expectedDetail = new ConcurrentHashMap<>();
      expectedDetail.put("parameter", "volume");
      expectedDetail.put("message", "Required value");
      expectedDetails.add(expectedDetail);
      assertThat(validationError.details).usingRecursiveComparison().isEqualTo(expectedDetails);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
   */
  @Test
  void testHandlesValidationErrorWithStringDetails() throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL;
    trade.symbol = "AUDNZD";
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode error = jsonMapper.createObjectNode();
        error.put("id", 1);
        error.put("error", "ValidationError");
        error.put("message", "Validation failed");
        error.put("requestId", request.get("requestId").asText());
        error.put("details", "E_AUTH");
        client.sendEvent("processingError", error.toString());
      }
    });
    try {
      client.trade("accountId", trade).get();
      throw new Exception("ValidationError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof ValidationException);
      ValidationException validationError = (ValidationException) error.getCause();
      List<Map<String, String>> expectedDetails = new ArrayList<>();
      Map<String, String> expectedDetail = new ConcurrentHashMap<>();
      expectedDetail.put("parameter", "volume");
      expectedDetail.put("message", "Required value");
      expectedDetails.add(expectedDetail);
      assertEquals("E_AUTH", validationError.details);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
   */
  @Test
  void testHandlesNotFoundError() throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        WebsocketError error = new WebsocketError();
        error.id = 1;
        error.error = "NotFoundError";
        error.message = "Position id 1234 not found";
        error.requestId = request.get("requestId").asText();
        client.sendEvent("processingError", jsonMapper.writeValueAsString(error));
      }
    });
    try {
      client.getPosition("accountId", "1234").get();
      throw new Exception("NotFoundError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof NotFoundException);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
   */
  @Test
  void testHandlesNotSynchronizedError() throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        WebsocketError error = new WebsocketError();
        error.id = 1;
        error.error = "NotSynchronizedError";
        error.message = "Error message";
        error.requestId = request.get("requestId").asText();
        client.sendEvent("processingError", jsonMapper.writeValueAsString(error));
      }
    });
    try {
      client.getPosition("accountId", "1234").get();
      throw new Exception("NotSynchronizedError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof NotSynchronizedException);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
   */
  @Test
  void testHandlesNotConnectedError() throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        WebsocketError error = new WebsocketError();
        error.id = 1;
        error.error = "NotAuthenticatedError";
        error.message = "Error message";
        error.requestId = request.get("requestId").asText();
        client.sendEvent("processingError", jsonMapper.writeValueAsString(error));
      }
    });
    try {
      client.getPosition("accountId", "1234").get();
      throw new Exception("NotConnectedError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof NotConnectedException);
    }
  }
 
  /**
   * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
   */
  @Test
  void testHandlesOtherErrors() throws Exception {
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        WebsocketError error = new WebsocketError();
        error.id = 1;
        error.error = "Error";
        error.message = "Error message";
        error.requestId = request.get("requestId").asText();
        client.sendEvent("processingError", jsonMapper.writeValueAsString(error));
      }
    });
    try {
      client.getPosition("accountId", "1234").get();
      throw new Exception("InternalError expected");
    } catch (ExecutionException error) {
      assertTrue(error.getCause() instanceof InternalException);
    }
  }
   
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesAuthenticatedSynchronizationEvent() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "authenticated");
    packet.put("accountId", "accountId");
    packet.put("host", "ps-mpa-1");
    packet.put("instanceIndex", 1);
    packet.put("replicas", 2);
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onConnected("1:ps-mpa-1", 2);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesAuthenticatedSynchronizationEventWithSessionId() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet1 = jsonMapper.createObjectNode();
    packet1.put("type", "authenticated");
    packet1.put("accountId", "accountId");
    packet1.put("host", "ps-mpa-1");
    packet1.put("instanceIndex", 2);
    packet1.put("replicas", 4);
    packet1.put("sessionId", "wrong");
    server.sendEvent("synchronization", packet1.toString());
    ObjectNode packet2 = jsonMapper.createObjectNode();
    packet2.put("type", "authenticated");
    packet2.put("accountId", "accountId");
    packet2.put("host", "ps-mpa-1");
    packet2.put("instanceIndex", 1);
    packet2.put("replicas", 2);
    packet2.put("sessionId", client.socketInstances.get(0).sessionId);
    server.sendEvent("synchronization", packet2.toString());
    Thread.sleep(200);
    Mockito.verify(listener, Mockito.times(1)).onConnected("1:ps-mpa-1", 2);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesBrokerConnectionStatusEvent() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
    Mockito.when(listener.onConnected(Mockito.anyString(), Mockito.anyInt()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          connectedFuture.complete(null);
          return connectedFuture;
        }
      });
    CompletableFuture<Void> statusChangedFuture = new CompletableFuture<>();
    Mockito.when(listener.onBrokerConnectionStatusChanged(Mockito.anyString(), Mockito.anyBoolean()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          statusChangedFuture.complete(null);
          return statusChangedFuture;
        }
      });
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("connected", true);
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    authPacket.put("replicas", 1);
    server.sendEvent("synchronization", authPacket.toString());
    connectedFuture.join();
    ObjectNode statusPacket = jsonMapper.createObjectNode();
    statusPacket.put("type", "status");
    statusPacket.put("accountId", "accountId");
    statusPacket.put("connected", true);
    statusPacket.put("host", "ps-mpa-1");
    statusPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", statusPacket.toString());
    statusChangedFuture.join();
    Thread.sleep(200);
    Mockito.verify(listener).onBrokerConnectionStatusChanged("1:ps-mpa-1", true);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testCallsOnDisconnectIfThereWasNoSignalForALongTime() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onDisconnected(Mockito.anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetaApiWebsocketClient.resetDisconnectTimerTimeout = 10000;
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    authPacket.put("replicas", 2);
    server.sendEvent("synchronization", authPacket.toString());
    ObjectNode statusPacket = jsonMapper.createObjectNode();
    statusPacket.put("type", "status");
    statusPacket.put("accountId", "accountId");
    statusPacket.put("host", "ps-mpa-1");
    statusPacket.put("connected", true);
    statusPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", statusPacket.toString());
    Thread.sleep(2000);
    server.sendEvent("synchronization", statusPacket.toString());
    Thread.sleep(5000);
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    server.sendEvent("synchronization", authPacket.toString());
    Thread.sleep(2000);
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    Thread.sleep(10000);
    Mockito.verify(listener).onDisconnected("1:ps-mpa-1");
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener}
   */
  @Test
  void testClosesStreamOnTimeoutIfAnotherStreamExists() throws Exception {
    MetaApiWebsocketClient.resetDisconnectTimerTimeout = 15000;
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.doNothing().when(clientSubscriptionManager).onTimeout(Mockito.anyString(), Mockito.anyInt());
    Mockito.when(listener.onStreamClosed(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(clientSubscriptionManager)
      .onDisconnected(Mockito.anyString(), Mockito.anyInt());
    client.addSynchronizationListener("accountId", listener);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "authenticated",
      "accountId", "accountId", "host", "ps-mpa-1", "instanceIndex", 1, "replicas", 2)));
    Thread.sleep(50);
    Thread.sleep(3750);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "authenticated",
      "accountId", "accountId", "host", "ps-mpa-2", "instanceIndex", 1, "replicas", 2)));
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-1", "connected", true, "instanceIndex", 1)));
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-2", "connected", true, "instanceIndex", 1)));
    Thread.sleep(50);
    Thread.sleep(3750);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-1", "connected", true, "instanceIndex", 1)));
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-2", "connected", true, "instanceIndex", 1)));
    Thread.sleep(50);
    Thread.sleep(13750);
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-1", "connected", true, "instanceIndex", 1)));
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-2", "connected", true, "instanceIndex", 1)));
    Thread.sleep(50);
    Thread.sleep(3750);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "status",
      "accountId", "accountId", "host", "ps-mpa-2", "connected", true, "instanceIndex", 1)));
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    Thread.sleep(50);
    Thread.sleep(13750);
    Mockito.verify(listener).onStreamClosed("1:ps-mpa-1");
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    Mockito.verify(clientSubscriptionManager, Mockito.never()).onTimeout(Mockito.anyString(), Mockito.anyInt());
    Thread.sleep(50);
    Thread.sleep(3750);
    Mockito.verify(listener).onDisconnected("1:ps-mpa-2");
    Mockito.verify(clientSubscriptionManager, Mockito.never()).onDisconnected(Mockito.anyString(), Mockito.anyInt());
    Mockito.verify(clientSubscriptionManager).onTimeout("accountId", 1);
  };
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesServerSideHealthStatusEvent() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onConnected(Mockito.anyString(), Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onBrokerConnectionStatusChanged(Mockito.anyString(), Mockito.anyBoolean()))
      .thenReturn(CompletableFuture.completedFuture(null));
    CompletableFuture<Void> onHealthStatusFuture = new CompletableFuture<>();
    Mockito.when(listener.onHealthStatus(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) {
          onHealthStatusFuture.complete(null);
          return onHealthStatusFuture;
        }
      });
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("connected", true);
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", authPacket.toString());
    Thread.sleep(200);
    ObjectNode statusPacket = jsonMapper.createObjectNode();
    statusPacket.put("type", "status");
    statusPacket.put("accountId", "accountId");
    statusPacket.put("connected", true);
    HealthStatus healthStatus = new HealthStatus() {{ restApiHealthy = true; }};
    statusPacket.set("healthStatus", jsonMapper.valueToTree(healthStatus));
    statusPacket.put("host", "ps-mpa-1");
    statusPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", statusPacket.toString());
    Thread.sleep(200);
    onHealthStatusFuture.join();
    Mockito.verify(listener).onHealthStatus(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(healthStatus);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesDisconnectedSynchronizationEvent() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("connected", true);
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", authPacket.toString());
    Thread.sleep(400);
    ObjectNode disconnectPacket = jsonMapper.createObjectNode();
    disconnectPacket.put("type", "disconnected");
    disconnectPacket.put("accountId", "accountId");
    disconnectPacket.put("host", "ps-mpa-1");
    disconnectPacket.put("instanceIndex", 1);
    server.sendEvent("synchronization", disconnectPacket.toString());
    Thread.sleep(400);
    Mockito.verify(listener).onDisconnected("1:ps-mpa-1");
  }
  
  @Test
  void testClosesTheStreamIfHostNameDisconnectedAndAnotherStreamExists() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onConnected(Mockito.anyString(), Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onStreamClosed(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(clientSubscriptionManager)
      .onDisconnected(Mockito.anyString(), Mockito.anyInt());
    client.addSynchronizationListener("accountId", listener);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "authenticated",
      "accountId", "accountId", "host", "ps-mpa-1", "instanceIndex", 1, "replicas", 2)));
    Thread.sleep(200);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "authenticated",
      "accountId", "accountId", "host", "ps-mpa-2", "instanceIndex", 1, "replicas", 2)));
    Thread.sleep(200);
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-1", "instanceIndex", 1)));
    Thread.sleep(200);
    Mockito.verify(listener).onStreamClosed("1:ps-mpa-1");
    Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyString());
    Mockito.verify(clientSubscriptionManager, Mockito.never()).onDisconnected(Mockito.anyString(), Mockito.anyInt());
    server.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-2", "instanceIndex", 1)));
    Thread.sleep(200);
    Mockito.verify(listener).onDisconnected(Mockito.anyString());
    Mockito.verify(clientSubscriptionManager).onDisconnected("accountId", 1);
  };
  
  @Test
  void testOnlyAcceptsPacketsWithOwnSynchronizationIds() throws InterruptedException {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onAccountInformationUpdated(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    client.addSynchronizationListener("accountId", listener);
    Mockito.when(client.socketInstances.get(0).synchronizationThrottler.getActiveSynchronizationIds())
      .thenReturn(Lists.list("synchronizationId"));
    CompletableFuture<Void> onAccountInformationUpdatedFuture = new CompletableFuture<>();
    Mockito.when(listener.onAccountInformationUpdated(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
      @Override
      public CompletableFuture<Void> answer(InvocationOnMock invocation) {
        onAccountInformationUpdatedFuture.complete(null);
        return onAccountInformationUpdatedFuture;
      }
    });
    Mockito.when(listener.onDisconnected(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    ObjectNode packet1 = jsonMapper.createObjectNode();
    packet1.put("type", "accountInformation");
    packet1.put("accountId", "accountId");
    packet1.set("accountInformation", jsonMapper.createObjectNode());
    packet1.put("instanceIndex", 1);
    server.sendEvent("synchronization", packet1.toString());
    onAccountInformationUpdatedFuture.join();
    Mockito.verify(listener, Mockito.times(1)).onAccountInformationUpdated(Mockito.anyString(), Mockito.any());
    ObjectNode packet2 = jsonMapper.createObjectNode();
    packet2.put("type", "accountInformation");
    packet2.put("accountId", "accountId");
    packet2.set("accountInformation", jsonMapper.createObjectNode());
    packet2.put("instanceIndex", 1);
    packet2.put("synchronizationId", "wrong");
    server.sendEvent("synchronization", packet2.toString());
    Thread.sleep(200);
    Mockito.verify(listener, Mockito.times(1)).onAccountInformationUpdated(Mockito.anyString(), Mockito.any());
    CompletableFuture<Void> onAccountInformationUpdatedFuture2 = new CompletableFuture<>();
    Mockito.when(listener.onAccountInformationUpdated(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
      @Override
      public CompletableFuture<Void> answer(InvocationOnMock invocation) {
        onAccountInformationUpdatedFuture2.complete(null);
        return onAccountInformationUpdatedFuture2;
      }
    });
    ObjectNode packet3 = jsonMapper.createObjectNode();
    packet3.put("type", "accountInformation");
    packet3.put("accountId", "accountId");
    packet3.set("accountInformation", jsonMapper.createObjectNode());
    packet3.put("instanceIndex", 1);
    packet3.put("synchronizationId", "synchronizationId");
    server.sendEvent("synchronization", packet3.toString());
    onAccountInformationUpdatedFuture2.join();
    Mockito.verify(listener, Mockito.times(2)).onAccountInformationUpdated(Mockito.anyString(), Mockito.any());
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#synchronize(String, String, IsoTime, IsoTime)}
   */
  @Test
  void testSynchronizesWithMetatraderTerminal() throws Exception {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("synchronize")
           && request.get("accountId").asText().equals("accountId")
           && request.get("host").asText().equals("ps-mpa-1")
           && request.get("startingHistoryOrderTime").asText().equals("2020-01-01T00:00:00Z")
           && request.get("startingDealTime").asText().equals("2020-01-02T00:00:00Z")
           && request.get("requestId").asText().equals("synchronizationId")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.synchronize("accountId", 1, "ps-mpa-1", "synchronizationId", 
      new IsoTime("2020-01-01T00:00:00.000Z"),
      new IsoTime("2020-01-02T00:00:00.000Z")).get();
    assertTimeoutPreemptively(Duration.ofSeconds(7), () -> {
      while (!requestReceived) Thread.sleep(200);
      assertTrue(requestReceived);
    });
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @Test
  void testProcessesSynchronizationStartedEvent() throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "synchronizationStarted");
    packet.put("accountId", "accountId");
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onSynchronizationStarted("1:ps-mpa-1");
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideAccountInformation")
  void testSynchronizesAccountInformation(MetatraderAccountInformation expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "accountInformation");
    packet.put("accountId", "accountId");
    packet.put("host", "ps-mpa-1");
    packet.set("accountInformation", jsonMapper.valueToTree(expected));
    packet.put("instanceIndex", 1);
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onAccountInformationUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(expected);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderPosition")
  void testSynchronizesPositions(MetatraderPosition expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "positions");
    packet.put("accountId", "accountId");
    packet.set("positions", jsonMapper.valueToTree(Lists.list(expected)));
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onPositionsReplaced(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(Lists.list(expected));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderOrder")
  void testSynchronizesOrders(MetatraderOrder expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "orders");
    packet.put("accountId", "accountId");
    packet.set("orders", jsonMapper.valueToTree(Lists.list(expected)));
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onOrdersReplaced(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(Lists.list(expected));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderHistoryOrders")
  void testSynchronizesHistoryOrders(MetatraderHistoryOrders expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "historyOrders");
    packet.put("accountId", "accountId");
    packet.set("historyOrders", jsonMapper.valueToTree(expected.historyOrders));
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onHistoryOrderAdded(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(expected.historyOrders.get(0));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideMetatraderDeals")
  void testSynchronizesDeals(MetatraderDeals expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "deals");
    packet.put("accountId", "accountId");
    packet.set("deals", jsonMapper.valueToTree(expected.deals));
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onDealAdded(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(expected.deals.get(0));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideCombinedModelsForUpdateEvent")
  void testProcessesSynchronizationUpdates(
    MetatraderAccountInformation accountInformation, MetatraderPosition position,
    MetatraderOrder order, MetatraderHistoryOrders historyOrders, MetatraderDeals deals
  ) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    CompletableFuture<Void> onAccountInformationUpdatedFuture = new CompletableFuture<>();
    Mockito.when(listener.onAccountInformationUpdated(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) {
          onAccountInformationUpdatedFuture.complete(null);
          return onAccountInformationUpdatedFuture;
        }
      });
    CompletableFuture<Void> onPositionUpdatedFuture = new CompletableFuture<>();
    Mockito.when(listener.onPositionUpdated(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          onPositionUpdatedFuture.complete(null);
          return onPositionUpdatedFuture;
        }
      });
    CompletableFuture<Void> onPositionRemovedFuture = new CompletableFuture<>();
    Mockito.when(listener.onPositionRemoved(Mockito.anyString(), Mockito.anyString()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          onPositionRemovedFuture.complete(null);
          return onPositionRemovedFuture;
        }
      });
    Mockito.when(listener.onOrderUpdated(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    CompletableFuture<Void> onOrderCompletedFuture = new CompletableFuture<>();
    Mockito.when(listener.onOrderCompleted(Mockito.anyString(), Mockito.anyString()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          onOrderCompletedFuture.complete(null);
          return onOrderCompletedFuture;
        }
      });
    Mockito.when(listener.onHistoryOrderAdded(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    CompletableFuture<Void> onDealAddedFuture = new CompletableFuture<>();
    Mockito.when(listener.onDealAdded(Mockito.anyString(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
        @Override
        public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
          onDealAddedFuture.complete(null);
          return onDealAddedFuture;
        }
      });
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "update");
    packet.put("accountId", "accountId");
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    packet.set("accountInformation", jsonMapper.valueToTree(accountInformation));
    packet.set("updatedPositions", jsonMapper.valueToTree(Lists.list(position)));
    packet.set("removedPositionIds", jsonMapper.valueToTree(Lists.list("1234")));
    packet.set("updatedOrders", jsonMapper.valueToTree(Lists.list(order)));
    packet.set("completedOrderIds", jsonMapper.valueToTree(Lists.list("2345")));
    packet.set("historyOrders", jsonMapper.valueToTree(historyOrders.historyOrders));
    packet.set("deals", jsonMapper.valueToTree(deals.deals));
    server.sendEvent("synchronization", packet.toString());
    onAccountInformationUpdatedFuture.join();
    onPositionUpdatedFuture.join();
    onPositionRemovedFuture.join();
    onOrderCompletedFuture.join();
    onDealAddedFuture.join();
    Thread.sleep(200);
    Mockito.verify(listener).onAccountInformationUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(accountInformation);
      return true;
    }));
    Mockito.verify(listener).onPositionUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(position);
      return true;
    }));
    Mockito.verify(listener).onPositionRemoved("1:ps-mpa-1", "1234");
    Mockito.verify(listener).onOrderUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(order);
      return true;
    }));
    Mockito.verify(listener).onOrderCompleted("1:ps-mpa-1", "2345");
    Mockito.verify(listener).onHistoryOrderAdded(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(historyOrders.historyOrders.get(0));
      return true;
    }));
    Mockito.verify(listener).onDealAdded(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(deals.deals.get(0));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testRetriesRequestOnFailue() {
    requestCounter = 0;
    MetatraderOrder order = new MetatraderOrder();
    order.id = "46871284";
    order.type = OrderType.ORDER_TYPE_BUY_LIMIT;
    order.state = OrderState.ORDER_STATE_PLACED;
    order.symbol = "AUDNZD";
    order.magic = 123456;
    order.platform = "mt5";
    order.time = new IsoTime("2020-04-20T08:38:58.270Z");
    order.openPrice = 1.03;
    order.currentPrice = 1.05206;
    order.volume = 0.01;
    order.currentVolume = 0.01;
    order.comment = "COMMENT2";
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (requestCounter > 1 && request.get("type").asText().equals("getOrder")
           && request.get("accountId").asText().equals("accountId")
           && request.get("orderId").asText().equals("46871284")
           && request.get("application").asText().equals("RPC")) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("order", jsonMapper.valueToTree(order));
          client.sendEvent("response", response.toString());
        }
        requestCounter++;
      }
    });
    MetatraderOrder actual = client.getOrder("accountId", "46871284").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(order);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testWaitsSpecifiedAmountOfTimeOnTooManyRequestsError() {
    requestCounter = 0;
    MetatraderOrder order = new MetatraderOrder() {{
      id = "46871284";
      type = MetatraderOrder.OrderType.ORDER_TYPE_BUY_LIMIT;
      state = MetatraderOrder.OrderState.ORDER_STATE_PLACED;
      symbol = "AUDNZD";
      magic = 123456;
      platform = "mt5";
      time = new IsoTime("2020-04-20T08:38:58.270Z");
      openPrice = 1.03;
      currentPrice = 1.05206;
      volume = 0.01;
      currentVolume = 0.01;
      comment = "COMMENT2";
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode response = jsonMapper.createObjectNode();
        if (requestCounter > 0 && request.get("type").asText().equals("getOrder")
           && request.get("accountId").asText().equals("accountId")
           && request.get("orderId").asText().equals("46871284")
           && request.get("application").asText().equals("RPC")) {
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("order", jsonMapper.valueToTree(order));
          client.sendEvent("response", response.toString());
        } else {
          response.put("id", 1);
          response.put("error", "TooManyRequestsError");
          response.set("requestId", request.get("requestId"));
          response.put("message", "The API allows 10000 requests per 60 minutes to avoid overloading our servers.");
          response.put("status_code", 429);
          ObjectNode metadata = jsonMapper.createObjectNode();
          metadata.put("periodInMinutes", 60);
          metadata.put("maxRequestsForPeriod", 10000);
          metadata.put("recommendedRetryTime", new IsoTime(Date.from(Instant.now().plusMillis(1000))).toString());
          response.set("metadata", metadata);
          client.sendEvent("processingError", response.toString());
        }
        requestCounter++;
      }
    });
    long startTime = Date.from(Instant.now()).getTime();
    MetatraderOrder actual = client.getOrder("accountId", "46871284").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(order);
    long timeDiff = Date.from(Instant.now()).getTime() - startTime;
    assertTrue((timeDiff >= 1000) && (timeDiff <= 2000));
  };
 
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testReturnsTooManyRequestsExceptionIfRecommendedTimeIsBeyondMaxRequestTime() {
    requestCounter = 0;
    MetatraderOrder order = new MetatraderOrder() {{
      id = "46871284";
      type = MetatraderOrder.OrderType.ORDER_TYPE_BUY_LIMIT;
      state = MetatraderOrder.OrderState.ORDER_STATE_PLACED;
      symbol = "AUDNZD";
      magic = 123456;
      platform = "mt5";
      time = new IsoTime("2020-04-20T08:38:58.270Z");
      openPrice = 1.03;
      currentPrice = 1.05206;
      volume = 0.01;
      currentVolume = 0.01;
      comment = "COMMENT2";
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        ObjectNode response = jsonMapper.createObjectNode();
        if (requestCounter > 0 && request.get("type").asText().equals("getOrder")
           && request.get("accountId").asText().equals("accountId")
           && request.get("orderId").asText().equals("46871284")
           && request.get("application").asText().equals("RPC")) {
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("order", jsonMapper.valueToTree(order));
          client.sendEvent("response", response.toString());
        } else {
          response.put("id", 1);
          response.put("error", "TooManyRequestsError");
          response.set("requestId", request.get("requestId"));
          response.put("message", "The API allows 10000 requests per 60 minutes to avoid overloading our servers.");
          response.put("status_code", 429);
          ObjectNode metadata = jsonMapper.createObjectNode();
          metadata.put("periodInMinutes", 60);
          metadata.put("maxRequestsForPeriod", 10000);
          metadata.put("recommendedRetryTime", new IsoTime(Date.from(Instant.now().plusMillis(60000))).toString());
          response.set("metadata", metadata);
          client.sendEvent("processingError", response.toString());
        }
        requestCounter++;
      }
    });
    try {
      client.getOrder("accountId", "46871284").join();
      throw new CompletionException(new Exception("TooManyRequestsError expected"));
    } catch (Throwable err) {
      assertTrue(err.getCause() instanceof TooManyRequestsException);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testDoesNotRetryRequestOnValidationError() {
    requestCounter = 0;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (requestCounter > 0 && request.get("type").asText().equals("subscribeToMarketData")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("EURUSD")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        } else {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("id", 1);
          response.put("error", "ValidationError");
          response.put("message", "Error message");
          response.set("requestId", request.get("requestId"));
          client.sendEvent("processingError", response.toString());
        }
        requestCounter++;
      }
    });
    try {
      client.subscribeToMarketData("accountId", 1, "EURUSD", new ArrayList<>()).join();
    } catch (CompletionException err) {
      assertTrue(err.getCause() instanceof ValidationException);
    }
    assertEquals(1, requestCounter);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testDoesNotRetryTradeRequestsOnFail() {
    requestCounter = 0;
    MetatraderTrade trade = new MetatraderTrade() {{
      actionType = ActionType.ORDER_TYPE_SELL;
      symbol = "AUDNZD";
      volume = 0.07;
    }};
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (request.get("type").asText().equals("trade")
          && request.get("accountId").asText().equals("accountId")
          && request.get("application").asText().equals("application")) {
          if (requestCounter > 0) {
            fail();
          }
          requestCounter++;
        }
      }
    });
    try {
      client.trade("accountId", trade).join();
      throw new CompletionException(new Exception("TimeoutError expected"));
    } catch (CompletionException err) {
      assertTrue(err.getCause() instanceof TimeoutException);
    }
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @RepeatedIfExceptionsTest(repeats = 3)
  void testDoesNotRetryRequestIfConnectionClosedBetweenRetries() throws Exception {
    requestCounter = 0;
    JsonNode response = jsonMapper.valueToTree(Js.asMap("type", "response", "accountId", "accountId"));
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (request.get("type").asText().equals("unsubscribe")
          && request.get("accountId").asText().equals("accountId")) {
          ObjectNode r = response.deepCopy();
          r.put("requestId", request.get("requestId").asText());
          client.sendEvent("response", r.toString());
        }
        if (request.get("type").asText().equals("getOrders")
          && request.get("accountId").asText().equals("accountId")
          && request.get("application").asText().equals("RPC")) {
          requestCounter++;
          client.sendEvent("processingError", Js.asJson("id", 1, "error", "NotSynchronizedError",
            "message", "Error message", "requestId", request.get("requestId").asText()).toString());
        }
      }
    });
    client.unsubscribe("accountId");
    try {
      client.getOrders("accountId").join();
      throw new CompletionException(new Exception("NotSynchronizedException expected"));
    } catch (Throwable err) {
      assertTrue(err.getCause() instanceof NotSynchronizedException);
    }
    assertEquals(1, requestCounter);
    assertFalse(client.socketInstancesByAccounts.containsKey("accountId"));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
   */
  @Test
  void testReturnsTimeoutExceptionIfNoServerResponseReceived() throws Exception {
    long defaultTimeout = (long) FieldUtils.readField(client, "requestTimeout", true);
    FieldUtils.writeField(client, "requestTimeout", 1, true);
    
    MetatraderTrade trade = new MetatraderTrade() {{
      actionType = ActionType.ORDER_TYPE_SELL;
      symbol = "AUDNZD";
      volume = 0.07;
    }};
    try {
      client.trade("accountId", trade).get();
      throw new Exception("TimeoutException expected");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof TimeoutException);
    }
    
    FieldUtils.writeField(client, "requestTimeout", defaultTimeout, true);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#subscribeToMarketData(String, String)}
   */
  @Test
  void testSubscribesToMarketDataWithMetatraderTerminal() {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("subscribeToMarketData")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("EURUSD")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
           && request.get("subscriptions").equals(jsonMapper.valueToTree(
             Arrays.asList( new MarketDataSubscription() {{ type = "quotes"; }})
           ))
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.subscribeToMarketData("accountId", 1, "EURUSD", Arrays.asList(
      new MarketDataSubscription() {{
      type = "quotes";
    }})).join();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#unsubscribeFromMarketData(String, String)}
   */
  @Test
  void testUnubscribesFromMarketDataWithMetatraderTerminal() {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("unsubscribeFromMarketData")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("EURUSD")
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
           && request.get("subscriptions").equals(jsonMapper.valueToTree(
             Arrays.asList( new MarketDataUnsubscription() {{ type = "quotes"; }})
           ))
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.unsubscribeFromMarketData("accountId", 1, "EURUSD", Arrays.asList(
      new MarketDataUnsubscription() {{ type = "quotes"; }})).join();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolSpecification")
  void testSynchronizesSymbolSpecifications(MetatraderSymbolSpecification expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onSymbolSpecificationsUpdated(Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onSymbolSpecificationUpdated(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onSymbolSpecificationRemoved(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "specifications");
    packet.put("accountId", "accountId");
    packet.set("specifications", jsonMapper.valueToTree(Lists.list(expected)));
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    packet.set("removedSymbols", jsonMapper.valueToTree(Lists.list("AUDNZD")));
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onSymbolSpecificationsUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(Arrays.asList(expected));
      return true;
    }), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(Arrays.asList("AUDNZD"));
      return true;
    }));
    Mockito.verify(listener).onSymbolSpecificationRemoved(Mockito.eq("1:ps-mpa-1"), Mockito.eq("AUDNZD"));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolPrice")
  void testSynchronizesSymbolPrices(MetatraderSymbolPrice price) throws Exception {
    List<MetatraderSymbolPrice> prices = Arrays.asList(price);
    List<MetatraderTick> ticks = Arrays.asList(new MetatraderTick() {{
      symbol = "AUDNZD";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      bid = 1.05297;
      ask = 1.05309;
      last = 0.5298;
      volume = 0.13;
      side = "buy";
    }});
    List<MetatraderCandle> candles = Arrays.asList(new MetatraderCandle() {{
      symbol = "AUDNZD";
      timeframe = "15m";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      open = 1.03297;
      high = 1.06309;
      low = 1.02705;
      close = 1.043;
      tickVolume = 1435;
      spread = 17;
      volume = 345;
    }});
    List<MetatraderBook> books = Arrays.asList(new MetatraderBook() {{
      symbol = "AUDNZD";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      book = Arrays.asList(
        new MetatraderBookEntry() {{
          type = MetatraderBookEntry.BookType.BOOK_TYPE_SELL;
          price = 1.05309;
          volume = 5.67;
        }},
        new MetatraderBookEntry() {{
          type = MetatraderBookEntry.BookType.BOOK_TYPE_SELL;
          price = 1.05297;
          volume = 3.45;
        }}
      );
    }});
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onSymbolPriceUpdated(Mockito.anyString(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onSymbolPricesUpdated(Mockito.anyString(), Mockito.anyList(), Mockito.anyDouble(),
      Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onCandlesUpdated(Mockito.anyString(), Mockito.anyList(),
      Mockito.anyDouble(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onTicksUpdated(Mockito.anyString(), Mockito.anyList(),
      Mockito.anyDouble(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onBooksUpdated(Mockito.anyString(), Mockito.anyList(),
      Mockito.anyDouble(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "prices");
    packet.put("accountId", "accountId");
    packet.put("host", "ps-mpa-1");
    packet.set("prices", jsonMapper.valueToTree(prices));
    packet.set("ticks", jsonMapper.valueToTree(ticks));
    packet.set("candles", jsonMapper.valueToTree(candles));
    packet.set("books", jsonMapper.valueToTree(books));
    packet.put("equity", 100);
    packet.put("margin", 200);
    packet.put("freeMargin", 400);
    packet.put("marginLevel", 40000);
    packet.put("instanceIndex", 1);
    packet.put("host", "ps-mpa-1");
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(50);
    Mockito.verify(listener).onSymbolPricesUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(prices);
      return true;
    }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0), Mockito.any());
    Mockito.verify(listener).onCandlesUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(candles);
      return true;
    }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0), Mockito.any());
    Mockito.verify(listener).onTicksUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(ticks);
      return true;
    }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0), Mockito.any());
    Mockito.verify(listener).onBooksUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(books);
      return true;
    }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0), Mockito.any());
    Mockito.verify(listener).onSymbolPriceUpdated(Mockito.eq("1:ps-mpa-1"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(prices.get(0));
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#waitSynchronized(String, String, Long)}
   */
  @Test
  void testWaitsForServerSideTerminalStateSynchronization() {
    requestReceived = false;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("waitSynchronized")
           && request.get("accountId").asText().equals("accountId")
           && request.get("applicationPattern").asText().equals("app.*")
           && request.get("timeoutInSeconds").asLong() == 10
           && request.get("application").asText().equals("application")
           && request.get("instanceIndex").asInt() == 1
        ) {
          requestReceived = true;
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.waitSynchronized("accountId", 1, "app.*", 10L).join();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link LatencyListener#onResponse(String, String, ResponseTimestamps)}
   */
  @Test
  void testInvokesLatencyListenerOnResponse() throws InterruptedException {
    class TestLatencyListener extends LatencyListener {
      public String accountId;
      public String requestType;
      public ResponseTimestamps actualTimestamps;
      
      @Override
      public CompletableFuture<Void> onResponse(String aid, String type, ResponseTimestamps ts) {
        accountId = aid;
        requestType = type;
        actualTimestamps = ts;
        return CompletableFuture.completedFuture(null);
      }
    };
    TestLatencyListener listener = new TestLatencyListener();
    client.addLatencyListener(listener);
    timestamps = null;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (timestamps == null) {
          timestamps = jsonMapper.treeToValue(request.get("timestamps"), ResponseTimestamps.class);
        }
        if (  request.get("type").asText().equals("getSymbolPrice")
           && request.get("accountId").asText().equals("accountId")
           && request.get("symbol").asText().equals("AUDNZD")
           && request.get("application").asText().equals("RPC")
           && timestamps.clientProcessingStarted != null
        ) {
          timestamps.serverProcessingStarted = new IsoTime();
          timestamps.serverProcessingFinished = new IsoTime();
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("price", jsonMapper.createObjectNode());
          response.set("timestamps", jsonMapper.valueToTree(timestamps));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.getSymbolPrice("accountId", "AUDNZD").join();
    Thread.sleep(2000);
    assertEquals("accountId", listener.accountId);
    assertEquals("getSymbolPrice", listener.requestType);
    assertNotNull(listener.actualTimestamps.clientProcessingStarted);
    assertNotNull(listener.actualTimestamps.clientProcessingFinished);
    assertNotNull(listener.actualTimestamps.serverProcessingStarted);
    assertNotNull(listener.actualTimestamps.serverProcessingFinished);
    listener.actualTimestamps.clientProcessingFinished = null;
    assertThat(listener.actualTimestamps).usingRecursiveComparison().isEqualTo(timestamps);
  }
  
  /**
   * Tests {@link LatencyListener#onSymbolPrice(String, String, SymbolPriceTimestamps)}
   */
  @Test
  void testMeasuresPriceStreamingLatencies() throws InterruptedException {
    List<MetatraderSymbolPrice> prices = Arrays.asList(new MetatraderSymbolPrice() {{
      symbol = "AUDNZD";
      timestamps = new SymbolPriceTimestamps() {{
        eventGenerated = new IsoTime();
        serverProcessingStarted = new IsoTime();
        serverProcessingFinished = new IsoTime();
      }};
    }});
    class TestLatencyListener extends LatencyListener {
      public String accountId;
      public String symbol;
      public SymbolPriceTimestamps actualTimestamps;
      
      @Override
      public CompletableFuture<Void> onSymbolPrice(String aid, String sym, SymbolPriceTimestamps ts) {
        accountId = aid;
        symbol = sym;
        actualTimestamps = ts;
        return CompletableFuture.completedFuture(null);
      }
    }
    TestLatencyListener listener = new TestLatencyListener();
    client.addLatencyListener(listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "prices");
    packet.put("accountId", "accountId");
    packet.set("prices", jsonMapper.valueToTree(prices));
    packet.put("equity", 100);
    packet.put("margin", 200);
    packet.put("freeMargin", 400);
    packet.put("marginLevel", 40000);
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(2000);
    assertEquals("accountId", listener.accountId);
    assertEquals("AUDNZD", listener.symbol);
    assertNotNull(listener.actualTimestamps.clientProcessingFinished);
    listener.actualTimestamps.clientProcessingFinished = null;
    assertThat(listener.actualTimestamps).usingRecursiveComparison().isEqualTo(prices.get(0).timestamps);
  }
  
  /**
   * Tests {@link LatencyListener#onUpdate(String, UpdateTimestamps)}
   */
  @Test
  void testMeasuresUpdateLatencies() throws InterruptedException {
    UpdateTimestamps timestamps = new UpdateTimestamps() {{
      eventGenerated = new IsoTime(Date.from(Instant.now()));
      serverProcessingStarted = new IsoTime(Date.from(Instant.now()));
      serverProcessingFinished = new IsoTime(Date.from(Instant.now()));
    }};
    class TestLatencyListener extends LatencyListener {
      public String accountId;
      public UpdateTimestamps actualTimestamps;
      
      @Override
      public CompletableFuture<Void> onUpdate(String aid, UpdateTimestamps ts) {
        accountId = aid;
        actualTimestamps = ts;
        return CompletableFuture.completedFuture(null);
      }
    }
    TestLatencyListener listener = new TestLatencyListener();
    client.addLatencyListener(listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "update");
    packet.put("accountId", "accountId");
    packet.set("timestamps", jsonMapper.valueToTree(timestamps));
    server.sendEvent("synchronization", packet.toString());
    Thread.sleep(2000);
    assertEquals("accountId", listener.accountId);
    assertNotNull(listener.actualTimestamps.clientProcessingFinished);
    listener.actualTimestamps.clientProcessingFinished = null;
    assertThat(listener.actualTimestamps).usingRecursiveComparison().isEqualTo(timestamps);
  }
  
  /**
   * Tests {@link LatencyListener#onTrade(String, TradeTimestamps)}
   */
  @Test
  void testProcessesTradeLatency() throws InterruptedException {
    MetatraderTrade trade = new MetatraderTrade();
    MetatraderTradeResponse tradeResponse = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      message = "Request completed";
      orderId = "46870472";
    }};
    TradeTimestamps timestamps = new TradeTimestamps() {{
      clientProcessingStarted = new IsoTime();
      serverProcessingStarted = new IsoTime();
      serverProcessingFinished = new IsoTime();
      tradeExecuted = new IsoTime();
    }};
    class TestLatencyListener extends LatencyListener {
      public String accountId;
      public TradeTimestamps actualTimestamps;
      
      @Override
      public CompletableFuture<Void> onTrade(String aid, TradeTimestamps ts) {
        accountId = aid;
        actualTimestamps = ts;
        return CompletableFuture.completedFuture(null);
      }
    };
    TestLatencyListener listener = new TestLatencyListener();
    client.addLatencyListener(listener);
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("trade")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("response", jsonMapper.valueToTree(tradeResponse));
          response.set("timestamps", jsonMapper.valueToTree(timestamps));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.trade("accountId", trade).join();
    Thread.sleep(2000);
    assertEquals("accountId", listener.accountId);
    assertNotNull(listener.actualTimestamps.clientProcessingFinished);
    listener.actualTimestamps.clientProcessingFinished = null;
    assertThat(listener.actualTimestamps).usingRecursiveComparison().isEqualTo(timestamps);
  }
  
  @Test
  void testReconnectsToServerOnDisconnect() throws InterruptedException {
    MetatraderTrade trade = new MetatraderTrade() {{
      actionType = MetatraderTrade.ActionType.ORDER_TYPE_SELL;
      symbol = "AUDNZD";
      volume = 0.07;
    }};
    MetatraderTradeResponse response = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      message = "Request completed";
      orderId = "46870472";
    }};
    ReconnectListener listener = Mockito.mock(ReconnectListener.class);
    Mockito.when(listener.onReconnected()).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.doNothing().when(clientPacketOrderer).onReconnected(Mockito.anyList());
    Mockito.doNothing().when(clientSubscriptionManager).onReconnected(Mockito.anyInt(), Mockito.anyList());
    client.addReconnectListener(listener, "accountId");
    requestCounter = 0;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("trade")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          requestCounter++;
          ObjectNode res = jsonMapper.createObjectNode();
          res.put("type", "response");
          res.set("accountId", request.get("accountId"));
          res.set("requestId", request.get("requestId"));
          res.set("response", jsonMapper.valueToTree(response));
          client.sendEvent("response", jsonMapper.writeValueAsString(res));
        }
        client.disconnect();
      }
    });

    client.trade("accountId", trade);
    Thread.sleep(50);
    Thread.sleep(1500);
    Thread.sleep(50);
    Mockito.verify(listener).onReconnected();
    Mockito.verify(clientSubscriptionManager).onReconnected(0, Arrays.asList("accountId"));
    Mockito.verify(clientPacketOrderer).onReconnected(Arrays.asList("accountId"));

    client.trade("accountId", trade);
    Thread.sleep(50);
    Thread.sleep(1500);
    Thread.sleep(50);
    assertEquals(2, requestCounter);
  };
  
  /**
   * Tests {@link MetaApiWebsocketClient#rpcRequest}
   */
  @Test
  void testRemovesReconnectListener() throws InterruptedException {
    MetatraderTrade trade = new MetatraderTrade() {{
      actionType = ActionType.ORDER_TYPE_SELL;
      symbol = "AUDNZD";
      volume = 0.07;
    }};
    MetatraderTradeResponse tradeResponse = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      message = "Request completed";
      orderId = "46870472";
    }};
    ReconnectListener listener = Mockito.mock(ReconnectListener.class);
    client.addReconnectListener(listener, "accountId");
    Mockito.doNothing().when(clientSubscriptionManager).onReconnected(Mockito.anyInt(), Mockito.anyList());
    requestCounter = 0;
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        assertTrue(request.get("trade").equals(jsonMapper.valueToTree(trade)));
        requestCounter++;
        if (  request.get("type").asText().equals("trade")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("application")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("response", jsonMapper.valueToTree(tradeResponse));
          client.sendEvent("response", response.toString());
        }
        client.disconnect();
      }
    });
    client.trade("accountId", trade).join();
    Thread.sleep(50);
    Thread.sleep(1100);
    Thread.sleep(50);
    Mockito.verify(listener).onReconnected();
    client.removeReconnectListener(listener);

    client.trade("accountId", trade).join();
    Thread.sleep(50);
    Thread.sleep(1100);
    Thread.sleep(50);
    Mockito.verify(listener, Mockito.times(1)).onReconnected();
    assertEquals(2, requestCounter);
  };
  
  long ordersCallTime;
  long positionsCallTime;
  long disconnectedCallTime;
  long pricesCallTime;
  
  /**
   * Tests {@link MetaApiWebsocketClient#queuePacket}
   */
  @Test
  void testProcessesPacketsInOrder() throws Exception {
    MetaApiWebsocketClient.resetDisconnectTimerTimeout = 7500;
    ordersCallTime = 0;
    positionsCallTime = 0;
    disconnectedCallTime = 0;
    pricesCallTime = 0;
    SynchronizationListener listener = new SynchronizationListener() {
      @Override
      public CompletableFuture<Void> onDisconnected(String instanceIndex) {
        return CompletableFuture.runAsync(() -> {
          Js.sleep(625);
          disconnectedCallTime = Date.from(Instant.now()).getTime();
        });
      }
      @Override
      public CompletableFuture<Void> onOrdersReplaced(String instanceIndex, List<MetatraderOrder> orders) {
        return CompletableFuture.runAsync(() -> {
          Js.sleep(1250);
          ordersCallTime = Date.from(Instant.now()).getTime();
        });
      }
      @Override
      public CompletableFuture<Void> onPositionsReplaced(String instanceIndex, List<MetatraderPosition> positions) {
        return CompletableFuture.runAsync(() -> {
          Js.sleep(125);
          positionsCallTime = Date.from(Instant.now()).getTime();
        });
      }
      @Override
      public CompletableFuture<Void> onSymbolPricesUpdated(String instanceIndex, List<MetatraderSymbolPrice> prices,
        Double equity, Double margin, Double freeMargin, Double marginLevel, Double accountCurrencyExchangeRate) {
        return CompletableFuture.runAsync(() -> {
          Js.sleep(125);
          pricesCallTime = Date.from(Instant.now()).getTime();
        });
      }
    };
    client.close();
    server.disconnect();
    Mockito.when(httpClient.request(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture("{\"url\": \"http://localhost:6784\"}"));
    client = new MetaApiWebsocketClient(httpClient, "token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 6000L;
      connectTimeout = 6000L;
      useSharedClientApi = false;
      retryOpts = new RetryOptions() {{retries = 3; minDelayInSeconds = 1; maxDelayInSeconds = 3;}};
      eventProcessing = new MetaApiWebsocketClient.EventProcessingOptions() {{sequentialProcessing = true;}};
    }});
    spyClientPacketOrderer();
    io.addEventListener("request", Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        JsonNode request = jsonMapper.valueToTree(data);
        if (  request.get("type").asText().equals("getPositions")
           && request.get("accountId").asText().equals("accountId")
           && request.get("application").asText().equals("RPC")
        ) {
          ObjectNode response = jsonMapper.createObjectNode();
          response.put("type", "response");
          response.set("accountId", request.get("accountId"));
          response.set("requestId", request.get("requestId"));
          response.set("positions", jsonMapper.valueToTree(Arrays.asList()));
          client.sendEvent("response", response.toString());
        }
      }
    });
    client.getPositions("accountId").join();
    client.addSynchronizationListener("accountId", listener);
    Mockito.doAnswer(new Answer<List<JsonNode>>() {
      @Override
      public List<JsonNode> answer(InvocationOnMock invocation) throws Throwable {
        return Arrays.asList(invocation.getArgument(0, JsonNode.class));
      }
    }).when(clientPacketOrderer).restoreOrder(Mockito.any(JsonNode.class));
    server.sendEvent("synchronization", Js.asJson("type", "authenticated",  "accountId", "accountId", 
      "host", "ps-mpa-1", "instanceIndex", 1, "replicas", 2, "sequenceNumber", 1).toString());
    Thread.sleep(50);
    Thread.sleep(7375);
    server.sendEvent("synchronization", Js.asJson("type", "orders", "accountId", "accountId",
      "orders", Arrays.asList(), "instanceIndex", 1, "host", "ps-mpa-1", "sequenceNumber", 2).toString());
    
    ObjectNode pricesPacketItem = jsonMapper.createObjectNode();
    pricesPacketItem.put("symbol", "EURUSD");
    
    ArrayNode pricesPacket = jsonMapper.createArrayNode();
    pricesPacket.add(pricesPacketItem);
    
    ObjectNode synchronizationPacket = jsonMapper.createObjectNode();
    synchronizationPacket.put("type", "prices");
    synchronizationPacket.put("accountId", "accountId");
    synchronizationPacket.set("prices", pricesPacket);
    synchronizationPacket.put("instanceIndex", 1);
    synchronizationPacket.put("host", "ps-mpa-1");
    synchronizationPacket.put("equity", 100);
    synchronizationPacket.put("margin", 200);
    synchronizationPacket.put("freeMargin", 400);
    synchronizationPacket.put("marginLevel", 40000);
    
    server.sendEvent("synchronization", synchronizationPacket.toString());
    Thread.sleep(50);
    Thread.sleep(375);
    server.sendEvent("synchronization", Js.asJson("type", "positions", "accountId", "accountId",
      "positions", Arrays.asList(), "instanceIndex", 1, "host", "ps-mpa-1", "sequenceNumber", 3).toString());
    Thread.sleep(50);
    Thread.sleep(2500);
    Thread.sleep(50);
    assertNotEquals(0, pricesCallTime);
    assertTrue(ordersCallTime > pricesCallTime);
    assertTrue(disconnectedCallTime > ordersCallTime);
    assertTrue(positionsCallTime > disconnectedCallTime);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#queuePacket}
   */
  @Test
  void testDoesNotProcessOldSynchronizationPacketWithoutGapsInSequenceNumbers() throws InterruptedException {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    Mockito.doAnswer(new Answer<List<JsonNode>>() {
      public List<JsonNode> answer(InvocationOnMock invocation) throws Throwable {
        return Arrays.asList((JsonNode) invocation.getArgument(0));
      }
    }).when(clientPacketOrderer).restoreOrder(Mockito.any());
    Mockito.when(client.socketInstances.get(0).synchronizationThrottler.getActiveSynchronizationIds())
      .thenReturn(Arrays.asList("ABC"));

    ObjectNode synchronization1StartPacket = jsonMapper.createObjectNode();
    synchronization1StartPacket.put("type", "synchronizationStarted");
    synchronization1StartPacket.put("accountId", "accountId");
    synchronization1StartPacket.put("sequenceNumber", 1);
    synchronization1StartPacket.put("sequenceTimestamp", 1603124267178L);
    synchronization1StartPacket.put("synchronizationId", "ABC");
    server.emit("synchronization", synchronization1StartPacket);
    ObjectNode synchronization1OrdersPacket = jsonMapper.createObjectNode();
    synchronization1OrdersPacket.put("type", "orders");
    synchronization1OrdersPacket.put("accountId", "accountId");
    synchronization1OrdersPacket.set("orders", jsonMapper.createArrayNode());
    synchronization1StartPacket.put("sequenceNumber", 2);
    synchronization1StartPacket.put("sequenceTimestamp", 1603124267181L);
    synchronization1StartPacket.put("synchronizationId", "ABC");
    server.emit("synchronization", synchronization1OrdersPacket);
    Thread.sleep(50);
    Mockito.verify(listener).onSynchronizationStarted(Mockito.anyString());
    Mockito.verify(listener).onOrdersReplaced(Mockito.anyString(), Mockito.anyList());

    Mockito.when(client.socketInstances.get(0).synchronizationThrottler.getActiveSynchronizationIds())
      .thenReturn(Arrays.asList("DEF"));
    ObjectNode synchronization2StartPacket = jsonMapper.createObjectNode();
    synchronization2StartPacket.put("type", "synchronizationStarted");
    synchronization2StartPacket.put("accountId", "accountId");
    synchronization2StartPacket.put("sequenceNumber", 3);
    synchronization2StartPacket.put("sequenceTimestamp", 1603124267190L);
    synchronization2StartPacket.put("synchronizationId", "DEF");
    server.emit("synchronization", synchronization2StartPacket);
    ObjectNode synchronization1OrdersPacket2 = jsonMapper.createObjectNode();
    synchronization1OrdersPacket2.put("type", "orders");
    synchronization1OrdersPacket2.put("accountId", "accountId");
    synchronization1OrdersPacket2.set("orders", jsonMapper.createArrayNode());
    synchronization1OrdersPacket2.put("sequenceNumber", 4);
    synchronization1OrdersPacket2.put("sequenceTimestamp", 1603124267192L);
    synchronization1OrdersPacket2.put("synchronizationId", "ABC");
    server.emit("synchronization", synchronization1OrdersPacket2);
    ObjectNode synchronization2OrdersPacket = jsonMapper.createObjectNode();
    synchronization2OrdersPacket.put("type", "orders");
    synchronization2OrdersPacket.put("accountId", "accountId");
    synchronization2OrdersPacket.set("orders", jsonMapper.createArrayNode());
    synchronization2StartPacket.put("sequenceNumber", 5);
    synchronization2StartPacket.put("sequenceTimestamp", 1603124267195L);
    synchronization2StartPacket.put("synchronizationId", "DEF");
    server.emit("synchronization", synchronization2OrdersPacket);
    Thread.sleep(50);
    Mockito.verify(listener, Mockito.times(2)).onSynchronizationStarted(Mockito.anyString());
    Mockito.verify(listener, Mockito.times(2)).onOrdersReplaced(Mockito.anyString(), Mockito.anyList());
  };
  
  private static Stream<Arguments> provideAccountInformation() throws Exception {
    MetatraderAccountInformation accountInformation = new MetatraderAccountInformation();
    accountInformation.broker = "True ECN Trading Ltd";
    accountInformation.currency = "USD";
    accountInformation.server = "ICMarketsSC-Demo";
    accountInformation.balance = 7319.9;
    accountInformation.equity = 7306.649913200001;
    accountInformation.margin = 184.1;
    accountInformation.freeMargin = 7120.22;
    accountInformation.leverage = 100;
    accountInformation.marginLevel = 3967.58283542;
    return Stream.of(Arguments.of(accountInformation));
  }
  
  private static Stream<Arguments> provideMetatraderPosition() throws Exception {
    MetatraderPosition position = new MetatraderPosition();
    position = new MetatraderPosition();
    position.id = "46214692";
    position.type = PositionType.POSITION_TYPE_BUY;;
    position.symbol = "GBPUSD";
    position.magic = 1000;
    position.time = new IsoTime("2020-04-15T02:45:06.521Z");
    position.updateTime = new IsoTime("2020-04-15T02:45:06.521Z");
    position.openPrice = 1.26101;
    position.currentPrice = 1.24883;
    position.currentTickValue = 1;
    position.volume = 0.07;
    position.swap = 0.0;
    position.profit = -85.25999999999966;
    position.commission = -0.25;
    position.clientId = "TE_GBPUSD_7hyINWqAlE";
    position.stopLoss = 1.17721;
    position.unrealizedProfit = -85.25999999999901;
    position.realizedProfit = -6.536993168992922e-13;
    return Stream.of(Arguments.of(position));
  }
  
  private static Stream<Arguments> provideMetatraderOrder() throws Exception {
    MetatraderOrder order = new MetatraderOrder();
    order.id = "46871284";
    order.type = OrderType.ORDER_TYPE_BUY_LIMIT;
    order.state = OrderState.ORDER_STATE_PLACED;
    order.symbol = "AUDNZD";
    order.magic = 123456;
    order.platform = "mt5";
    order.time = new IsoTime("2020-04-20T08:38:58.270Z");
    order.openPrice = 1.03;
    order.currentPrice = 1.05206;
    order.volume = 0.01;
    order.currentVolume = 0.01;
    order.comment = "COMMENT2";
    return Stream.of(Arguments.of(order));
  }
  
  private static Stream<Arguments> provideMetatraderHistoryOrders() throws Exception {
    MetatraderOrder order = new MetatraderOrder();
    order.clientId = "TE_GBPUSD_7hyINWqAlE";
    order.currentPrice = 1.261;
    order.currentVolume = 0;
    order.doneTime = new IsoTime("2020-04-15T02:45:06.521Z");
    order.id = "46214692";
    order.magic = 1000;
    order.platform = "mt5";
    order.positionId = "46214692";
    order.state = OrderState.ORDER_STATE_FILLED;
    order.symbol = "GBPUSD";
    order.time = new IsoTime("2020-04-15T02:45:06.260Z");
    order.type = OrderType.ORDER_TYPE_BUY;
    order.volume = 0.07;
    MetatraderHistoryOrders history = new MetatraderHistoryOrders();
    history.historyOrders = Lists.list(order);
    history.synchronizing = false;
    return Stream.of(Arguments.of(history));
  }
  
  private static Stream<Arguments> provideMetatraderDeals() throws Exception {
    MetatraderDeal deal = new MetatraderDeal();
    deal.clientId = "TE_GBPUSD_7hyINWqAlE";
    deal.commission = -0.25;
    deal.entryType = DealEntryType.DEAL_ENTRY_IN;
    deal.id = "33230099";
    deal.magic = 1000L;
    deal.platform = "mt5";
    deal.orderId = "46214692";
    deal.positionId = "46214692";
    deal.price = 1.26101;
    deal.profit = 0;
    deal.swap = 0.0;
    deal.symbol = "GBPUSD";
    deal.time = new IsoTime("2020-04-15T02:45:06.521Z");
    deal.type = DealType.DEAL_TYPE_BUY;
    deal.volume = 0.07;
    MetatraderDeals deals = new MetatraderDeals();
    deals.deals = Lists.list(deal);
    deals.synchronizing = false;
    return Stream.of(Arguments.of(deals));
  }
  
  private static Stream<Arguments> provideCombinedModelsForUpdateEvent() throws Exception {
    return Stream.of(Arguments.of(
      provideAccountInformation().iterator().next().get()[0],
      provideMetatraderPosition().iterator().next().get()[0],
      provideMetatraderOrder().iterator().next().get()[0],
      provideMetatraderHistoryOrders().iterator().next().get()[0],
      provideMetatraderDeals().iterator().next().get()[0]
    ));
  }
  
  private static Stream<Arguments> provideSymbolSpecification() {
    MetatraderSymbolSpecification specification = new MetatraderSymbolSpecification();
    specification.symbol = "AUDNZD";
    specification.tickSize = 0.00001;
    specification.minVolume = 0.01;
    specification.maxVolume = 100;
    specification.volumeStep = 0.01;
    return Stream.of(Arguments.of(specification));
  }
  
  private static Stream<Arguments> provideSymbolPrice() {
    MetatraderSymbolPrice price = new MetatraderSymbolPrice();
    price.symbol = "AUDNZD";
    price.bid = 1.05297;
    price.ask = 1.05309;
    price.profitTickValue = 0.59731;
    price.lossTickValue = 0.59736;
    return Stream.of(Arguments.of(price));
  }
  
  private void spyClientPacketOrderer() throws Exception {
    clientPacketOrderer = (PacketOrderer) FieldUtils.readField(client, "packetOrderer", true);
    clientPacketOrderer = Mockito.spy(clientPacketOrderer);
    FieldUtils.writeField(client, "packetOrderer", clientPacketOrderer, true);
  }
}