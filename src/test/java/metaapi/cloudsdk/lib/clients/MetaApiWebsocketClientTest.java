package metaapi.cloudsdk.lib.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.corundumstudio.socketio.listener.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.corundumstudio.socketio.*;

import metaapi.cloudsdk.lib.clients.errorHandler.*;
import metaapi.cloudsdk.lib.clients.errorHandler.InternalException;
import metaapi.cloudsdk.lib.clients.errorHandler.ValidationException.ErrorDetail;
import metaapi.cloudsdk.lib.clients.models.*;
import metaapi.cloudsdk.lib.clients.models.MetatraderDeal.*;
import metaapi.cloudsdk.lib.clients.models.MetatraderOrder.*;
import metaapi.cloudsdk.lib.clients.models.MetatraderPosition.*;
import metaapi.cloudsdk.lib.clients.models.MetatraderTrade.*;

/**
 * Tests {@link MetaApiWebsocketClient}
 */
class MetaApiWebsocketClientTest {

    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private static MetaApiWebsocketClient client;
    private SocketIOServer server;
    
    // Some variables that cannot be written from request callbacks 
    // if they are local test variables
    private boolean requestReceived = false;
    private MetatraderTrade actualTrade = null;
    
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        client = new MetaApiWebsocketClient("token");
        client.setUrl("http://localhost:6784");
    }

    @BeforeEach
    void setUp() throws Exception {
        Configuration serverConfiguration = new Configuration();
        serverConfiguration.setPort(6784);
        serverConfiguration.setContext("/ws");
        server = new SocketIOServer(serverConfiguration);
        server.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(SocketIOClient socket) {
                if (!socket.getHandshakeData().getSingleUrlParam("auth-token").equals("token")) {
                    socket.sendEvent("UnauthorizedError", "Authorization token invalid");
                    socket.disconnect();
                }
            }
        });
        server.start();
        client.connect().get();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.stop();
    }

    /**
     * Tests {@link MetaApiWebsocketClient#getAccountInformation(String)}
     */
    @Test
    void testRetrievesMetaTraderAccountInformationFromApi() throws Exception {
        MetatraderAccountInformation expected = new MetatraderAccountInformation();
        expected.broker = "True ECN Trading Ltd";
        expected.currency = "USD";
        expected.server = "ICMarketsSC-Demo";
        expected.balance = 7319.9;
        expected.equity = 7306.649913200001;
        expected.margin = 184.1;
        expected.freeMargin = 7120.22;
        expected.leverage = 100;
        expected.marginLevel = Optional.of(3967.58283542);
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getAccountInformation") 
                     && request.get("accountId").asText().equals("accountId")
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
        List<MetatraderPosition> expected = List.of(position);
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getPositions") 
                     && request.get("accountId").asText().equals("accountId")
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (	request.get("type").asText().equals("getPosition") 
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("positionId").asText().equals(expected.id)
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
        List<MetatraderOrder> expected = List.of(order);
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getOrders")
                     && request.get("accountId").asText().equals("accountId")
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getOrder")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("orderId").asText().equals(expected.id)
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getHistoryOrdersByTicket")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("ticket").asText().equals(expected.historyOrders.get(0).id)
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getHistoryOrdersByPosition")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("positionId").asText().equals(expected.historyOrders.get(0).positionId.get())
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
            .getHistoryOrdersByPosition("accountId", expected.historyOrders.get(0).positionId.get()).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#getHistoryOrdersByTimeRange(String, IsoTime, IsoTime, int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderHistoryOrders")
    void testRetrievesMetaTraderHistoryOrdersFromApiByTimeRange(MetatraderHistoryOrders expected) throws Exception {
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getHistoryOrdersByTimeRange")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("startTime").asText().equals("2020-04-15T02:45:00Z")
                     && request.get("endTime").asText().equals("2020-04-15T02:46:00Z")
                     && request.get("offset").asInt() == 1 && request.get("limit").asInt() == 100
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getDealsByTicket")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("ticket").asText().equals(expected.deals.get(0).orderId.get())
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
        MetatraderDeals actual = client.getDealsByTicket("accountId", expected.deals.get(0).orderId.get()).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#getDealsByPosition(String, String)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderDeals")
    void testRetrievesMetaTraderDealsFromApiByPosition(MetatraderDeals expected) throws Exception {
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getDealsByPosition")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("positionId").asText().equals(expected.deals.get(0).positionId.get())
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
        MetatraderDeals actual = client.getDealsByPosition("accountId", expected.deals.get(0).positionId.get()).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#getDealsByTimeRange(String, IsoTime, IsoTime, int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderDeals")
    void testRetrievesMetaTraderDealsFromApiByTimeRange(MetatraderDeals expected) throws Exception {
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getDealsByTimeRange")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("startTime").asText().equals("2020-04-15T02:45:00Z")
                     && request.get("endTime").asText().equals("2020-04-15T02:46:00Z")
                     && request.get("offset").asInt() == 1 && request.get("limit").asInt() == 100
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("removeHistory")
                     && request.get("accountId").asText().equals("accountId")
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
        client.removeHistory("accountId").get();
        assertTrue(requestReceived);
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
     */
    @Test
    void testExecutesATradeViaApi() throws Exception {
        MetatraderTrade expectedTrade = new MetatraderTrade();
        expectedTrade.actionType = ActionType.ORDER_TYPE_SELL;
        expectedTrade.symbol = Optional.of("AUDNZD");
        expectedTrade.volume = Optional.of(0.07);
        emptyOptionalNullValues(expectedTrade);
        MetatraderTradeResponse expectedTradeResponse = new MetatraderTradeResponse();
        expectedTradeResponse.error = 10009;
        expectedTradeResponse.description = "TRADE_RETCODE_DONE";
        expectedTradeResponse.orderId = Optional.of("46870472");
        emptyOptionalNullValues(expectedTradeResponse);
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("trade")
                     && request.get("accountId").asText().equals("accountId")
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
     * Tests {@link MetaApiWebsocketClient#subscribe(String)}
     */
    @Test
    void testSubscribesToMetatraderTerminal() throws Exception {
        requestReceived = false;
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("subscribe")
                     && request.get("accountId").asText().equals("accountId")
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
        client.subscribe("accountId").get();
        assertTrue(requestReceived);
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#reconnect(String)}
     */
    @Test
    void testReconnectsToMetatraderTerminal() throws Exception {
        requestReceived = false;
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("reconnect")
                     && request.get("accountId").asText().equals("accountId")
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
    @Test
    void testRetrievesSymbolSpecificationFromApi() throws Exception {
        MetatraderSymbolSpecification expected = new MetatraderSymbolSpecification();
        expected.symbol = "AUDNZD";
        expected.tickSize = 0.00001;
        expected.minVolume = 0.01;
        expected.maxVolume = 100;
        expected.volumeStep = 0.01;
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getSymbolSpecification")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("symbol").asText().equals(expected.symbol)
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
    @Test
    void testRetrievesSymbolPriceFromApi() throws Exception {
        MetatraderSymbolPrice expected = new MetatraderSymbolPrice();
        expected.symbol = "AUDNZD";
        expected.bid = 1.05297;
        expected.ask = 1.05309;
        expected.profitTickValue = 0.59731;
        expected.lossTickValue = 0.59736;
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                if (    request.get("type").asText().equals("getSymbolPrice")
                     && request.get("accountId").asText().equals("accountId")
                     && request.get("symbol").asText().equals(expected.symbol)
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
     * Tests {@link MetaApiWebsocketClient#trade(String, MetatraderTrade)}
     */
    @Test
    void testHandlesValidationError() throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL;
        trade.symbol = Optional.of("AUDNZD");
        ErrorDetail expectedDetail = new ErrorDetail();
        expectedDetail.parameter = "volume";
        expectedDetail.message = "Required value.";
        List<ErrorDetail> expectedDetails = List.of(expectedDetail);
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
                WebsocketError error = new WebsocketError();
                error.id = 1;
                error.error = "ValidationError";
                error.message = "Validation failed";
                error.requestId = request.get("requestId").asText();
                error.details = Optional.of(expectedDetails);
                client.sendEvent("processingError", jsonMapper.writeValueAsString(error));
            }
        });
        try {
            client.trade("accountId", trade).get();
            throw new Exception("ValidationError expected");
        } catch (ExecutionException error) {
            assertTrue(error.getCause() instanceof ValidationException);
            ValidationException validationError = (ValidationException) error.getCause();
            assertThat(validationError.details).usingRecursiveComparison().isEqualTo(expectedDetails);
        }
    }
    
    /**
     * Tests {@link MetaApiWebsocketClient#getPosition(String, String)}
     */
    @Test
    void testHandlesNotFoundError() throws Exception {
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
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
        server.addEventListener("request", String.class, new DataListener<String>() {
            @Override
            public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                JsonNode request = jsonMapper.readTree(data);
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
        position.commission = Optional.of(-0.25);
        position.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        position.stopLoss = Optional.of(1.17721);
        position.unrealizedProfit = Optional.of(-85.25999999999901);
        position.realizedProfit = Optional.of(-6.536993168992922e-13);
        emptyOptionalNullValues(position);
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
        order.openPrice = Optional.of(1.03);
        order.currentPrice = 1.05206;
        order.volume = 0.01;
        order.currentVolume = 0.01;
        order.comment = Optional.of("COMMENT2");
        emptyOptionalNullValues(order);
        return Stream.of(Arguments.of(order));
    }
    
    private static Stream<Arguments> provideMetatraderHistoryOrders() throws Exception {
        MetatraderOrder order = new MetatraderOrder();
        order.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        order.currentPrice = 1.261;
        order.currentVolume = 0;
        order.doneTime = Optional.of(new IsoTime("2020-04-15T02:45:06.521Z"));
        order.id = "46214692";
        order.magic = 1000;
        order.platform = "mt5";
        order.positionId = Optional.of("46214692");
        order.state = OrderState.ORDER_STATE_FILLED;
        order.symbol = "GBPUSD";
        order.time = new IsoTime("2020-04-15T02:45:06.260Z");
        order.type = OrderType.ORDER_TYPE_BUY;
        order.volume = 0.07;
        emptyOptionalNullValues(order);
        MetatraderHistoryOrders history = new MetatraderHistoryOrders();
        history.historyOrders = List.of(order);
        history.synchronizing = false;
        return Stream.of(Arguments.of(history));
    }
    
    private static Stream<Arguments> provideMetatraderDeals() throws Exception {
        MetatraderDeal deal = new MetatraderDeal();
        deal.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        deal.commission = Optional.of(-0.25);
        deal.entryType = Optional.of(DealEntryType.DEAL_ENTRY_IN);
        deal.id = "33230099";
        deal.magic = Optional.of(1000);
        deal.platform = "mt5";
        deal.orderId = Optional.of("46214692");
        deal.positionId = Optional.of("46214692");
        deal.price = Optional.of(1.26101);
        deal.profit = 0;
        deal.swap = Optional.of(0.0);
        deal.symbol = Optional.of("GBPUSD");
        deal.time = new IsoTime("2020-04-15T02:45:06.521Z");
        deal.type = DealType.DEAL_TYPE_BUY;
        deal.volume = Optional.of(0.07);
        emptyOptionalNullValues(deal);
        MetatraderDeals deals = new MetatraderDeals();
        deals.deals = List.of(deal);
        deals.synchronizing = false;
        return Stream.of(Arguments.of(deals));
    }
    
    /**
     * Model optional null values are not sent at all but in case their absence in responses they are 
     * still parsed as Optional.empty that is correct semantically but breaks tests because (initial) 
     * null != (recieved) Optional.empty. So this method explicitly sets them as Optional.empty.
     */
    private static void emptyOptionalNullValues(Object object) throws Exception {
        Field[] publicFields = object.getClass().getFields();
        for (int i = 0; i < publicFields.length; ++i) {
            Field field = publicFields[i];
            if (field.get(object) == null && field.getType().isAssignableFrom(Optional.class)) {
                field.set(object, Optional.empty());
            }
        }
    }
}