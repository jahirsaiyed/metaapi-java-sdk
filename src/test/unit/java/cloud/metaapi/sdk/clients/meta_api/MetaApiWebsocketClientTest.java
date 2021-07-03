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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.FieldUtils;
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

import cloud.metaapi.sdk.clients.RetryOptions;
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
import cloud.metaapi.sdk.util.JsonMapper;

import com.corundumstudio.socketio.*;

/**
 * Tests {@link MetaApiWebsocketClient}
 */
class MetaApiWebsocketClientTest {

  private static ObjectMapper jsonMapper = JsonMapper.getInstance();
  private static SocketIOServer server;
  private static SocketIOClient socket;
  private static int initResetDisconnectTimerTimeout;
  private static MetaApiWebsocketClient client;
  
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
    MetaApiWebsocketClient.resetDisconnectTimerTimeout = initResetDisconnectTimerTimeout;
    Configuration serverConfiguration = new Configuration();
    serverConfiguration.setPort(6784);
    serverConfiguration.setContext("/ws");
    server = new SocketIOServer(serverConfiguration);
    server.addConnectListener(new ConnectListener() {
      @Override
      public void onConnect(SocketIOClient connected) {
        socket = connected;
        if (!socket.getHandshakeData().getSingleUrlParam("auth-token").equals("token")) {
          socket.sendEvent("UnauthorizedError", "Authorization token invalid");
          socket.disconnect();
        }
      }
    });
    server.start();
    initResetDisconnectTimerTimeout = MetaApiWebsocketClient.resetDisconnectTimerTimeout;
  }
  
  @AfterAll
  static void tearDownAfterClass() {
  	server.stop();
  }

  @BeforeEach
  void setUp() throws Throwable {
    client = new MetaApiWebsocketClient("token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 6000L;
      connectTimeout = 6000L;
      retryOpts = new RetryOptions() {{
        retries = 3;
        minDelayInSeconds = 1;
        maxDelayInSeconds = 3;
      }};
    }});
    client.setUrl("http://localhost:6784");
    client.socketInstancesByAccounts = Maps.newHashMap("accountId", 0);
    client.connect().join();
    SynchronizationThrottler clientSyncThrottler = Mockito
      .spy(client.socketInstances.get(0).synchronizationThrottler);
    Mockito.when(clientSyncThrottler.getActiveSynchronizationIds()).thenReturn(Lists.emptyList());
    client.socketInstances.get(0).synchronizationThrottler = clientSyncThrottler;
  }

  @AfterEach
  void tearDown() {
  	server.removeAllListeners("request");
    client.removeAllListeners();
    client.close();
  }
  
  
  /**
   * Tests {@link MetaApiWebsocketClient#connect()}
   */
  @Test
  void testSendsClientIdWhenConnects() {
    String clientId = socket.getHandshakeData().getSingleUrlParam("clientId");
    assertTrue(clientId != null);
    assertTrue(Double.valueOf(clientId) >= 0 && Double.valueOf(clientId) < 1);
  }
  
 /**
  * Tests {@link MetaApiWebsocketClient#tryReconnect()}
  */
 @Test
 void testChangesClientIdOnReconnect() throws InterruptedException {
   clientId = Double.valueOf(socket.getHandshakeData().getSingleUrlParam("clientId"));
   connectAmount = 0;
   client.close();
   server.addConnectListener(new ConnectListener() {
     @Override
     public void onConnect(SocketIOClient connected) {
       socket = connected;
       connectAmount++;
       double headerClientId = Double.valueOf(socket.getHandshakeData().getHttpHeaders().get("client-id"));
       double queryClientId = Double.valueOf(socket.getHandshakeData().getSingleUrlParam("clientId"));
       assertEquals(headerClientId, queryClientId);
       assertNotEquals(clientId, headerClientId);
       assertNotEquals(clientId, queryClientId);
       clientId = queryClientId;
       if (connectAmount == 1) {
         socket.disconnect();
       }
     }
   });
   client.connect().join();
   Thread.sleep(2000);
   assertTrue(connectAmount >= 2);
 }

  /**
   * Tests {@link MetaApiWebsocketClient#getAccountInformation(String)}
   */
  @ParameterizedTest
  @MethodSource("provideAccountInformation")
  void testRetrievesMetaTraderAccountInformationFromApi(MetatraderAccountInformation expected) throws Exception {
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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

    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
      Map<String, String> details = new HashMap<>();
      details.put("parameter", "volume");
      details.put("message", "Required value.");
      assertThat(((ValidationException) err.getCause().getCause()).details)
        .usingRecursiveComparison().isEqualTo(Arrays.asList(details));
    }
    server.removeAllListeners("request");
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
      Map<String, String> expectedDetail = new HashMap<>();
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
      Map<String, String> expectedDetail = new HashMap<>();
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   client.addSynchronizationListener("accountId", listener);
   ObjectNode packet = jsonMapper.createObjectNode();
   packet.put("type", "authenticated");
   packet.put("accountId", "accountId");
   packet.put("instanceIndex", 1);
   packet.put("replicas", 2);
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onConnected(1, 2);
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
  */
 @Test
 void testProcessesAuthenticatedSynchronizationEventWithSessionId() throws Exception {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   client.addSynchronizationListener("accountId", listener);
   ObjectNode packet1 = jsonMapper.createObjectNode();
   packet1.put("type", "authenticated");
   packet1.put("accountId", "accountId");
   packet1.put("instanceIndex", 2);
   packet1.put("replicas", 4);
   packet1.put("sessionId", "wrong");
   socket.sendEvent("synchronization", packet1.toString());
   ObjectNode packet2 = jsonMapper.createObjectNode();
   packet2.put("type", "authenticated");
   packet2.put("accountId", "accountId");
   packet2.put("instanceIndex", 1);
   packet2.put("replicas", 2);
   packet2.put("sessionId", client.socketInstances.get(0).sessionId);
   socket.sendEvent("synchronization", packet2.toString());
   Thread.sleep(200);
   Mockito.verify(listener, Mockito.times(1)).onConnected(1, 2);
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
  */
 @Test
 void testProcessesBrokerConnectionStatusEvent() throws Exception {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
   Mockito.when(listener.onConnected(Mockito.anyInt(), Mockito.anyInt()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
       @Override
       public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
         connectedFuture.complete(null);
         return connectedFuture;
       }
     });
   CompletableFuture<Void> statusChangedFuture = new CompletableFuture<>();
   Mockito.when(listener.onBrokerConnectionStatusChanged(Mockito.anyInt(), Mockito.anyBoolean()))
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
   socket.sendEvent("synchronization", authPacket.toString());
   connectedFuture.join();
   ObjectNode statusPacket = jsonMapper.createObjectNode();
   statusPacket.put("type", "status");
   statusPacket.put("accountId", "accountId");
   statusPacket.put("connected", true);
   statusPacket.put("host", "ps-mpa-1");
   statusPacket.put("instanceIndex", 1);
   socket.sendEvent("synchronization", statusPacket.toString());
   statusChangedFuture.join();
   Thread.sleep(200);
   Mockito.verify(listener).onBrokerConnectionStatusChanged(1, true);
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
  */
 @Test
 void testCallsOnDisconnectIfThereWasNoSignalForALongTime() throws Exception {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   Mockito.when(listener.onDisconnected(Mockito.anyInt()))
     .thenReturn(CompletableFuture.completedFuture(null));
   MetaApiWebsocketClient.resetDisconnectTimerTimeout = 10000;
   client.addSynchronizationListener("accountId", listener);
   ObjectNode authPacket = jsonMapper.createObjectNode();
   authPacket.put("type", "authenticated");
   authPacket.put("accountId", "accountId");
   authPacket.put("host", "ps-mpa-1");
   authPacket.put("instanceIndex", 1);
   authPacket.put("replicas", 2);
   socket.sendEvent("synchronization", authPacket.toString());
   ObjectNode statusPacket = jsonMapper.createObjectNode();
   statusPacket.put("type", "status");
   statusPacket.put("accountId", "accountId");
   statusPacket.put("host", "ps-mpa-1");
   statusPacket.put("connected", true);
   statusPacket.put("instanceIndex", 1);
   socket.sendEvent("synchronization", statusPacket.toString());
   Thread.sleep(2000);
   socket.sendEvent("synchronization", statusPacket.toString());
   Thread.sleep(5000);
   Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyInt());
   socket.sendEvent("synchronization", authPacket.toString());
   Thread.sleep(2000);
   Mockito.verify(listener, Mockito.never()).onDisconnected(Mockito.anyInt());
   Thread.sleep(10000);
   Mockito.verify(listener).onDisconnected(1);
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
  */
 @Test
 void testProcessesServerSideHealthStatusEvent() throws Exception {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   Mockito.when(listener.onConnected(Mockito.anyInt(), Mockito.anyInt()))
     .thenReturn(CompletableFuture.completedFuture(null));
   Mockito.when(listener.onBrokerConnectionStatusChanged(Mockito.anyInt(), Mockito.anyBoolean()))
     .thenReturn(CompletableFuture.completedFuture(null));
   CompletableFuture<Void> onHealthStatusFuture = new CompletableFuture<>();
   Mockito.when(listener.onHealthStatus(Mockito.anyInt(), Mockito.any()))
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
   socket.sendEvent("synchronization", authPacket.toString());
   Thread.sleep(200);
   ObjectNode statusPacket = jsonMapper.createObjectNode();
   statusPacket.put("type", "status");
   statusPacket.put("accountId", "accountId");
   statusPacket.put("connected", true);
   HealthStatus healthStatus = new HealthStatus() {{ restApiHealthy = true; }};
   statusPacket.set("healthStatus", jsonMapper.valueToTree(healthStatus));
   statusPacket.put("host", "ps-mpa-1");
   statusPacket.put("instanceIndex", 1);
   socket.sendEvent("synchronization", statusPacket.toString());
   Thread.sleep(200);
   onHealthStatusFuture.join();
   Mockito.verify(listener).onHealthStatus(Mockito.eq(1), Mockito.argThat(arg -> {
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
   socket.sendEvent("synchronization", authPacket.toString());
   ObjectNode disconnectPacket = jsonMapper.createObjectNode();
   disconnectPacket.put("type", "disconnected");
   disconnectPacket.put("accountId", "accountId");
   disconnectPacket.put("host", "ps-mpa-1");
   disconnectPacket.put("instanceIndex", 1);
   socket.sendEvent("synchronization", disconnectPacket.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onDisconnected(1);
 }
 
 @Test
 void testOnlyAcceptsPacketsWithOwnSynchronizationIds() throws InterruptedException {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   Mockito.when(listener.onAccountInformationUpdated(Mockito.anyInt(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   client.addSynchronizationListener("accountId", listener);
   Mockito.when(client.socketInstances.get(0).synchronizationThrottler.getActiveSynchronizationIds())
     .thenReturn(Lists.list("synchronizationId"));
   CompletableFuture<Void> onAccountInformationUpdatedFuture = new CompletableFuture<>();
   Mockito.when(listener.onAccountInformationUpdated(Mockito.anyInt(), Mockito.any()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
     @Override
     public CompletableFuture<Void> answer(InvocationOnMock invocation) {
       onAccountInformationUpdatedFuture.complete(null);
       return onAccountInformationUpdatedFuture;
     }
   });
   ObjectNode packet1 = jsonMapper.createObjectNode();
   packet1.put("type", "accountInformation");
   packet1.put("accountId", "accountId");
   packet1.set("accountInformation", jsonMapper.createObjectNode());
   packet1.put("instanceIndex", 1);
   socket.sendEvent("synchronization", packet1.toString());
   onAccountInformationUpdatedFuture.join();
   Mockito.verify(listener, Mockito.times(1)).onAccountInformationUpdated(Mockito.anyInt(), Mockito.any());
   ObjectNode packet2 = jsonMapper.createObjectNode();
   packet2.put("type", "accountInformation");
   packet2.put("accountId", "accountId");
   packet2.set("accountInformation", jsonMapper.createObjectNode());
   packet2.put("instanceIndex", 1);
   packet2.put("synchronizationId", "wrong");
   socket.sendEvent("synchronization", packet2.toString());
   Thread.sleep(200);
   Mockito.verify(listener, Mockito.times(1)).onAccountInformationUpdated(Mockito.anyInt(), Mockito.any());
   CompletableFuture<Void> onAccountInformationUpdatedFuture2 = new CompletableFuture<>();
   Mockito.when(listener.onAccountInformationUpdated(Mockito.anyInt(), Mockito.any()))
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
   socket.sendEvent("synchronization", packet3.toString());
   onAccountInformationUpdatedFuture2.join();
   Mockito.verify(listener, Mockito.times(2)).onAccountInformationUpdated(Mockito.anyInt(), Mockito.any());
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#synchronize(String, String, IsoTime, IsoTime)}
  */
 @Test
 void testSynchronizesWithMetatraderTerminal() throws Exception {
   requestReceived = false;
   server.addEventListener("request", Object.class, new DataListener<Object>() {
     @Override
     public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
       JsonNode request = jsonMapper.valueToTree(data);
       if (  request.get("type").asText().equals("synchronize")
          && request.get("accountId").asText().equals("accountId")
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
   client.synchronize("accountId", 1, "synchronizationId", 
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
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onSynchronizationStarted(1);
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
   packet.set("accountInformation", jsonMapper.valueToTree(expected));
   packet.put("instanceIndex", 1);
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onAccountInformationUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
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
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onPositionsReplaced(Mockito.eq(1), Mockito.argThat(arg -> {
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
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onOrdersReplaced(Mockito.eq(1), Mockito.argThat(arg -> {
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
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onHistoryOrderAdded(Mockito.eq(1), Mockito.argThat(arg -> {
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
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onDealAdded(Mockito.eq(1), Mockito.argThat(arg -> {
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
   Mockito.when(listener.onAccountInformationUpdated(Mockito.anyInt(), Mockito.any()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
       @Override
       public CompletableFuture<Void> answer(InvocationOnMock invocation) {
         onAccountInformationUpdatedFuture.complete(null);
         return onAccountInformationUpdatedFuture;
       }
     });
   CompletableFuture<Void> onPositionUpdatedFuture = new CompletableFuture<>();
   Mockito.when(listener.onPositionUpdated(Mockito.anyInt(), Mockito.any()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
       @Override
       public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
         onPositionUpdatedFuture.complete(null);
         return onPositionUpdatedFuture;
       }
     });
   CompletableFuture<Void> onPositionRemovedFuture = new CompletableFuture<>();
   Mockito.when(listener.onPositionRemoved(Mockito.anyInt(), Mockito.anyString()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
       @Override
       public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
         onPositionRemovedFuture.complete(null);
         return onPositionRemovedFuture;
       }
     });
   Mockito.when(listener.onOrderUpdated(Mockito.anyInt(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   CompletableFuture<Void> onOrderCompletedFuture = new CompletableFuture<>();
   Mockito.when(listener.onOrderCompleted(Mockito.anyInt(), Mockito.anyString()))
     .thenAnswer(new Answer<CompletableFuture<Void>>() {
       @Override
       public CompletableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
         onOrderCompletedFuture.complete(null);
         return onOrderCompletedFuture;
       }
     });
   Mockito.when(listener.onHistoryOrderAdded(Mockito.anyInt(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   CompletableFuture<Void> onDealAddedFuture = new CompletableFuture<>();
   Mockito.when(listener.onDealAdded(Mockito.anyInt(), Mockito.any()))
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
   packet.set("accountInformation", jsonMapper.valueToTree(accountInformation));
   packet.set("updatedPositions", jsonMapper.valueToTree(Lists.list(position)));
   packet.set("removedPositionIds", jsonMapper.valueToTree(Lists.list("1234")));
   packet.set("updatedOrders", jsonMapper.valueToTree(Lists.list(order)));
   packet.set("completedOrderIds", jsonMapper.valueToTree(Lists.list("2345")));
   packet.set("historyOrders", jsonMapper.valueToTree(historyOrders.historyOrders));
   packet.set("deals", jsonMapper.valueToTree(deals.deals));
   socket.sendEvent("synchronization", packet.toString());
   onAccountInformationUpdatedFuture.join();
   onPositionUpdatedFuture.join();
   onPositionRemovedFuture.join();
   onOrderCompletedFuture.join();
   onDealAddedFuture.join();
   Thread.sleep(200);
   Mockito.verify(listener).onAccountInformationUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(accountInformation);
     return true;
   }));
   Mockito.verify(listener).onPositionUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(position);
     return true;
   }));
   Mockito.verify(listener).onPositionRemoved(1, "1234");
   Mockito.verify(listener).onOrderUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(order);
     return true;
   }));
   Mockito.verify(listener).onOrderCompleted(1, "2345");
   Mockito.verify(listener).onHistoryOrderAdded(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(historyOrders.historyOrders.get(0));
     return true;
   }));
   Mockito.verify(listener).onDealAdded(Mockito.eq(1), Mockito.argThat(arg -> {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   Mockito.when(listener.onSymbolSpecificationsUpdated(Mockito.anyInt(), Mockito.anyList(), Mockito.anyList()))
     .thenReturn(CompletableFuture.completedFuture(null));
   Mockito.when(listener.onSymbolSpecificationUpdated(Mockito.anyInt(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   Mockito.when(listener.onSymbolSpecificationRemoved(Mockito.anyInt(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   client.addSynchronizationListener("accountId", listener);
   ObjectNode packet = jsonMapper.createObjectNode();
   packet.put("type", "specifications");
   packet.put("accountId", "accountId");
   packet.set("specifications", jsonMapper.valueToTree(Lists.list(expected)));
   packet.put("instanceIndex", 1);
   packet.set("removedSymbols", jsonMapper.valueToTree(Lists.list("AUDNZD")));
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(200);
   Mockito.verify(listener).onSymbolSpecificationsUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(Arrays.asList(expected));
     return true;
   }), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(Arrays.asList("AUDNZD"));
     return true;
   }));
   Mockito.verify(listener).onSymbolSpecificationRemoved(Mockito.eq(1), Mockito.eq("AUDNZD"));
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
  */
 @ParameterizedTest
 @MethodSource("provideSymbolPrice")
 void testSynchronizesSymbolPrices(MetatraderSymbolPrice expected) throws Exception {
   SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
   Mockito.when(listener.onSymbolPricesUpdated(Mockito.anyInt(), Mockito.anyList(), Mockito.anyDouble(),
     Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.any()))
     .thenReturn(CompletableFuture.completedFuture(null));
   client.addSynchronizationListener("accountId", listener);
   ObjectNode packet = jsonMapper.createObjectNode();
   packet.put("type", "prices");
   packet.put("accountId", "accountId");
   packet.set("prices", jsonMapper.valueToTree(Lists.list(expected)));
   packet.put("equity", 100);
   packet.put("margin", 200);
   packet.put("freeMargin", 400);
   packet.put("marginLevel", 40000);
   packet.put("instanceIndex", 1);
   socket.sendEvent("synchronization", packet.toString());
   Thread.sleep(50);
   Mockito.verify(listener).onSymbolPriceUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(expected);
     return true;
   }));
   Mockito.verify(listener).onSymbolPricesUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
     assertThat(arg).usingRecursiveComparison().isEqualTo(Arrays.asList(expected));
     return true;
   }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0),
     Mockito.any());
 }
 
 /**
  * Tests {@link MetaApiWebsocketClient#waitSynchronized(String, String, Long)}
  */
 @Test
 void testWaitsForServerSideTerminalStateSynchronization() {
   requestReceived = false;
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
   socket.sendEvent("synchronization", packet.toString());
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
   socket.sendEvent("synchronization", packet.toString());
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
   server.addEventListener("request", Object.class, new DataListener<Object>() {
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
    position.swap = 0;
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
    deal.magic = 1000;
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
}