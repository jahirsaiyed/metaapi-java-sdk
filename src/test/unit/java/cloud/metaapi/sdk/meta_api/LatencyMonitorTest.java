package cloud.metaapi.sdk.meta_api;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.meta_api.LatencyListener.ResponseTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.SymbolPriceTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.TradeTimestamps;
import cloud.metaapi.sdk.clients.meta_api.LatencyListener.UpdateTimestamps;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.meta_api.LatencyMonitor.Latencies;
import cloud.metaapi.sdk.meta_api.LatencyMonitor.MonitorLatencies;

/**
 * Tests {@link LatencyMonitor}
 */
class LatencyMonitorTest {

  private LatencyMonitor monitor;
  
  @BeforeEach
  void setUp() throws Exception {
    monitor = new LatencyMonitor();
  }

  /**
   * Tests
   * {@link LatencyMonitor#onTrade(String, TradeTimestamps)},
   * {@link LatencyMonitor#getTradeLatencies()}
   */
  @Test
  void testProcessesTradeLatencies() {
    monitor.onTrade("accountId", new TradeTimestamps() {{
      clientProcessingStarted = new IsoTime("2020-12-07T13:22:48.000Z");
      serverProcessingStarted = new IsoTime("2020-12-07T13:22:49.000Z");
      tradeStarted = new IsoTime("2020-12-07T13:22:51.000Z");
      tradeExecuted = new IsoTime("2020-12-07T13:22:54.000Z");
    }});
    Map<String, Latencies> expectedClientLatencies = new HashMap<>();
    expectedClientLatencies.put("1h", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedClientLatencies.put("1d", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedClientLatencies.put("1w", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    Map<String, Latencies> expectedServerLatencies = new HashMap<>();
    expectedServerLatencies.put("1h", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1d", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1w", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    Map<String, Latencies> expectedBrokerLatencies = new HashMap<>();
    expectedBrokerLatencies.put("1h", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedBrokerLatencies.put("1d", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedBrokerLatencies.put("1w", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    assertThat(monitor.getTradeLatencies()).usingRecursiveComparison().isEqualTo(new MonitorLatencies() {{
      clientLatency = expectedClientLatencies;
      serverLatency = expectedServerLatencies;
      brokerLatency = expectedBrokerLatencies;
    }});
  }
  
  /**
   * Tests
   * {@link LatencyMonitor#onUpdate(String, UpdateTimestamps)},
   * {@link LatencyMonitor#getUpdateLatencies()}
   */
  @Test
  void testProcessesUpdateLatencies() {
    monitor.onUpdate("accountId", new UpdateTimestamps() {{
      eventGenerated = new IsoTime("2020-12-07T13:22:48.000Z");
      serverProcessingStarted = new IsoTime("2020-12-07T13:22:49.000Z");
      serverProcessingFinished = new IsoTime("2020-12-07T13:22:51.000Z");
      clientProcessingFinished = new IsoTime("2020-12-07T13:22:54.000Z");
    }});
    Map<String, Latencies> expectedBrokerLatencies = new HashMap<>();
    expectedBrokerLatencies.put("1h", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedBrokerLatencies.put("1d", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedBrokerLatencies.put("1w", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    Map<String, Latencies> expectedServerLatencies = new HashMap<>();
    expectedServerLatencies.put("1h", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1d", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1w", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    Map<String, Latencies> expectedClientLatencies = new HashMap<>();
    expectedClientLatencies.put("1h", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedClientLatencies.put("1d", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedClientLatencies.put("1w", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    assertThat(monitor.getUpdateLatencies()).usingRecursiveComparison().isEqualTo(new MonitorLatencies() {{
      clientLatency = expectedClientLatencies;
      serverLatency = expectedServerLatencies;
      brokerLatency = expectedBrokerLatencies;
    }});
  }
  
  /**
   * Tests
   * {@link LatencyMonitor#onSymbolPrice(String, String, SymbolPriceTimestamps)},
   * {@link LatencyMonitor#getPriceLatencies()}
   */
  @Test
  void testProcessesPriceStreamingLatencies() {
    monitor.onSymbolPrice("accountId", "EURUSD", new SymbolPriceTimestamps() {{
      eventGenerated = new IsoTime("2020-12-07T13:22:48.000Z");
      serverProcessingStarted = new IsoTime("2020-12-07T13:22:49.000Z");
      serverProcessingFinished = new IsoTime("2020-12-07T13:22:51.000Z");
      clientProcessingFinished = new IsoTime("2020-12-07T13:22:54.000Z");
    }});
    Map<String, Latencies> expectedBrokerLatencies = new HashMap<>();
    expectedBrokerLatencies.put("1h", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedBrokerLatencies.put("1d", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedBrokerLatencies.put("1w", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    Map<String, Latencies> expectedServerLatencies = new HashMap<>();
    expectedServerLatencies.put("1h", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1d", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1w", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    Map<String, Latencies> expectedClientLatencies = new HashMap<>();
    expectedClientLatencies.put("1h", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedClientLatencies.put("1d", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    expectedClientLatencies.put("1w", new Latencies() {{
      p50 = 3000;
      p75 = 3000;
      p90 = 3000;
      p95 = 3000;
      p98 = 3000;
      avg = 3000;
      count = 1;
      min = 3000;
      max = 3000;
    }});
    assertThat(monitor.getPriceLatencies()).usingRecursiveComparison().isEqualTo(new MonitorLatencies() {{
      clientLatency = expectedClientLatencies;
      serverLatency = expectedServerLatencies;
      brokerLatency = expectedBrokerLatencies;
    }});
  }
  
  /**
   * Tests
   * {@link LatencyMonitor#onResponse(String, String, ResponseTimestamps)},
   * {@link LatencyMonitor#getRequestLatencies()}
   */
  @Test
  void testProcessesRequestLatencies() {
    monitor.onResponse("accountId", "getSymbolPrice", new ResponseTimestamps() {{
      clientProcessingStarted = new IsoTime("2020-12-07T13:22:48.500Z");
      serverProcessingStarted = new IsoTime("2020-12-07T13:22:49.000Z");
      serverProcessingFinished = new IsoTime("2020-12-07T13:22:51.000Z");
      clientProcessingFinished = new IsoTime("2020-12-07T13:22:51.500Z");
    }});
    Map<String, Latencies> expectedClientLatencies = new HashMap<>();
    expectedClientLatencies.put("1h", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedClientLatencies.put("1d", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    expectedClientLatencies.put("1w", new Latencies() {{
      p50 = 1000;
      p75 = 1000;
      p90 = 1000;
      p95 = 1000;
      p98 = 1000;
      avg = 1000;
      count = 1;
      min = 1000;
      max = 1000;
    }});
    Map<String, Latencies> expectedServerLatencies = new HashMap<>();
    expectedServerLatencies.put("1h", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1d", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    expectedServerLatencies.put("1w", new Latencies() {{
      p50 = 2000;
      p75 = 2000;
      p90 = 2000;
      p95 = 2000;
      p98 = 2000;
      avg = 2000;
      count = 1;
      min = 2000;
      max = 2000;
    }});
    Map<String, MonitorLatencies> expected = new HashMap<>();
    expected.put("getSymbolPrice", new MonitorLatencies() {{
      clientLatency = expectedClientLatencies;
      serverLatency = expectedServerLatencies;
    }});
    assertThat(monitor.getRequestLatencies()).usingRecursiveComparison().isEqualTo(expected);
  }
}