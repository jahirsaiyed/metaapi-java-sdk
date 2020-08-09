package cloud.metaapi.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.SynchronizationListener;
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.clients.models.MetatraderDeal.DealEntryType;
import cloud.metaapi.sdk.clients.models.MetatraderDeal.DealType;
import cloud.metaapi.sdk.clients.models.MetatraderOrder.OrderState;
import cloud.metaapi.sdk.clients.models.MetatraderOrder.OrderType;
import cloud.metaapi.sdk.clients.models.MetatraderPosition.PositionType;
import cloud.metaapi.sdk.clients.models.MetatraderTrade.ActionType;

/**
 * Tests {@link MetaApiConnection}
 */
class MetaApiConnectionTest {

    private MetaApiConnection api;
    private MetaApiWebsocketClient client;
    
    @BeforeEach
    void setUp() {
        MetatraderAccountDto accountDto = new MetatraderAccountDto();
        accountDto._id = "accountId";
        accountDto.synchronizationMode = "user";
        client = Mockito.mock(MetaApiWebsocketClient.class);
        api = new MetaApiConnection(client, new MetatraderAccount(
            accountDto, Mockito.mock(MetatraderAccountClient.class), client
        ));
    }

    /**
     * Tests {@link MetaApiConnection#getAccountInformation()}
     */
    @Test
    void testRetrivesAccountInformation() throws Exception {
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
        Mockito.when(client.getAccountInformation("accountId")).thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderAccountInformation actual = api.getAccountInformation().get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getPositions()}
     */
    @ParameterizedTest
    @MethodSource("providePosition")
    void testRetrivesPositions(MetatraderPosition position) throws Exception {
        List<MetatraderPosition> expected = List.of(position);
        Mockito.when(client.getPositions("accountId")).thenReturn(CompletableFuture.completedFuture(expected));
        List<MetatraderPosition> actual = api.getPositions().get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getPosition(String)}
     */
    @ParameterizedTest
    @MethodSource("providePosition")
    void testRetrivesPositionById(MetatraderPosition expected) throws Exception {
        Mockito.when(client.getPosition("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderPosition actual = api.getPosition("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getOrders()}
     */
    @ParameterizedTest
    @MethodSource("provideOrder")
    void testRetrivesPositions(MetatraderOrder order) throws Exception {
        List<MetatraderOrder> expected = List.of(order);
        Mockito.when(client.getOrders("accountId")).thenReturn(CompletableFuture.completedFuture(expected));
        List<MetatraderOrder> actual = api.getOrders().get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getOrder(String)}
     */
    @ParameterizedTest
    @MethodSource("provideOrder")
    void testRetrivesOrderById(MetatraderOrder expected) throws Exception {
        Mockito.when(client.getOrder("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderOrder actual = api.getOrder("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getHistoryOrdersByTicket(String)}
     */
    @ParameterizedTest
    @MethodSource("provideHistoryOrders")
    void testRetrivesHistoryOrdersByTicket(MetatraderHistoryOrders expected) throws Exception {
        Mockito.when(client.getHistoryOrdersByTicket("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderHistoryOrders actual = api.getHistoryOrdersByTicket("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getHistoryOrdersByPosition(String)}
     */
    @ParameterizedTest
    @MethodSource("provideHistoryOrders")
    void testRetrivesHistoryOrdersByPosition(MetatraderHistoryOrders expected) throws Exception {
        Mockito.when(client.getHistoryOrdersByPosition("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderHistoryOrders actual = api.getHistoryOrdersByPosition("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getHistoryOrdersByTimeRange(IsoTime, IsoTime, int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideHistoryOrders")
    void testRetrivesHistoryOrdersByTimeRange(MetatraderHistoryOrders expected) throws Exception {
        IsoTime startTime = new IsoTime(Date.from(Instant.ofEpochSecond(Instant.now().getEpochSecond() - 1000)));
        IsoTime endTime = new IsoTime(Date.from(Instant.now()));
        Mockito.when(client.getHistoryOrdersByTimeRange("accountId", startTime, endTime, 1, 100))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderHistoryOrders actual = api.getHistoryOrdersByTimeRange(startTime, endTime, 1, 100).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getDealsByTicket(String)}
     */
    @ParameterizedTest
    @MethodSource("provideDeals")
    void testRetrivesDealsByTicket(MetatraderDeals expected) throws Exception {
        Mockito.when(client.getDealsByTicket("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderDeals actual = api.getDealsByTicket("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getDealsByPosition(String)}
     */
    @ParameterizedTest
    @MethodSource("provideDeals")
    void testRetrivesDealsByPosition(MetatraderDeals expected) throws Exception {
        Mockito.when(client.getDealsByPosition("accountId", "46214692"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderDeals actual = api.getDealsByPosition("46214692").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getDealsByTimeRange(IsoTime, IsoTime, int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideDeals")
    void testRetrivesDealsByTimeRange(MetatraderDeals expected) throws Exception {
        IsoTime startTime = new IsoTime(Date.from(Instant.ofEpochSecond(Instant.now().getEpochSecond() - 1000)));
        IsoTime endTime = new IsoTime(Date.from(Instant.now()));
        Mockito.when(client.getDealsByTimeRange("accountId", startTime, endTime, 1, 100))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderDeals actual = api.getDealsByTimeRange(startTime, endTime, 1, 100).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#removeHistory()}
     */
    @Test
    void testRemovesHistory() throws Exception {
        Mockito.when(client.removeHistory("accountId")).thenReturn(CompletableFuture.completedFuture(null));
        api.removeHistory().get();
        Mockito.verify(client).removeHistory("accountId");
    }
    
    /**
     * Tests {@link MetaApiConnection#createMarketBuyOrder(String, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesMarketBuyOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.stopLoss = Optional.of(0.9);
        trade.takeProfit = Optional.of(2.0);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createMarketBuyOrder(
            trade.symbol.get(), trade.volume.get(), trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#createMarketSellOrder(String, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesMarketSellOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.stopLoss = Optional.of(2.0);
        trade.takeProfit = Optional.of(0.9);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createMarketSellOrder(
            trade.symbol.get(), trade.volume.get(), trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#createLimitBuyOrder(String, double, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesLimitBuyOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY_LIMIT;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.openPrice = Optional.of(1.0);
        trade.stopLoss = Optional.of(0.9);
        trade.takeProfit = Optional.of(2.0);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createLimitBuyOrder(
            trade.symbol.get(), trade.volume.get(), trade.openPrice.get(), 
            trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#createLimitSellOrder(String, double, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesLimitSellOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL_LIMIT;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.openPrice = Optional.of(1.0);
        trade.stopLoss = Optional.of(2.0);
        trade.takeProfit = Optional.of(0.9);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createLimitSellOrder(
            trade.symbol.get(), trade.volume.get(), trade.openPrice.get(), 
            trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#createStopBuyOrder(String, double, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesStopBuyOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_BUY_STOP;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.openPrice = Optional.of(1.5);
        trade.stopLoss = Optional.of(0.9);
        trade.takeProfit = Optional.of(2.0);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createStopBuyOrder(
            trade.symbol.get(), trade.volume.get(), trade.openPrice.get(), 
            trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#createStopSellOrder(String, double, double, Optional, Optional, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCreatesStopSellOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_TYPE_SELL_STOP;
        trade.symbol = Optional.of("GBPUSD");
        trade.volume = Optional.of(0.07);
        trade.openPrice = Optional.of(1.0);
        trade.stopLoss = Optional.of(2.0);
        trade.takeProfit = Optional.of(0.9);
        trade.comment = Optional.of("comment");
        trade.clientId = Optional.of("TE_GBPUSD_7hyINWqAlE");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.createStopSellOrder(
            trade.symbol.get(), trade.volume.get(), trade.openPrice.get(), 
            trade.stopLoss, trade.takeProfit, trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#modifyPosition(String, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradePositionResponse")
    void testModifiesPosition(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_MODIFY;
        trade.positionId = Optional.of("46870472");
        trade.stopLoss = Optional.of(2.0);
        trade.takeProfit = Optional.of(0.9);
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.modifyPosition(
            trade.positionId.get(), trade.stopLoss, trade.takeProfit
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#closePositionPartially(String, double, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradePositionResponse")
    void testClosesPositionPartially(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_PARTIAL;
        trade.positionId = Optional.of("46870472");
        trade.volume = Optional.of(0.9);
        trade.comment = Optional.empty();
        trade.clientId = Optional.empty();
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.closePositionPartially(
            trade.positionId.get(), trade.volume.get(), trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#closePosition(String, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradePositionResponse")
    void testClosesPosition(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_CLOSE_ID;
        trade.positionId = Optional.of("46870472");
        trade.comment = Optional.empty();
        trade.clientId = Optional.empty();
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.closePosition(trade.positionId.get(), trade.comment, trade.clientId).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#closePositionBySymbol(String, Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideTradePositionResponse")
    void testClosesPositionBySymbol(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.POSITION_CLOSE_SYMBOL;
        trade.symbol = Optional.of("EURUSD");
        trade.comment = Optional.empty();
        trade.clientId = Optional.empty();
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.closePositionBySymbol(
            trade.symbol.get(), trade.comment, trade.clientId
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#modifyOrder(String, double, double, double)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testModifiesOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_MODIFY;
        trade.orderId = Optional.of("46870472");
        trade.openPrice = Optional.of(1.0);
        trade.stopLoss = Optional.of(2.0);
        trade.takeProfit = Optional.of(0.9);
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.modifyOrder(
            trade.orderId.get(), trade.openPrice.get(), trade.stopLoss.get(), trade.takeProfit.get()
        ).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#cancelOrder(String)}
     */
    @ParameterizedTest
    @MethodSource("provideTradeOrderResponse")
    void testCancelsOrder(MetatraderTradeResponse expected) throws Exception {
        MetatraderTrade trade = new MetatraderTrade();
        trade.actionType = ActionType.ORDER_CANCEL;
        trade.orderId = Optional.of("46870472");
        Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderTradeResponse actual = api.cancelOrder(trade.orderId.get()).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
            assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
            return true;
        }));
    }
    
    /**
     * Tests {@link MetaApiConnection#reconnect()}
     */
    @Test
    void testReconnectsTerminal() throws Exception {
        Mockito.when(client.reconnect("accountId")).thenReturn(CompletableFuture.completedFuture(null));
        api.reconnect().get();
        Mockito.verify(client).reconnect("accountId");
    }
    
    /**
     * Tests {@link MetaApiConnection#subscribe()}
     */
    @Test
    void testSubscribesToTerminal() throws Exception {
        Mockito.when(client.subscribe("accountId")).thenReturn(CompletableFuture.completedFuture(null));
        api.subscribe().get();
        Mockito.verify(client).subscribe("accountId");
    }

    /**
     * Tests {@link MetaApiConnection#subscribeToMarketData(String)}
     */
    @Test
    void testSubscribesToMarketData() throws Exception {
        Mockito.when(client.subscribeToMarketData("accountId", "EURUSD"))
            .thenReturn(CompletableFuture.completedFuture(null));
        api.subscribeToMarketData("EURUSD").get();
        Mockito.verify(client).subscribeToMarketData("accountId", "EURUSD");
    }
    
    /**
     * Tests {@link MetaApiConnection#getSymbolSpecification(String)}
     */
    @Test
    void testRetrievesSymbolSpecification() throws Exception {
        MetatraderSymbolSpecification expected = new MetatraderSymbolSpecification();
        expected.symbol = "AUDNZD";
        expected.tickSize = 0.00001;
        expected.minVolume = 0.01;
        expected.maxVolume = 100;
        expected.volumeStep = 0.01;
        Mockito.when(client.getSymbolSpecification("accountId", "AUDNZD"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderSymbolSpecification actual = api.getSymbolSpecification("AUDNZD").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetaApiConnection#getSymbolPrice(String)}
     */
    @Test
    void testRetrievesSymbolPrice() throws Exception {
        MetatraderSymbolPrice expected = new MetatraderSymbolPrice();
        expected.symbol = "AUDNZD";
        expected.bid = 1.05297;
        expected.ask = 1.05309;
        expected.profitTickValue = 0.59731;
        expected.lossTickValue = 0.59736;
        Mockito.when(client.getSymbolPrice("accountId", "AUDNZD"))
            .thenReturn(CompletableFuture.completedFuture(expected));
        MetatraderSymbolPrice actual = api.getSymbolPrice("AUDNZD").get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    /**
     * Tests {@link MetaApiConnection#addSynchronizationListener(SynchronizationListener)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoWithUserSynchronizationMode")
    void testAddsSynchronizationListenersForAccountWithUserSynchronizationMode(
        MetatraderAccountDto userSyncModeAccount
    ) throws Exception {
        MetaApiConnection api = new MetaApiConnection(client, new MetatraderAccount(
            userSyncModeAccount, Mockito.mock(MetatraderAccountClient.class), client));
        SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
        api.addSynchronizationListener(listener);
        Mockito.verify(client).addSynchronizationListener("accountId", listener);
    }

    /**
     * Tests {@link MetaApiConnection#close()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoWithUserSynchronizationMode")
    void testUnsubscribesFromEventsOnClose(
        MetatraderAccountDto userSyncModeAccount
    ) throws Exception {
        MetaApiConnection api = new MetaApiConnection(client, new MetatraderAccount(
            userSyncModeAccount, Mockito.mock(MetatraderAccountClient.class), client));
        api.close();
        Mockito.verify(client).removeSynchronizationListener("accountId", api);
    }
    
    /**
     * Tests {@link MetaApiConnection#waitSynchronized(int, int)}
     */
    @Test
    void testWaitsUntilSynchronizationCompleteInUserMode() throws Exception {
        assertFalse(api.isSynchronized().get());
        CompletableFuture<Void> future = api.waitSynchronized(Integer.MAX_VALUE, 10);
        Thread.sleep(15);
        api.onDealSynchronizationFinished();
        future.get();
        assertTrue(api.isSynchronized().get());
    }

    /**
     * Tests {@link MetaApiConnection#waitSynchronized(int, int)}
     */
    @Test
    void testTimesOutWatingForSynchronizationCompleteInUserMode() throws Exception {
        assertThrows(TimeoutException.class, () -> {
            try {
                api.waitSynchronized(1, 10).get();
                throw new Exception("TimeoutException is expected");
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
        assertFalse(api.isSynchronized().get());
    }
    
    /**
     * Tests {@link MetaApiConnection#onReconnected()}
     */
    @Test
    void testSubscribesToTerminalOnReconnect() throws Throwable {
        Mockito.when(client.subscribe("accountId")).thenReturn(CompletableFuture.completedFuture(null));
        api.onReconnected().get();
        Mockito.verify(client).subscribe("accountId");
    }
    
    private static Stream<Arguments> providePosition() {
        MetatraderPosition position = new MetatraderPosition();
        position.id = "46214692";
        position.type = PositionType.POSITION_TYPE_BUY;
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
        return Stream.of(Arguments.of(position));
    }
    
    private static Stream<Arguments> provideOrder() {
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
        return Stream.of(Arguments.of(order));
    }
    
    private static Stream<Arguments> provideHistoryOrders() {
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
        MetatraderHistoryOrders history = new MetatraderHistoryOrders();
        history.historyOrders = List.of(order);
        history.synchronizing = false;
        return Stream.of(Arguments.of(history));
    }
    
    private static Stream<Arguments> provideDeals() {
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
        MetatraderDeals deals = new MetatraderDeals();
        deals.deals = List.of(deal);
        deals.synchronizing = false;
        return Stream.of(Arguments.of(deals));
    }
    
    private static Stream<Arguments> provideTradeOrderResponse() {
        MetatraderTradeResponse result = new MetatraderTradeResponse();
        result.error = 10009;
        result.description = "TRADE_RETCODE_DONE";
        result.orderId = Optional.of("46870472");
        return Stream.of(Arguments.of(result));
    }
    
    private static Stream<Arguments> provideTradePositionResponse() {
        MetatraderTradeResponse result = new MetatraderTradeResponse();
        result.error = 10009;
        result.description = "TRADE_RETCODE_DONE";
        result.positionId = Optional.of("46870472");
        return Stream.of(Arguments.of(result));
    }
    
    private static Stream<Arguments> provideAccountDtoWithUserSynchronizationMode() {
        MetatraderAccountDto accountDto = new MetatraderAccountDto();
        accountDto._id = "accountId";
        accountDto.synchronizationMode = "user";
        return Stream.of(Arguments.of(accountDto));
    }
}