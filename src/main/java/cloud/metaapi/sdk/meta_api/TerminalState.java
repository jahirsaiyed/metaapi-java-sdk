package cloud.metaapi.sdk.meta_api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder.OrderType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition.PositionType;

/**
 * Responsible for storing a local copy of remote terminal state
 */
public class TerminalState extends SynchronizationListener {

    /**
     * Defines timeout of waiting for a broker connection status changed signal.
     * Intended to be used in tests for reducing a test time execution.
     */
    protected int statusTimerTimeoutInMilliseconds = 60000;
    
    private boolean connected = false;
    private boolean connectedToBroker = false;
    private List<MetatraderPosition> positions = new ArrayList<>();
    private List<MetatraderOrder> orders = new ArrayList<>();
    private List<MetatraderSymbolSpecification> specifications = new ArrayList<>();
    private Map<String, MetatraderSymbolSpecification> specificationsBySymbol = new HashMap<>();
    private Map<String, MetatraderSymbolPrice> pricesBySymbol = new HashMap<>();
    private Optional<MetatraderAccountInformation> accountInformation = Optional.empty();
    private int accountInformationUpdateCount = 0;
    private Timer statusTimer = null;
    
    /**
     * Returns true if MetaApi have connected to MetaTrader terminal
     * @return true if MetaApi have connected to MetaTrader terminal
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Returns true if MetaApi have connected to MetaTrader terminal and MetaTrader terminal is connected to broker
     * @return true if MetaApi have connected to MetaTrader terminal and MetaTrader terminal is connected to broker
     */
    public boolean isConnectedToBroker() {
        return connectedToBroker;
    }
    
    /**
     * Returns a local copy of account information
     * @returns local copy of account information
     */
    public Optional<MetatraderAccountInformation> getAccountInformation() {
        return accountInformation;
    }
    
    /**
     * Returns a local copy of MetaTrader positions opened
     * @returns a local copy of MetaTrader positions opened
     */
    public List<MetatraderPosition> getPositions() {
        return positions;
    }
    
    /**
     * Returns a local copy of MetaTrader orders opened
     * @returns a local copy of MetaTrader orders opened
     */
    public List<MetatraderOrder> getOrders() {
        return orders;
    }
    
    /**
     * Returns a local copy of symbol specifications available in MetaTrader trading terminal
     * @returns a local copy of symbol specifications available in MetaTrader trading terminal
     */
    public List<MetatraderSymbolSpecification> getSpecifications() {
        return specifications;
    }
    
    /**
     * Returns MetaTrader symbol specification by symbol
     * @param symbol symbol (e.g. currency pair or an index)
     * @return {@link Optional} of {@link MetatraderSymbolSpecification} found or empty {@link Optional} 
     * if specification for a symbol is not found
     */
    public Optional<MetatraderSymbolSpecification> getSpecification(String symbol) {
        return Optional.ofNullable(specificationsBySymbol.get(symbol));
    }
    
    /**
     * Returns MetaTrader symbol price by symbol
     * @param symbol symbol (e.g. currency pair or an index)
     * @return {@link Optional} of {@link MetatraderSymbolPrice} found or empty {@link Optional}
     * if price for a symbol is not found
     */
    public Optional<MetatraderSymbolPrice> getPrice(String symbol) {
        return Optional.ofNullable(pricesBySymbol.get(symbol));
    }
    
    @Override
    public CompletableFuture<Void> onConnected() {
        connected = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDisconnected() {
        connected = false;
        connectedToBroker = false;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onBrokerConnectionStatusChanged(boolean connected) {
        connectedToBroker = connected;
        if (statusTimer != null) statusTimer.cancel();
        final TerminalState self = this;
        // Recreate the timer because once it has been terminated, 
        // no more tasks can be scheduled on it
        statusTimer = new Timer();
        statusTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                self.onDisconnected();
            }
        }, statusTimerTimeoutInMilliseconds);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onAccountInformationUpdated(MetatraderAccountInformation accountInformation) {
        this.accountInformation = Optional.of(accountInformation);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionUpdated(MetatraderPosition position) {
        for (int i = 0; i < positions.size(); ++i) {
            if (positions.get(i).id.equals(position.id)) {
                positions.set(i, position);
                return CompletableFuture.completedFuture(null);
            }
        }
        positions.add(position);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionRemoved(String positionId) {
        positions.removeIf(position -> position.id.equals(positionId));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderUpdated(MetatraderOrder order) {
        for (int i = 0; i < orders.size(); ++i) {
            if (orders.get(i).id.equals(order.id)) {
                orders.set(i, order);
                return CompletableFuture.completedFuture(null);
            }
        }
        orders.add(order);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderCompleted(String orderId) {
        orders.removeIf(order -> order.id.equals(orderId));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onSymbolSpecificationUpdated(MetatraderSymbolSpecification specification) {
        int index = -1;
        for (int i = 0; i < specifications.size(); ++i) {
            if (specifications.get(i).symbol.equals(specification.symbol)) {
                index = i;
                break;
            }
        }
        if (index != -1) specifications.set(index, specification);
        else specifications.add(specification);
        specificationsBySymbol.put(specification.symbol, specification);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onSymbolPriceUpdated(MetatraderSymbolPrice price) {
        pricesBySymbol.put(price.symbol, price);
        Optional<MetatraderSymbolSpecification> specification = getSpecification(price.symbol);
        if (specification.isPresent()) {
            for (MetatraderPosition position : positions) {
                if (!position.symbol.equals(price.symbol)) continue;
                if (position.unrealizedProfit == null || position.realizedProfit == null) {
                    position.unrealizedProfit = 
                        (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
                        (position.currentPrice - position.openPrice) * position.currentTickValue *
                        position.volume / specification.get().tickSize;
                    position.realizedProfit = position.profit - position.unrealizedProfit;
                }
                double newPositionPrice = (position.type == PositionType.POSITION_TYPE_BUY ? price.bid : price.ask);
                double isProfitable = (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
                    (newPositionPrice - position.openPrice);
                double currentTickValue = (isProfitable > 0 ? price.profitTickValue : price.lossTickValue);
                double unrealizedProfit = (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
                    (newPositionPrice - position.openPrice) * currentTickValue *
                    position.volume / specification.get().tickSize;
                double increment = unrealizedProfit - position.unrealizedProfit;
                position.unrealizedProfit = unrealizedProfit;
                position.profit = position.unrealizedProfit + position.realizedProfit;
                position.currentPrice = newPositionPrice;
                position.currentTickValue = currentTickValue;
                if (accountInformation.isPresent()) {
                    accountInformation.get().equity += increment;
                    accountInformationUpdateCount++;
                }
            }
            for (MetatraderOrder order : orders) {
                if (!order.symbol.equals(price.symbol)) continue;
                order.currentPrice = (order.type == OrderType.ORDER_TYPE_BUY_LIMIT 
                    || order.type == OrderType.ORDER_TYPE_BUY_STOP
                    || order.type == OrderType.ORDER_TYPE_BUY_STOP_LIMIT
                ? price.ask : price.bid);
                if (accountInformationUpdateCount > 100) {
                    double profitSum = 0;
                    for (MetatraderPosition position : positions) profitSum += position.profit;
                    accountInformation.get().equity = accountInformation.get().balance + profitSum;
                    accountInformationUpdateCount = 0;
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}