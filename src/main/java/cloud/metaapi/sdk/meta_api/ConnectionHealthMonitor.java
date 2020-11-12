package cloud.metaapi.sdk.meta_api;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
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
    private Reservoir uptimeReservoir;
    private Date priceUpdatedAt;
    private long offset;
    private boolean quotesHealthy = false;
    
    /**
     * Constructs the listener
     * @param connection MetaApi connection instance
     */
    public ConnectionHealthMonitor(MetaApiConnection connection) {
        super();
        this.connection = connection;
        ConnectionHealthMonitor self = this;
        Timer intervalTimer = new Timer();
        intervalTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                self.updateQuoteHealthStatus();
                self.measureUptime();
            }
        }, measureInterval, measureInterval);
        this.uptimeReservoir = new Reservoir(24 * 7, 7 * 24 * 60 * 60 * 1000);
    }
    
    public CompletableFuture<Void> onSymbolPriceUpdated(MetatraderSymbolPrice price) {
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
     * Returns uptime in percents measured over a period of one week
     * @return uptime in percents measured over a period of one week
     */
    public double getUptime() {
        return uptimeReservoir.getStatistics().average;
    }
    
    private void measureUptime() {
        try {
            uptimeReservoir.pushMeasurement(connection.getTerminalState().isConnected()
                && connection.getTerminalState().isConnectedToBroker() && connection.isSynchronized()
                && quotesHealthy ? 100 : 0);
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