package cloud.metaapi.sdk.meta_api;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.ConnectionHealthStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSession;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSessions;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolSpecification;
import cloud.metaapi.sdk.meta_api.reservoir.Reservoir;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tracks connection health status
 */
public class ConnectionHealthMonitor extends SynchronizationListener {

  protected static int measureInterval = 1000;
  protected static int minQuoteInterval = 60000;
  private static Logger logger = Logger.getLogger(ConnectionHealthMonitor.class);
  private MetaApiConnection connection;
  private Map<String, Reservoir> uptimeReservoirs;
  private Date priceUpdatedAt;
  private long offset;
  private boolean quotesHealthy = false;
  private Timer updateMeasurementsInterval;
  private Map<String, HealthStatus> serverHealthStatus = new HashMap<>();
  
  /**
   * Constructs the listener
   * @param connection MetaApi connection instance
   */
  public ConnectionHealthMonitor(MetaApiConnection connection) {
    super();
    this.connection = connection;
    ConnectionHealthMonitor self = this;
    this.updateMeasurementsInterval = new Timer();
    this.updateMeasurementsInterval.schedule(new TimerTask() {
      @Override
      public void run() {
        self.updateQuoteHealthStatus();
        self.measureUptime();
      }
    }, measureInterval, measureInterval);
    this.uptimeReservoirs = new HashMap<>();
    this.uptimeReservoirs.put("5m", new Reservoir(300, 5 * 60 * 1000));
    this.uptimeReservoirs.put("1h", new Reservoir(600, 60 * 60 * 1000));
    this.uptimeReservoirs.put("1d", new Reservoir(24 * 60, 24 * 60 * 60 * 1000));
    this.uptimeReservoirs.put("1w", new Reservoir(24 * 7, 7 * 24 * 60 * 60 * 1000));
  }
  
  /**
   * Stops health monitor
   */
  public void stop() {
    updateMeasurementsInterval.cancel();
  }
  
  @Override
  public CompletableFuture<Void> onSymbolPriceUpdated(String instanceIndex, MetatraderSymbolPrice price) {
    try {
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
      long brokerTimestamp = formatter.parse(price.brokerTime).getTime();
      priceUpdatedAt = Date.from(ServiceProvider.getNow());
      offset = priceUpdatedAt.getTime() - brokerTimestamp;
    } catch (ParseException e) {
      logger.error("Failed to update quote streaming health status on price update for account "
        + connection.getAccount().getId(), e);
      e.printStackTrace();
    }
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onHealthStatus(String instanceIndex, HealthStatus status) {
    serverHealthStatus.put(instanceIndex, status);
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onDisconnected(String instanceIndex) {
    serverHealthStatus.remove(instanceIndex);
    return CompletableFuture.completedFuture(null);
  }
  
  /**
   * Returns server-side application health status
   * @return server-side application health status
   */
  public Optional<HealthStatus> getServerHealthStatus() {
    HealthStatus result = null;
    for (HealthStatus s : serverHealthStatus.values()) {
      if (result == null) {
        result = s;
      } else {
        for (Field field : HealthStatus.class.getFields()) {
          try {
            field.set(result, field.get(field) != null ? field.get(field) : field.get(result));
          } catch (IllegalArgumentException | IllegalAccessException e) {
            logger.error(e);
          }
        }
      }
    }
    return Optional.ofNullable(result);
  }
  
  /**
   * Returns health status
   * @return connection health status
   */
  public ConnectionHealthStatus getHealthStatus() {
    ConnectionHealthStatus status = new ConnectionHealthStatus() {{
      connected = connection.getTerminalState().isConnected();
      connectedToBroker = connection.getTerminalState().isConnectedToBroker();
      quoteStreamingHealthy = quotesHealthy;
      isSynchronized = connection.isSynchronized();
    }};
    status.healthy = status.connected && status.connectedToBroker && status.quoteStreamingHealthy
      && status.isSynchronized;
    String message;
    if (status.healthy) {
      message = "Connection to broker is stable. No health issues detected.";
    } else {
      message = "Connection is not healthy because ";
      List<String> reasons = new LinkedList<String>();
      if (!status.connected) {
        reasons.add("connection to API server is not established or lost");
      }
      if (!status.connectedToBroker) {
        reasons.add("connection to broker is not established or lost");
      }
      if (!status.isSynchronized) {
        reasons.add("local terminal state is not synchronized to broker");
      }
      if (!status.quoteStreamingHealthy) {
        reasons.add("quotes are not streamed from the broker properly");
      }
      message = message + String.join(" and ", reasons) + ".";
    }
    status.message = message;
    return status;
  }
  
  /**
   * Returns uptime in percents measured over specific periods of time
   * @return uptime in percents measured over specific periods of time
   */
  public Map<String, Double> getUptime() {
    Map<String, Double> uptime = new HashMap<>();
    for (Map.Entry<String, Reservoir> entry : uptimeReservoirs.entrySet()) {
      uptime.put(entry.getKey(), entry.getValue().getStatistics().average);
    }
    return uptime;
  }
  
  private void measureUptime() {
    try {
      for (Reservoir r : uptimeReservoirs.values()) {
        r.pushMeasurement(connection.getTerminalState().isConnected()
          && connection.getTerminalState().isConnectedToBroker()
          && connection.isSynchronized()
          && quotesHealthy ? 100 : 0);
      }
    } catch (Throwable e) {
      logger.error("Failed to measure uptime for account " + connection.getAccount().getId(), e);
    }
  }
  
  private void updateQuoteHealthStatus() {
    try {
      Date serverDateTime = Date.from(ServiceProvider.getNow().minusMillis(offset));
      SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
      String serverTime = formatter.format(serverDateTime);
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(serverDateTime);
      int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
      boolean inQuoteSession = false;
      if (priceUpdatedAt == null) {
        priceUpdatedAt = Date.from(Instant.now());
      }
      if (connection.getSubscribedSymbols().size() == 0) {
        priceUpdatedAt = Date.from(Instant.now());
      }
      for (String symbol : connection.getSubscribedSymbols()) {
        Optional<MetatraderSymbolSpecification> specification = connection
          .getTerminalState().getSpecification(symbol);
        if (specification.isPresent()) {
          List<MetatraderSession> quoteSessions = getQuoteSessions(
            specification.get().quoteSessions, dayOfWeek);
          for (MetatraderSession session : quoteSessions) {
            if ((session.from.compareTo(serverTime) == -1 || session.from.compareTo(serverTime) == 0)
              && (session.to.compareTo(serverTime) == 1 || session.to.compareTo(serverTime) == 0)) {
              inQuoteSession = true;
            }
          }
        }
      }
      quotesHealthy = connection.getSubscribedSymbols().size() == 0 || !inQuoteSession ||
        (ServiceProvider.getNow().toEpochMilli() - priceUpdatedAt.getTime() < minQuoteInterval);
    } catch (Throwable e) {
      logger.error("Failed to update quote streaming health status for account "
        + connection.getAccount().getId(), e);
    }
  }
  
  private List<MetatraderSession> getQuoteSessions(MetatraderSessions quoteSessions, int dayOfWeek) {
    List<MetatraderSession> result = null;
    switch (dayOfWeek) {
      case Calendar.SUNDAY:
        result = quoteSessions.SUNDAY;
        break;
      case Calendar.MONDAY:
        result = quoteSessions.MONDAY;
        break;
      case Calendar.TUESDAY:
        result = quoteSessions.TUESDAY;
        break;
      case Calendar.WEDNESDAY:
        result = quoteSessions.WEDNESDAY;
        break;
      case Calendar.THURSDAY:
        result = quoteSessions.THURSDAY;
        break;
      case Calendar.FRIDAY:
        result = quoteSessions.FRIDAY;
        break;
      case Calendar.SATURDAY:
        result = quoteSessions.SATURDAY;
        break;
    }
    return result != null ? result : new ArrayList<>();
  }
}