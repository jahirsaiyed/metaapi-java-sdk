package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  private static MetaApiWebsocketClient client;
  private static SocketIOServer server;
  private static SocketIOClient socket;
  
  // Some variables that cannot be written from request callbacks 
  // if they are local test variables
  private boolean requestReceived = false;
  private MetatraderTrade actualTrade = null;
  private ResponseTimestamps timestamps = null;
  private int requestCounter = 0;
  
  @BeforeAll
  static void setUpBeforeClass() throws IOException {
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
    client = new MetaApiWebsocketClient("token", new MetaApiWebsocketClient.ClientOptions() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 6000L;
      connectTimeout = 6000L;
      retryOpts = new RetryOptions() {{ retries = 3; }};
    }});
    client.setUrl("http://localhost:6784");
  }
  
  @AfterAll
  static void tearDownAfterClass() {
  	server.stop();
  }

  @BeforeEach
  void setUp() throws Throwable {
    client.connect().get();
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
    String clientId = socket.getHandshakeData().getHttpHeaders().get("Client-id");
    assertTrue(clientId != null);
    assertTrue(Float.valueOf(clientId) >= 0 && Float.valueOf(clientId) < 1);
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
          response.set("requestId", request.get("requestId"));
          client.sendEvent("response", response.toString());
        }
      }
    });
    JsonNode actual = client.unsubscribe("accountId").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(response);
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
    packet2.put("sessionId", (String) FieldUtils.readField(client, "sessionId", true));
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
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("connected", true);
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    socket.sendEvent("synchronization", authPacket.toString());
    ObjectNode statusPacket = jsonMapper.createObjectNode();
    statusPacket.put("type", "status");
    statusPacket.put("accountId", "accountId");
    statusPacket.put("connected", true);
    statusPacket.put("host", "ps-mpa-1");
    statusPacket.put("instanceIndex", 1);
    socket.sendEvent("synchronization", statusPacket.toString());
    Thread.sleep(200);
    Mockito.verify(listener).onBrokerConnectionStatusChanged(1, true);
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
    client.addSynchronizationListener("accountId", listener);
    ObjectNode authPacket = jsonMapper.createObjectNode();
    authPacket.put("type", "authenticated");
    authPacket.put("accountId", "accountId");
    authPacket.put("connected", true);
    authPacket.put("host", "ps-mpa-1");
    authPacket.put("instanceIndex", 1);
    socket.sendEvent("synchronization", authPacket.toString());
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
    Mockito.when(listener.onAccountInformationUpdated(Mockito.anyInt(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onPositionUpdated(Mockito.anyInt(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onPositionRemoved(Mockito.anyInt(), Mockito.anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onOrderUpdated(Mockito.anyInt(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onOrderCompleted(Mockito.anyInt(), Mockito.anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onHistoryOrderAdded(Mockito.anyInt(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(listener.onDealAdded(Mockito.anyInt(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
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
      client.subscribeToMarketData("accountId", 1, "EURUSD").join();
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
        if (requestCounter > 0) {
          fail();
        }
        requestCounter++;
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
  void testSubscribesToMarketDataWithMetatraderTerminal() throws Exception {
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
    client.subscribeToMarketData("accountId", 1, "EURUSD").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#unsubscribeFromMarketData(String, String)}
   */
  @Test
  void testUnubscribesFromMarketDataWithMetatraderTerminal() throws Exception {
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
    client.unsubscribeFromMarketData("accountId", 1, "EURUSD").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#unsubscribeFromMarketData(String, String)}
   */
  @Test
  void testUnubscribesFromMarketDataWithMetatraderTerminal() throws Exception {
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
    client.unsubscribeFromMarketData("accountId", 1, "EURUSD").get();
    assertTrue(requestReceived);
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolSpecification")
  void testSynchronizesSymbolSpecifications(MetatraderSymbolSpecification expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    client.addSynchronizationListener("accountId", listener);
    ObjectNode packet = jsonMapper.createObjectNode();
    packet.put("type", "specifications");
    packet.put("accountId", "accountId");
    packet.set("specifications", jsonMapper.valueToTree(Lists.list(expected)));
    packet.put("instanceIndex", 1);
    socket.sendEvent("synchronization", packet.toString());
    Thread.sleep(50);
    Mockito.verify(listener).onSymbolSpecificationUpdated(Mockito.eq(1), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(expected);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiWebsocketClient#addSynchronizationListener(String, SynchronizationListener)}
   */
  @ParameterizedTest
  @MethodSource("provideSymbolPrice")
  void testSynchronizesSymbolPrices(MetatraderSymbolPrice expected) throws Exception {
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    Mockito.when(listener.onSymbolPricesUpdated(Mockito.anyInt(), Mockito.anyList(), Mockito.anyDouble(),
      Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
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
    }), Mockito.eq(100.0), Mockito.eq(200.0), Mockito.eq(400.0), Mockito.eq(40000.0));
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
        timestamps = jsonMapper.treeToValue(request.get("timestamps"), ResponseTimestamps.class);
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