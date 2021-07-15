package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataSubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketDataUnsubscription;
import cloud.metaapi.sdk.clients.meta_api.models.MarketTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;
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
import cloud.metaapi.sdk.clients.meta_api.models.PendingTradeOptions;
import cloud.metaapi.sdk.clients.meta_api.models.StopOptions;
import cloud.metaapi.sdk.clients.meta_api.models.StopOptions.StopUnits;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal.DealEntryType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal.DealType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder.OrderState;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder.OrderType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition.PositionType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTrade.ActionType;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.meta_api.HistoryFileManager.History;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link MetaApiConnection}
 */
class MetaApiConnectionTest {

  private MetaApiConnection api;
  private MetatraderAccount account;
  private MetaApiWebsocketClient client;
  private HistoryStorage storageMock;
  private ConnectionRegistry connectionRegistry;
  private TerminalState apiTerminalState;
  
  @BeforeEach
  void setUp() throws Exception {
    account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    Mockito.when(account.getState()).thenReturn(DeploymentState.DEPLOYED);
    HistoryFileManager historyFileManagerMock = Mockito.mock(HistoryFileManager.class);
    Mockito.when(historyFileManagerMock.getHistoryFromDisk()).thenReturn(CompletableFuture.completedFuture(
      new History() {{ deals = Lists.list(); historyOrders = Lists.list(); }}));
    ServiceProvider.setHistoryFileManagerMock(historyFileManagerMock);
    
    client = Mockito.mock(MetaApiWebsocketClient.class);
    Mockito.when(client.synchronize(Mockito.anyString(), Mockito.anyInt(), Mockito.any(),
      Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(true));
    
    storageMock = Mockito.mock(HistoryStorage.class);
    connectionRegistry = Mockito.mock(ConnectionRegistry.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(connectionRegistry.getApplication()).thenReturn("MetaApi");
    api = new MetaApiConnection(client, account, storageMock, connectionRegistry);
    
    apiTerminalState = (TerminalState) FieldUtils.readField(api, "terminalState", true);
    apiTerminalState = Mockito.spy(apiTerminalState);
    FieldUtils.writeField(api, "terminalState", apiTerminalState, true);
  }
  
  @AfterEach
  void tearDown() {
    ServiceProvider.reset();
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
    expected.marginLevel = 3967.58283542;
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
    List<MetatraderPosition> expected = Lists.list(position);
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
    List<MetatraderOrder> expected = Lists.list(order);
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
    Mockito.when(client.removeHistory("accountId", "app"))
      .thenReturn(CompletableFuture.completedFuture(null));
    api.removeHistory("app").get();
    Mockito.verify(client).removeHistory("accountId", "app");
    Mockito.verify(storageMock).clear();
  }
  
  /**
   * Tests {@link MetaApiConnection#removeApplication()}
   */
  @Test
  void testRemovesApplication() throws Exception {
    Mockito.when(client.removeApplication("accountId")).thenReturn(CompletableFuture.completedFuture(null));
    api.removeApplication().get();
    Mockito.verify(client).removeApplication("accountId");
    Mockito.verify(storageMock).clear();
  }
  
  /**
   * Tests {@link MetaApiConnection#createMarketBuyOrder}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesMarketBuyOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.stopLoss = 0.9;
    trade.takeProfit = 2.0;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createMarketBuyOrder(
      trade.symbol, trade.volume, trade.stopLoss, trade.takeProfit, new MarketTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createMarketBuyOrder}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesMarketBuyOrderWithRelativeSLTP(MetatraderTradeResponse expected) {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.stopLoss = 0.1;
    trade.stopLossUnits = StopUnits.RELATIVE_PRICE;
    trade.takeProfit = 2000.0;
    trade.takeProfitUnits = StopUnits.RELATIVE_POINTS;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createMarketBuyOrder(trade.symbol, trade.volume,
        new StopOptions() {{value = 0.1; units = StopUnits.RELATIVE_PRICE;}},
        new StopOptions() {{value = 2000.0; units = StopUnits.RELATIVE_POINTS;}},
        new MarketTradeOptions() {{comment = trade.comment; clientId = trade.clientId;}}
    ).join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createMarketSellOrder(String, double, Double, Double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesMarketSellOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.stopLoss = 2.0;
    trade.takeProfit = 0.9;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createMarketSellOrder(
      trade.symbol, trade.volume, trade.stopLoss, trade.takeProfit, new MarketTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createLimitBuyOrder(String, double, double, Double, Double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesLimitBuyOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY_LIMIT;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.openPrice = 1.0;
    trade.stopLoss = 0.9;
    trade.takeProfit = 2.0;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createLimitBuyOrder(
      trade.symbol, trade.volume, trade.openPrice, trade.stopLoss, trade.takeProfit, new PendingTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createLimitSellOrder(String, double, double, Double, Double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesLimitSellOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL_LIMIT;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.openPrice = 1.0;
    trade.stopLoss = 2.0;
    trade.takeProfit = 0.9;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createLimitSellOrder(
      trade.symbol, trade.volume, trade.openPrice, trade.stopLoss, trade.takeProfit, new PendingTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createStopBuyOrder(String, double, double, Double, Double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesStopBuyOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_BUY_STOP;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.openPrice = 1.5;
    trade.stopLoss = 0.9;
    trade.takeProfit = 2.0;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createStopBuyOrder(
      trade.symbol, trade.volume, trade.openPrice, trade.stopLoss, trade.takeProfit, new PendingTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createStopSellOrder(String, double, double, Double, Double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradeOrderResponse")
  void testCreatesStopSellOrder(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.ORDER_TYPE_SELL_STOP;
    trade.symbol = "GBPUSD";
    trade.volume = 0.07;
    trade.openPrice = 1.0;
    trade.stopLoss = 2.0;
    trade.takeProfit = 0.9;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.createStopSellOrder(
      trade.symbol, trade.volume, trade.openPrice, trade.stopLoss, trade.takeProfit, new PendingTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createStopLimitBuyOrder(String, double, double, double, Double, Double, PendingTradeOptions)}
   */
  @Test
  void testCreatesStopLimitBuyOrder() {
    MetatraderTradeResponse tradeResult = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      orderId = "46870472";
    }};
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(tradeResult));
    MetatraderTradeResponse actual = api.createStopLimitBuyOrder("GBPUSD", 0.07, 1.5, 1.4, 0.9, 2.0,
      new PendingTradeOptions() {{ comment = "comment"; clientId = "TE_GBPUSD_7hyINWqAlE"; }}).join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(tradeResult);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(new MetatraderTrade() {{
        actionType = ActionType.ORDER_TYPE_BUY_STOP_LIMIT;
        symbol = "GBPUSD";
        volume = 0.07;
        openPrice = 1.5;
        stopLimitPrice = 1.4;
        stopLoss = 0.9;
        takeProfit = 2.0;
        comment = "comment";
        clientId = "TE_GBPUSD_7hyINWqAlE";
      }});
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#createStopLimitSellOrder(String, double, double, double, Double, Double, PendingTradeOptions)}
   */
  @Test
  void testCreatesStopLimitSellOrder() {
    MetatraderTradeResponse tradeResult = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      orderId = "46870472";
    }};
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(tradeResult));
    MetatraderTradeResponse actual = api.createStopLimitSellOrder("GBPUSD", 0.07, 1.0, 1.1, 2.0, 0.9,
      new PendingTradeOptions() {{ comment = "comment"; clientId = "TE_GBPUSD_7hyINWqAlE"; }}).join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(tradeResult);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(new MetatraderTrade() {{
        actionType = ActionType.ORDER_TYPE_SELL_STOP_LIMIT;
        symbol = "GBPUSD";
        volume = 0.07;
        openPrice = 1.0;
        stopLimitPrice = 1.1;
        stopLoss = 2.0;
        takeProfit = 0.9;
        comment = "comment";
        clientId = "TE_GBPUSD_7hyINWqAlE";
      }});
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#modifyPosition(String, Double, Double)}
   */
  @ParameterizedTest
  @MethodSource("provideTradePositionResponse")
  void testModifiesPosition(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_MODIFY;
    trade.positionId = "46870472";
    trade.stopLoss = 2.0;
    trade.takeProfit = 0.9;
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.modifyPosition(trade.positionId, trade.stopLoss, trade.takeProfit).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#closePositionPartially(String, double, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradePositionResponse")
  void testClosesPositionPartially(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_PARTIAL;
    trade.positionId = "46870472";
    trade.volume = 0.9;
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.closePositionPartially(
      trade.positionId, trade.volume, new MarketTradeOptions() {{
        comment = trade.comment; clientId = trade.clientId;
      }}
    ).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#closePosition(String, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradePositionResponse")
  void testClosesPosition(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_CLOSE_ID;
    trade.positionId = "46870472";
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.closePosition(trade.positionId, new MarketTradeOptions() {{
      comment = trade.comment; clientId = trade.clientId;
    }}).get();
    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#closeBy(String, String, MarketTradeOptions)}
   */
  @Test
  void testClosesPositionByAnOppositeOne() {
    MetatraderTradeResponse tradeResult = new MetatraderTradeResponse() {{
      numericCode = 10009;
      stringCode = "TRADE_RETCODE_DONE";
      positionId = "46870472";
      closeByPositionId = "46870482";
    }};
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(tradeResult));
    MetatraderTradeResponse actual = api.closeBy("46870472", "46870482", new MarketTradeOptions() {{
      comment = "comment"; clientId = "TE_GBPUSD_7hyINWqAlE";
    }}).join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(tradeResult);
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITION_CLOSE_BY;
    trade.positionId = "46870472";
    trade.closeByPositionId = "46870482";
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.verify(client).trade(Mockito.eq("accountId"), Mockito.argThat(arg -> {
      assertThat(arg).usingRecursiveComparison().isEqualTo(trade);
      return true;
    }));
  }
  
  /**
   * Tests {@link MetaApiConnection#closePositionsBySymbol(String, TradeOptions)}
   */
  @ParameterizedTest
  @MethodSource("provideTradePositionResponse")
  void testClosesPositionsBySymbol(MetatraderTradeResponse expected) throws Exception {
    MetatraderTrade trade = new MetatraderTrade();
    trade.actionType = ActionType.POSITIONS_CLOSE_SYMBOL;
    trade.symbol = "EURUSD";
    trade.comment = "comment";
    trade.clientId = "TE_GBPUSD_7hyINWqAlE";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.closePositionsBySymbol(trade.symbol, new MarketTradeOptions() {{
      comment = trade.comment; clientId = trade.clientId;
    }}).get();
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
    trade.orderId = "46870472";
    trade.openPrice = 1.0;
    trade.stopLoss = 2.0;
    trade.takeProfit = 0.9;
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.modifyOrder(
      trade.orderId, trade.openPrice, trade.stopLoss, trade.takeProfit
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
    trade.orderId = "46870472";
    Mockito.when(client.trade(Mockito.eq("accountId"), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(expected));
    MetatraderTradeResponse actual = api.cancelOrder(trade.orderId).get();
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
    Mockito.when(client.ensureSubscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    api.subscribe().join();
    Mockito.verify(client).ensureSubscribe("accountId", null);
  }
  
  /**
   * Tests {@link MetaApiConnection#synchronize}
   */
  @Test
  void testDoesNotSubscribeIfConnectionIsClosed() {
    Mockito.when(client.ensureSubscribe(Mockito.anyString(), Mockito.anyInt())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.unsubscribe(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    api.close().join();
    api.subscribe().join();
    Mockito.verify(client, Mockito.never()).ensureSubscribe(Mockito.anyString(), Mockito.anyInt());
  };
  
  /**
   * Tests {@link MetaApiConnection#synchronize()}
   */
  @Test
  void testSynchronizesStateWithTerminal() {
    IsoTime startingHistoryOrderTime = new IsoTime("2020-01-01T00:00:00.000Z");
    IsoTime startingDealTime = new IsoTime("2020-01-02T00:00:00.000Z");
    Mockito.when(client.synchronize(Mockito.eq("accountId"), Mockito.eq(1),
      Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    api.getHistoryStorage().onHistoryOrderAdded("1:ps-mpa-1", new MetatraderOrder() {{
      doneTime = startingHistoryOrderTime;
    }});
    api.getHistoryStorage().onDealAdded("1:ps-mpa-1", new MetatraderDeal() {{
      time = startingDealTime;
    }});
    api.synchronize("1:ps-mpa-1").join();
    Mockito.verify(client).synchronize(Mockito.eq("accountId"), Mockito.eq(1),
      Mockito.eq("ps-mpa-1"), Mockito.anyString(), Mockito.eq(startingHistoryOrderTime),
      Mockito.eq(startingDealTime));
  }
  
  /**
   * Tests {@link MetaApiConnection#synchronize()}
   */
  @Test
  void testSynchronizesStateWithTerminalFromSpecifiedTime() {
    IsoTime historyStartTime = new IsoTime("2020-10-07T00:00:00.000Z");
    Mockito.when(client.synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.anyString(),
      Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry, historyStartTime);
    api.getHistoryStorage().onHistoryOrderAdded("1:ps-mpa-1", new MetatraderOrder() {{
      doneTime = new IsoTime("2020-01-01T00:00:00.000Z");
    }});
    api.getHistoryStorage().onDealAdded("1:ps-mpa-1", new MetatraderDeal() {{
      time = new IsoTime("2020-01-02T00:00:00.000Z");
    }});
    api.synchronize("1:ps-mpa-1").join();
    Mockito.verify(client).synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.eq("ps-mpa-1"),
      Mockito.anyString(), Mockito.eq(historyStartTime), Mockito.eq(historyStartTime));
  }
  
  /**
   * Tests {@link MetaApiConnection#subscribeToMarketData(String)}
   */
  @Test
  void testSubscribesToMarketData() throws Exception {
    Mockito.when(client.subscribeToMarketData(Mockito.anyString(), Mockito.anyInt(),
      Mockito.anyString(), Mockito.anyList())).thenReturn(CompletableFuture.completedFuture(null));
    List<MarketDataSubscription> subscriptions = Arrays.asList(new MarketDataSubscription() {{
      type = "quotes";
    }});
    api.subscribeToMarketData("EURUSD", subscriptions, 1).get();
    Mockito.verify(client).subscribeToMarketData("accountId", 1, "EURUSD", subscriptions);
  }
  
  /**
   * Tests {@link MetaApiConnection#unsubscribeFromMarketData(String)}
   */
  @Test
  void testUnsubscribesFromMarketData() throws Exception {
    Mockito.when(client.unsubscribeFromMarketData(Mockito.anyString(), Mockito.anyInt(),
      Mockito.anyString(), Mockito.anyList())).thenReturn(CompletableFuture.completedFuture(null));
    List<MarketDataUnsubscription> subscriptions = Arrays.asList(new MarketDataUnsubscription() {{
      type = "quotes";
    }});
    api.unsubscribeFromMarketData("EURUSD", subscriptions, 1).get();
    Mockito.verify(client).unsubscribeFromMarketData("accountId", 1, "EURUSD", subscriptions);
  }
  
  /**
   * Tests {@link MetaApiConnection#onSubscriptionDowngraded(int, String, List, List)}
   */
  @Test
  void testUnsubscribesDuringMarketDataSubscriptionDowngrade() {
    MetaApiConnection api = Mockito.spy(this.api);
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(api)
      .subscribeToMarketData(Mockito.anyString(), Mockito.anyList());
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(api)
      .unsubscribeFromMarketData(Mockito.anyString(), Mockito.anyList());
    List<MarketDataUnsubscription> unsubscriptions = Arrays.asList(new MarketDataUnsubscription() {{ type = "ticks"; }});
    api.onSubscriptionDowngraded("1:ps-mpa-1", "EURUSD", new ArrayList<>(), unsubscriptions).join();
    Mockito.verify(api).unsubscribeFromMarketData("EURUSD", unsubscriptions);
    Mockito.verify(api, Mockito.never()).subscribeToMarketData(Mockito.anyString(), Mockito.anyList());
  }
  
  /**
   * Tests {@link MetaApiConnection#onSubscriptionDowngraded(int, String, List, List)}
   */
  @Test
  void testUpdateMarketDataSubscriptionOnDowngrade() {
    MetaApiConnection api = Mockito.spy(this.api);
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(api)
      .subscribeToMarketData(Mockito.anyString(), Mockito.anyList());
    Mockito.doReturn(CompletableFuture.completedFuture(null)).when(api)
      .unsubscribeFromMarketData(Mockito.anyString(), Mockito.anyList());
    List<MarketDataSubscription> updates = Arrays.asList(new MarketDataSubscription() {{
      type = "quotes";
      intervalInMilliseconds = 30000;
    }});
    api.onSubscriptionDowngraded("1:ps-mpa-1", "EURUSD", updates, new ArrayList<>()).join();
    Mockito.verify(api).subscribeToMarketData("EURUSD", updates);
    Mockito.verify(api, Mockito.never()).unsubscribeFromMarketData(Mockito.anyString(), Mockito.anyList());
  }
  
  /**
   * Tests {@link MetaApiConnection#getSymbols}
   */
  @Test
  void testRetrievesSymbols() {
    List<String> symbols = Arrays.asList("EURUSD");
    Mockito.when(client.getSymbols("accountId"))
      .thenReturn(CompletableFuture.completedFuture(symbols));
    List<String> actual = api.getSymbols().join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(symbols);
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
   * Tests {@link MetaApiConnection#getCandle(String, String)}
   */
  @Test
  void testRetrievesCurrentCandle() {
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
    Mockito.when(client.getCandle("accountId", "AUDNZD", "15m"))
      .thenReturn(CompletableFuture.completedFuture(candle));
    MetatraderCandle actual = api.getCandle("AUDNZD", "15m").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(candle);
  }
  
  /**
   * Tests {@link MetaApiConnection#getTick(String)}
   */
  @Test
  void testRetrievesLatestTick() {
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
    Mockito.when(client.getTick("accountId", "AUDNZD")).thenReturn(CompletableFuture.completedFuture(tick));
    MetatraderTick actual = api.getTick("AUDNZD").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(tick);
  }
  
  /**
   * Tests {@link MetaApiConnection#getBook(String)}
   */
  @Test
  void testRetrievesLatestOrderBook() {
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
    Mockito.when(client.getBook("accountId", "AUDNZD")).thenReturn(CompletableFuture.completedFuture(book));
    MetatraderBook actual = api.getBook("AUDNZD").join();
    assertThat(actual).usingRecursiveComparison().isEqualTo(book);
  }
  
  /**
   * Tests {@link MetaApiConnection#saveUptime(Map)}
   */
  @Test
  void testSavesUptimeStatsToTheServer() {
    Mockito.when(client.saveUptime(Mockito.eq("accountId"), Mockito.anyMap()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Map<String, Double> uptime = Maps.newHashMap("1h", 100.0); 
    api.saveUptime(uptime).join();
    Mockito.verify(client).saveUptime("accountId", uptime);
  }
  
  /**
   * Tests {@link MetaApiConnection#getTerminalState()}
   * Tests {@link MetaApiConnection#getHistoryStorage()}
   */
  @Test
  void testInitializesListenersAndTerminalStateAndHistoryStorageForAccountsWithUserSynchronizationMode() {
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    assertNotNull(api.getTerminalState());
    assertNotNull(api.getHistoryStorage());
    Mockito.verify(client).addSynchronizationListener("accountId", api);
    Mockito.verify(client).addSynchronizationListener("accountId", api.getTerminalState());
    Mockito.verify(client).addSynchronizationListener("accountId", api.getHistoryStorage());
  }
  
  /**
   * Tests {@link MetaApiConnection#addSynchronizationListener(SynchronizationListener)}
   */
  @Test
  void testAddsSynchronizationListeners() {
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    api.addSynchronizationListener(listener);
    Mockito.verify(client).addSynchronizationListener("accountId", listener);
  }
  
  /**
   * Tests {@link MetaApiConnection#removeSynchronizationListener(SynchronizationListener)}
   */
  @Test
  void testRemovesSynchronizationListeners() {
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    SynchronizationListener listener = Mockito.mock(SynchronizationListener.class);
    api.removeSynchronizationListener(listener);
    Mockito.verify(client).removeSynchronizationListener("accountId", listener);
  }
  
  /**
   * Tests {@link MetaApiConnection#onConnected()}
   */
  @Test
  void testSynchronizesOnConnection() throws Exception {
    IsoTime startingHistoryOrderTime = new IsoTime("2020-01-01T00:00:00.000Z");
    IsoTime startingDealTime = new IsoTime("2020-01-02T00:00:00.000Z");
    Mockito.when(client.synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.anyString(),
      Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    api.getHistoryStorage().onHistoryOrderAdded("1:ps-mpa-1", new MetatraderOrder() {{
      doneTime = startingHistoryOrderTime;
    }});
    api.getHistoryStorage().onDealAdded("1:ps-mpa-1", new MetatraderDeal() {{
      time = startingDealTime;
    }});
    api.onConnected("1:ps-mpa-1", 1).get();
    Thread.sleep(50);
    Mockito.verify(client).synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.eq("ps-mpa-1"),
      Mockito.anyString(), Mockito.eq(startingHistoryOrderTime), Mockito.eq(startingDealTime));
  }
  
  /**
   * Tests {@link MetaApiConnection#onConnected()}
   */
  @Test
  void testMaintainsSynchronizationIfConnectionHasFailed() throws InterruptedException {
    IsoTime startingHistoryOrderTime = new IsoTime("2020-01-01T00:00:00.000Z");
    IsoTime startingDealTime = new IsoTime("2020-01-02T00:00:00.000Z");
    Mockito.when(client.synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.anyString(),
      Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenThrow(new CompletionException(new Exception("test error")))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    api.getHistoryStorage().onHistoryOrderAdded("1:ps-mpa-1", new MetatraderOrder() {{
      doneTime = startingHistoryOrderTime;
    }});
    api.getHistoryStorage().onDealAdded("1:ps-mpa-1", new MetatraderDeal() {{
      time = startingDealTime;
    }});
    api.onConnected("1:ps-mpa-1", 1).join();
    Thread.sleep(50);
    Mockito.verify(client).synchronize(Mockito.eq("accountId"), Mockito.eq(1), Mockito.eq("ps-mpa-1"),
      Mockito.anyString(), Mockito.eq(startingHistoryOrderTime), Mockito.eq(startingDealTime));
  }
  
  /**
   * Tests {@link MetaApiConnection#onConnected}
   */
  @Test
  void testDoesNotSynchronizeIfConnectionIsClosed() {
    Mockito.when(client.synchronize(Mockito.anyString(), Mockito.anyInt(),
      Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.unsubscribe(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(null));
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    api = new MetaApiConnection(client, account, null, connectionRegistry);
    api.getHistoryStorage().onHistoryOrderAdded("1:ps-mpa-1", new MetatraderOrder() {{
      doneTime = new IsoTime("2020-01-01T00:00:00.000Z");
    }}).join();
    api.getHistoryStorage().onDealAdded("1:ps-mpa-1", new MetatraderDeal() {{
      time = new IsoTime("2020-01-02T00:00:00.000Z");
    }}).join();
    api.close().join();
    api.onConnected("1:ps-mpa-1", 1).join();
    Mockito.verify(client, Mockito.never()).synchronize(Mockito.anyString(), Mockito.anyInt(),
      Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any());
  };
  
  /**
   * Tests {@link MetaApiConnection#onConnected}
   */
  @Test
  void testRestoresMarketDataSubscriptionsOnSynchronization() {
    Mockito.doReturn(Optional.of(new MetatraderSymbolPrice() {{symbol = "EURUSD";}}))
      .when(apiTerminalState).getPrice("EURUSD");
    Mockito.doReturn(Optional.ofNullable(null)).when(apiTerminalState).getPrice("AUDNZD");
    Mockito.when(client.subscribeToMarketData(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(),
      Mockito.anyList())).thenReturn(CompletableFuture.completedFuture(null));
    api.subscribeToMarketData("EURUSD").join();
    api.subscribeToMarketData("AUDNZD").join();
    api.onAccountInformationUpdated("1:ps-mpa-1", new MetatraderAccountInformation()).join();
    Mockito.verify(client, Mockito.times(1)).subscribeToMarketData(Mockito.eq("accountId"),
      Mockito.eq(1), Mockito.eq("AUDNZD"), Mockito.anyList());
  };
  
  /**
   * Tests {@link MetaApiConnection#close()}
   */
  @Test
  void testUnsubscribesFromEventsOnClose() {
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("accountId");
    Mockito.when(client.unsubscribe(Mockito.anyString()))
      .thenReturn(CompletableFuture.completedFuture(null));
    MetaApiConnection api = new MetaApiConnection(client, account, null, connectionRegistry);
    api.close().join();
    Mockito.verify(client).unsubscribe("accountId");
    Mockito.verify(client).removeSynchronizationListener("accountId", api);
    Mockito.verify(client).removeSynchronizationListener("accountId", api.getTerminalState());
    Mockito.verify(client).removeSynchronizationListener("accountId", api.getHistoryStorage());
  }
  
  /**
   * Tests {@link MetaApiConnection#waitSynchronized(SynchronizationOptions)}
   */
  @Test
  void testWaitsUntilSynchronizationComplete() throws Exception {
    Mockito.when(client.waitSynchronized(Mockito.anyString(), Mockito.eq(1), Mockito.anyString(), Mockito.anyLong()))
      .thenReturn(CompletableFuture.completedFuture(null));
    assertFalse(api.isSynchronized("1:ps-mpa-1", "synchronizationId").join());
    CompletableFuture<Void> future = api.waitSynchronized(new SynchronizationOptions() {{
      applicationPattern = "app.*";
      synchronizationId = "synchronizationId";
      timeoutInSeconds = 1;
      intervalInMilliseconds = 10;
    }});
    Thread.sleep(15);
    api.onOrderSynchronizationFinished("1:ps-mpa-1", "synchronizationId");
    api.onDealSynchronizationFinished("1:ps-mpa-1", "synchronizationId");
    future.join();
    assertTrue(api.isSynchronized("1:ps-mpa-1", "synchronizationId").join());
  }

  /**
   * Tests {@link MetaApiConnection#waitSynchronized(String, Integer, Integer)}
   */
  @Test
  void testTimesOutWatingForSynchronizationComplete() {
    assertThrows(TimeoutException.class, () -> {
      try {
        api.waitSynchronized(new SynchronizationOptions() {{
          applicationPattern = "app.*";
          synchronizationId = "synchronizationId";
          timeoutInSeconds = 1;
          intervalInMilliseconds = 10;
        }}).get();
        throw new Exception("TimeoutException is expected");
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    });
    assertFalse(api.isSynchronized("1:ps-mpa-1", "synchronizationId").join());
  }
  
  /**
   * Tests {@link MetaApiConnection#initialize()}
   */
  @Test
  void testLoadsDataToHistoryStorageFromDisk() throws Exception {
    Mockito.when(storageMock.initialize()).thenReturn(CompletableFuture.completedFuture(null));
    api.initialize().get();
    Mockito.verify(storageMock).initialize();
  }
  
  /**
   * Tests {@link MetaApiConnection#onDisconnected(int)}
   */
  @Test
  void testSetsSynchronizedFalseOnDisconnect() throws InterruptedException {
    Mockito.when(storageMock.getLastHistoryOrderTime(Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(new IsoTime()));
    Mockito.when(storageMock.getLastDealTime(Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(new IsoTime()));
    
    api.onConnected("1:ps-mpa-1", 2).join();
    Thread.sleep(50);
    assertTrue(api.isSynchronized());
    api.onDisconnected("1:ps-mpa-1").join();
    assertFalse(api.isSynchronized());
  }
  
  /**
   * Tests {@link MetaApiConnection#onDisconnected}
   */
  @Test
  void testDeletesStateIfStreamClosed() throws InterruptedException {
    Mockito.when(storageMock.getLastHistoryOrderTime(Mockito.anyInt()))
      .thenReturn(CompletableFuture.completedFuture(new IsoTime()));
    Mockito.when(storageMock.getLastDealTime(Mockito.anyInt()))
    .thenReturn(CompletableFuture.completedFuture(new IsoTime()));
    
    api.onConnected("1:ps-mpa-1", 2).join();
    Thread.sleep(50);
    assertTrue(api.isSynchronized());
    api.onStreamClosed("1:ps-mpa-1").join();
    assertFalse(api.isSynchronized());
  };
  
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
    position.swap = 0.0;
    position.profit = -85.25999999999966;
    position.commission = -0.25;
    position.clientId = "TE_GBPUSD_7hyINWqAlE";
    position.stopLoss = 1.17721;
    position.unrealizedProfit = -85.25999999999901;
    position.realizedProfit = -6.536993168992922e-13;
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
    order.openPrice = 1.03;
    order.currentPrice = 1.05206;
    order.volume = 0.01;
    order.currentVolume = 0.01;
    order.comment = "COMMENT2";
    return Stream.of(Arguments.of(order));
  }
  
  private static Stream<Arguments> provideHistoryOrders() {
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
  
  private static Stream<Arguments> provideDeals() {
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
  
  private static Stream<Arguments> provideTradeOrderResponse() {
    MetatraderTradeResponse result = new MetatraderTradeResponse();
    result.numericCode = 10009;
    result.stringCode = "TRADE_RETCODE_DONE";
    result.orderId = "46870472";
    return Stream.of(Arguments.of(result));
  }
  
  private static Stream<Arguments> provideTradePositionResponse() {
    MetatraderTradeResponse result = new MetatraderTradeResponse();
    result.numericCode = 10009;
    result.stringCode = "TRADE_RETCODE_DONE";
    result.positionId = "46870472";
    return Stream.of(Arguments.of(result));
  }
}
