package cloud.metaapi.sdk.meta_api;

import static org.junit.jupiter.api.Assertions.*;

import java.text.SimpleDateFormat;
import java.util.Optional;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Arrays;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.meta_api.models.ConnectionHealthStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSession;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSessions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link ConnectionHealthMonitor}
 */
class ConnectionHealthMonitorTest {

  private ConnectionHealthMonitor healthMonitor;
  private MetatraderSymbolPrice[] prices;
  private MetaApiConnection connection;
  private String[] brokerTimes = { "2020-10-05 09:00:00.000", "2020-10-10 10:00:00.000" };
  private SimpleDateFormat brokerTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  
  @BeforeAll
  static void setUpBeforeClass() {
    ServiceProvider.setRandom(0.0);
  }
  
  @AfterAll
  static void tearDownAfterClass() {
    ServiceProvider.setRandom(null);
  }
  
  @BeforeEach
  void setUp() throws Exception {
    ServiceProvider.setNowInstantMock(brokerTimeFormatter.parse("2020-10-05 10:00:00.000").toInstant());
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("id");
    MetatraderSymbolSpecification symbolSpec = new MetatraderSymbolSpecification() {{
      quoteSessions = new MetatraderSessions() {{
        MONDAY = Lists.list(new MetatraderSession() {{
          from = "08:00:00.000";
          to = "17:00:00.000";
        }});
      }};
    }};
    TerminalState terminalState = Mockito.mock(TerminalState.class);
    Mockito.when(terminalState.getSpecification(Mockito.anyString())).thenReturn(Optional.of(symbolSpec));
    Mockito.when(terminalState.isConnected()).thenReturn(true);
    Mockito.when(terminalState.isConnectedToBroker()).thenReturn(true);
    connection = Mockito.mock(MetaApiConnection.class);
    Mockito.when(connection.getAccount()).thenReturn(account);
    Mockito.when(connection.getSubscribedSymbols()).thenReturn(Lists.list("EURUSD"));
    Mockito.when(connection.getTerminalState()).thenReturn(terminalState);
    Mockito.when(connection.isSynchronized()).thenReturn(true);
    ConnectionHealthMonitor.minMeasureInterval = 250;
    ConnectionHealthMonitor.minQuoteInterval = 15000;
    healthMonitor = new ConnectionHealthMonitor(connection);
    prices = Arrays.array(new MetatraderSymbolPrice() {{
      symbol = "EURUSD";
      brokerTime = brokerTimes[0];
    }}, new MetatraderSymbolPrice() {{
      symbol = "EURUSD";
      brokerTime = brokerTimes[1];
    }});
  }
  
  @AfterEach
  void tearDown() {
    ServiceProvider.reset();
  }

  /**
   * Tests {@link ConnectionHealthMonitor#getUptime()}
   */
  @Test
  void testReturns100Uptime() throws InterruptedException {
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]).join();
    sleepIntervals(10);
    assertEquals(100, healthMonitor.getUptime().get("1h"));
    assertEquals(100, healthMonitor.getUptime().get("1d"));
    assertEquals(100, healthMonitor.getUptime().get("1w"));
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getUptime()}
   */
  @Test
  void testReturnsAverageUptime() throws InterruptedException {
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]).join();
    sleepIntervals(100);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(59, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(59, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(59, Assertions.within(2.0));
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getUptime()}
   */
  @Test
  void testChecksConnectionForDowntime() throws InterruptedException {
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]).join();
    sleepIntervals(4);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(100, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(100, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(100, Assertions.within(2.0));
    Mockito.when(connection.getTerminalState().isConnected()).thenReturn(false);
    sleepIntervals(4);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(50, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(50, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(50, Assertions.within(2.0));
    Mockito.when(connection.getTerminalState().isConnected()).thenReturn(true);
    Mockito.when(connection.getTerminalState().isConnectedToBroker()).thenReturn(false);
    sleepIntervals(8);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(25, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(25, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(25, Assertions.within(2.0));
    Mockito.when(connection.getTerminalState().isConnectedToBroker()).thenReturn(true);
    Mockito.when(connection.isSynchronized()).thenReturn(false);
    sleepIntervals(4);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(20, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(20, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(20, Assertions.within(2.0));
    Mockito.when(connection.isSynchronized()).thenReturn(true);
    sleepIntervals(12);
    Assertions.assertThat(healthMonitor.getUptime().get("1h")).isCloseTo(50, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1d")).isCloseTo(50, Assertions.within(2.0));
    Assertions.assertThat(healthMonitor.getUptime().get("1w")).isCloseTo(50, Assertions.within(2.0));
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testReturnsOkStatus() throws IllegalAccessException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    Assertions.assertThat(healthMonitor.getHealthStatus())
      .usingRecursiveComparison().isEqualTo(new ConnectionHealthStatus() {{
      connected = true;
      connectedToBroker = true;
      healthy = true;
      message = "Connection to broker is stable. No health issues detected.";
      quoteStreamingHealthy = true;
      isSynchronized = true;
    }});
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testReturnsErrorStatusWithOneMessage() throws IllegalAccessException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    Mockito.when(connection.getTerminalState().isConnectedToBroker()).thenReturn(false);
    Assertions.assertThat(healthMonitor.getHealthStatus())
      .usingRecursiveComparison().isEqualTo(new ConnectionHealthStatus() {{
      connected = true;
      connectedToBroker = false;
      healthy = false;
      message = "Connection is not healthy because connection to broker is not established or lost.";
      quoteStreamingHealthy = true;
      isSynchronized = true;
    }});
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testReturnsErrorStatusWithMultipleMessages() throws IllegalAccessException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    Mockito.when(connection.getTerminalState().isConnected()).thenReturn(false);
    Mockito.when(connection.getTerminalState().isConnectedToBroker()).thenReturn(false);
    Mockito.when(connection.isSynchronized()).thenReturn(false);
    Assertions.assertThat(healthMonitor.getHealthStatus())
      .usingRecursiveComparison().isEqualTo(new ConnectionHealthStatus() {{
      connected = false;
      connectedToBroker = false;
      healthy = false;
      message = "Connection is not healthy because connection to API server is not established or lost "
        + "and connection to broker is not established or lost "
        + "and local terminal state is not synchronized to broker.";
      quoteStreamingHealthy = true;
      isSynchronized = false;
    }});
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testShowsAsHealthyIfRecentlyUpdatedAndInSession() throws IllegalAccessException, InterruptedException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]);
    sleepIntervals(1);
    assertTrue(healthMonitor.getHealthStatus().quoteStreamingHealthy);
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testShowsAsNotHealthyIfOldUpdatedAndInSession() throws IllegalAccessException, InterruptedException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]);
    sleepIntervals(61);
    assertFalse(healthMonitor.getHealthStatus().quoteStreamingHealthy);
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testShowsAsHealthyIfNotInSession() throws IllegalAccessException, InterruptedException {
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[1]);
    sleepIntervals(61);
    assertTrue(healthMonitor.getHealthStatus().quoteStreamingHealthy);
  }
  
  /**
   * Tests {@link ConnectionHealthMonitor#getHealthStatus()}
   */
  @Test
  void testShowsAsHealthyIfNoSymbols() throws IllegalAccessException, InterruptedException {
    Mockito.when(connection.getSubscribedSymbols()).thenReturn(Lists.emptyList());
    FieldUtils.writeField(healthMonitor, "quotesHealthy", true, true);
    healthMonitor.onSymbolPriceUpdated("ps-mpa-1", prices[0]);
    sleepIntervals(61);
    assertTrue(healthMonitor.getHealthStatus().quoteStreamingHealthy);
  }
  
  private void sleepIntervals(int count) throws InterruptedException {
    for (int i = 0; i < count; ++i) {
      Thread.sleep(healthMonitor.getRandomTimeout());
      ServiceProvider.setNowInstantMock(ServiceProvider.getNow()
        .plusMillis(healthMonitor.getRandomTimeout()));
    }
  }
}