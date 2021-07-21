package cloud.metaapi.sdk.meta_api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloud.metaapi.sdk.clients.meta_api.LatencyListener;
import cloud.metaapi.sdk.meta_api.reservoir.Reservoir;
import cloud.metaapi.sdk.meta_api.reservoir.StatisticalReservoir;

/**
 * Responsible for monitoring MetaApi application latencies
 */
public class LatencyMonitor extends LatencyListener {
  
  /**
   * Contains latencies from client, server and broker side
   */
  public static class MonitorLatencies {
    /**
     * Latencies from client side
     */
    public Map<String, Latencies> clientLatency;
    /**
     * Latencies from server side
     */
    public Map<String, Latencies> serverLatency;
    /**
     * Latencies from broker side, or {@code null} for request latencies
     */
    public Map<String, Latencies> brokerLatency;
  }
  
  /**
   * Contains measured latencies
   */
  public static class Latencies {
    /**
     * 50th persentile
     */
    public double p50;
    /**
     * 75th persentile
     */
    public double p75;
    /**
     * 90th persentile
     */
    public double p90;
    /**
     * 95th persentile
     */
    public double p95;
    /**
     * 98th persentile
     */
    public double p98;
    /**
     * Average value
     */
    public double avg;
    /**
     * Count of values
     */
    public int count;
    /**
     * Minimum value
     */
    public double min;
    /**
     * Maximum value
     */
    public double max;
  }
  
  private static class MonitorReservoir {
    public StatisticalReservoir percentiles;
    public Reservoir reservoir;
  }
  
  private static class MapReservoirs<T> {
    
    private Map<String, T> reservoirs;
    
    /**
     * Construct instance
     * @param reservoirs Initial reservoirs, or {@code null}
     */
    public MapReservoirs(Map<String, T> reservoirs) {
      this.reservoirs = reservoirs != null ? reservoirs : new HashMap<>(); 
    }
    
    /**
     * Sets reservoir
     * @param key Request type
     * @param reservoir Reservoir to set
     */
    public void set(String key, T reservoir) {
      reservoirs.put(key, reservoir);
    }
    
    /**
     * Returns reservoir
     * @param type Request type
     * @return Reservoir or {@code null}
     */
    public T get(String type) {
      return reservoirs.get(type);
    }
    
    /**
     * Returns set reservoirs
     * @return set reservoirs
     */
    public Collection<T> getReservoirs() {
      return reservoirs.values();
    }
    
    /**
     * Returns reservoir entries (pairs of key and value)
     * @return reservoir entries
     */
    public Set<Entry<String, T>> getEntries() {
      return reservoirs.entrySet();
    }
  }
  
  private static Logger logger = LogManager.getLogger(LatencyMonitor.class);
  private MapReservoirs<MapReservoirs<MonitorReservoir>> tradeReservoirs;
  private MapReservoirs<MapReservoirs<MonitorReservoir>> updateReservoirs;
  private MapReservoirs<MapReservoirs<MonitorReservoir>> priceReservoirs;
  private MapReservoirs<MapReservoirs<MapReservoirs<MonitorReservoir>>> requestReservoirs;
  
  /**
   * Constructs latency monitor instance
   */
  public LatencyMonitor() {
    super();
    Map<String, MapReservoirs<MonitorReservoir>> tradeReservoirMap = new HashMap<>();
    tradeReservoirMap.put("clientLatency", initializeReservoirs());
    tradeReservoirMap.put("serverLatency", initializeReservoirs());
    tradeReservoirMap.put("brokerLatency", initializeReservoirs());
    tradeReservoirs = new MapReservoirs<>(tradeReservoirMap);
    Map<String, MapReservoirs<MonitorReservoir>> updateReservoirMap = new HashMap<>();
    updateReservoirMap.put("clientLatency", initializeReservoirs());
    updateReservoirMap.put("serverLatency", initializeReservoirs());
    updateReservoirMap.put("brokerLatency", initializeReservoirs());
    updateReservoirs = new MapReservoirs<>(updateReservoirMap);
    Map<String, MapReservoirs<MonitorReservoir>> priceReservoirMap = new HashMap<>();
    priceReservoirMap.put("clientLatency", initializeReservoirs());
    priceReservoirMap.put("serverLatency", initializeReservoirs());
    priceReservoirMap.put("brokerLatency", initializeReservoirs());
    priceReservoirs = new MapReservoirs<>(priceReservoirMap);
    requestReservoirs = new MapReservoirs<>(null);
  }
  
  @Override
  public CompletableFuture<Void> onResponse(String accountId, String type, ResponseTimestamps timestamps) {
    if (requestReservoirs.get(type) == null) {
      MapReservoirs<MapReservoirs<MonitorReservoir>> reservoirsByLatencyType = new MapReservoirs<>(null);
      reservoirsByLatencyType.set("clientLatency", initializeReservoirs());
      reservoirsByLatencyType.set("serverLatency", initializeReservoirs());
      requestReservoirs.set(type, reservoirsByLatencyType);
    }
    if (timestamps.serverProcessingStarted != null && timestamps.serverProcessingFinished != null) {
      long serverLatency = timestamps.serverProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingStarted.getDate().getTime();
      saveMeasurement(requestReservoirs.get(type).get("serverLatency"), serverLatency);
    }
    if (timestamps.clientProcessingStarted != null && timestamps.clientProcessingFinished != null &&
        timestamps.serverProcessingStarted != null && timestamps.serverProcessingFinished != null) {
      long serverLatency = timestamps.serverProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingStarted.getDate().getTime();
      long clientLatency = timestamps.clientProcessingFinished.getDate().getTime()
        - timestamps.clientProcessingStarted.getDate().getTime() - serverLatency;
      saveMeasurement(requestReservoirs.get(type).get("clientLatency"), clientLatency);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns request processing latencies
   * @return request processing latencies
   */
  @SuppressWarnings("unchecked")
  public Map<String, MonitorLatencies> getRequestLatencies() {
    Map<String, MonitorLatencies> result = new HashMap<>();
    Map<String, Object> latencyMap = constructLatenciesRecursively(requestReservoirs);
    for (Entry<String, Object> entry : latencyMap.entrySet()) {
      result.put(entry.getKey(), constructMonitorLatencies((Map<String, Object>) entry.getValue()));
    }
    return result;
  }
  
  @Override
  public CompletableFuture<Void> onSymbolPrice(String accountId, String symbol, SymbolPriceTimestamps timestamps) {
    if (timestamps.eventGenerated != null && timestamps.serverProcessingStarted != null) {
      long brokerLatency = timestamps.serverProcessingStarted.getDate().getTime()
        - timestamps.eventGenerated.getDate().getTime();
      saveMeasurement(priceReservoirs.get("brokerLatency"), brokerLatency);
    }
    if (timestamps.serverProcessingStarted != null && timestamps.serverProcessingFinished != null) {
      long serverLatency = timestamps.serverProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingStarted.getDate().getTime();
      saveMeasurement(priceReservoirs.get("serverLatency"), serverLatency);
    }
    if (timestamps.serverProcessingFinished != null && timestamps.clientProcessingFinished != null) {
      long clientLatency = timestamps.clientProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingFinished.getDate().getTime();
      saveMeasurement(priceReservoirs.get("clientLatency"), clientLatency);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns price streaming latencies
   * @return price streaming latencies
   */
  public MonitorLatencies getPriceLatencies() {
    return constructMonitorLatencies(constructLatenciesRecursively(priceReservoirs));
  }
  
  @Override
  public CompletableFuture<Void> onUpdate(String accountId, UpdateTimestamps timestamps) {
    if (timestamps.eventGenerated != null && timestamps.serverProcessingStarted != null) {
      long brokerLatency = timestamps.serverProcessingStarted.getDate().getTime()
        - timestamps.eventGenerated.getDate().getTime();
      saveMeasurement(updateReservoirs.get("brokerLatency"), brokerLatency);
    }
    if (timestamps.serverProcessingStarted != null && timestamps.serverProcessingFinished != null) {
      long serverLatency = timestamps.serverProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingStarted.getDate().getTime();
      saveMeasurement(updateReservoirs.get("serverLatency"), serverLatency);
    }
    if (timestamps.serverProcessingFinished != null && timestamps.clientProcessingFinished != null) {
      long clientLatency = timestamps.clientProcessingFinished.getDate().getTime()
        - timestamps.serverProcessingFinished.getDate().getTime();
      saveMeasurement(updateReservoirs.get("clientLatency"), clientLatency);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns update streaming latencies
   * @return update streaming latencies
   */
  public MonitorLatencies getUpdateLatencies() {
    return constructMonitorLatencies(constructLatenciesRecursively(updateReservoirs));
  }
  
  @Override
  public CompletableFuture<Void> onTrade(String accountId, TradeTimestamps timestamps) {
    if (timestamps.clientProcessingStarted != null && timestamps.serverProcessingStarted != null) {
      long clientLatency = timestamps.serverProcessingStarted.getDate().getTime()
        - timestamps.clientProcessingStarted.getDate().getTime();
      saveMeasurement(tradeReservoirs.get("clientLatency"), clientLatency);
    }
    if (timestamps.serverProcessingStarted != null && timestamps.tradeStarted != null) {
      long serverLatency = timestamps.tradeStarted.getDate().getTime()
        - timestamps.serverProcessingStarted.getDate().getTime();
      saveMeasurement(tradeReservoirs.get("serverLatency"), serverLatency);
    }
    if (timestamps.tradeStarted != null && timestamps.tradeExecuted != null) {
      long brokerLatency = timestamps.tradeExecuted.getDate().getTime()
        - timestamps.tradeStarted.getDate().getTime();
      saveMeasurement(tradeReservoirs.get("brokerLatency"), brokerLatency);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns trade latencies
   * @return trade latencies
   */
  public MonitorLatencies getTradeLatencies() {
    return constructMonitorLatencies(constructLatenciesRecursively(tradeReservoirs));
  }
  
  private void saveMeasurement(MapReservoirs<MonitorReservoir> reservoirs, long clientLatency) {
    reservoirs.getReservoirs().forEach(reservoir -> {
      reservoir.percentiles.pushMeasurement(clientLatency);
      reservoir.reservoir.pushMeasurement(clientLatency);
    });
  }
  
  private Map<String, Object> constructLatenciesRecursively(MapReservoirs<?> reservoirs) {
    Map<String, Object> result = new HashMap<>();
    for (Entry<String, ?> entry : reservoirs.getEntries()) {
      if (entry.getValue() instanceof MapReservoirs) {
        result.put(entry.getKey(), constructLatenciesRecursively((MapReservoirs<?>) entry.getValue()));
      } else {
        MonitorReservoir reservoir = (MonitorReservoir) entry.getValue();
        result.put(entry.getKey(), new Latencies() {{
          p50 = reservoir.percentiles.getPercentile(50);
          p75 = reservoir.percentiles.getPercentile(75);
          p90 = reservoir.percentiles.getPercentile(90);
          p95 = reservoir.percentiles.getPercentile(95);
          p98 = reservoir.percentiles.getPercentile(98);
          avg = reservoir.reservoir.getStatistics().average;
          count = reservoir.reservoir.getStatistics().count;
          min = reservoir.reservoir.getStatistics().min;
          max = reservoir.reservoir.getStatistics().max;
        }});
      }
    }
    return result;
  }
  
  private MonitorLatencies constructMonitorLatencies(Map<String, Object> latencyMap) {
    MonitorLatencies result = new MonitorLatencies();
    for (Entry<String, Object> entry : latencyMap.entrySet()) {
      try {
        FieldUtils.writeField(result, entry.getKey(), entry.getValue());
      } catch (IllegalAccessException e) {
        logger.error(e);
      }
    }
    return result;
  }
  
  private MapReservoirs<MonitorReservoir> initializeReservoirs() {
    Map<String, MonitorReservoir> reservoirs = new HashMap<>();
    reservoirs.put("1h", new MonitorReservoir() {{
      percentiles = new StatisticalReservoir(1000, 60 * 60 * 1000L);
      reservoir = new Reservoir(60, 60 * 60 * 1000);
    }});
    reservoirs.put("1d", new MonitorReservoir() {{
      percentiles = new StatisticalReservoir(1000, 60 * 60 * 1000L);
      reservoir = new Reservoir(60, 60 * 60 * 1000);
    }});
    reservoirs.put("1w", new MonitorReservoir() {{
      percentiles = new StatisticalReservoir(1000, 60 * 60 * 1000L);
      reservoir = new Reservoir(60, 60 * 60 * 1000);
    }});
    return new MapReservoirs<>(reservoirs);
  }
}