package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private Map<String, Date> completedOrders = new HashMap<>();
    private Map<String, Date> removedPositions = new HashMap<>();
    private MetatraderAccountInformation accountInformation = null;
    private boolean positionsInitialized = false;
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
     * @return local copy of account information
     */
    public Optional<MetatraderAccountInformation> getAccountInformation() {
        return Optional.ofNullable(accountInformation);
    }
    
    /**
     * Returns a local copy of MetaTrader positions opened
     * @return a local copy of MetaTrader positions opened
     */
    public List<MetatraderPosition> getPositions() {
        return positions;
    }
    
    /**
     * Returns a local copy of MetaTrader orders opened
     * @return a local copy of MetaTrader orders opened
     */
    public List<MetatraderOrder> getOrders() {
        return orders;
    }
    
    /**
     * Returns a local copy of symbol specifications available in MetaTrader trading terminal
     * @return a local copy of symbol specifications available in MetaTrader trading terminal
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

    /**
     * Invoked when MetaTrader terminal state synchronization is started
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onSynchronizationStarted() {
        accountInformation = null;
        positions.clear();
        orders.clear();
        specifications.clear();
        specificationsBySymbol.clear();
        pricesBySymbol.clear();
        completedOrders.clear();
        removedPositions.clear();
        positionsInitialized = false;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onAccountInformationUpdated(MetatraderAccountInformation accountInformation) {
        this.accountInformation = accountInformation;
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onPositionsReplaced(List<MetatraderPosition> positions) {
        this.positions = new ArrayList<>(positions);
        this.removedPositions.clear();
        this.positionsInitialized = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionUpdated(MetatraderPosition position) {
        int index = -1;
        for (int i = 0; i < positions.size(); ++i) {
            if (positions.get(i).id.equals(position.id)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            positions.set(index, position);
        } else if (!removedPositions.containsKey(position.id)) {
            positions.add(position);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onPositionRemoved(String positionId) {
        Optional<MetatraderPosition> position = positions.stream().filter(p -> !p.id.equals(positionId)).findFirst();
        if (!position.isPresent()) {
            for (Entry<String, Date> e : removedPositions.entrySet()) {
                if (e.getValue().getTime() + 5 * 60 * 1000 < Date.from(Instant.now()).getTime()) {
                    removedPositions.remove(e.getKey());
                }
            }
            removedPositions.put(positionId, Date.from(Instant.now()));
        } else {
            positions.removeIf(p -> p.id.equals(positionId));
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> onOrdersReplaced(List<MetatraderOrder> orders) {
        this.orders = orders;
        this.completedOrders.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderUpdated(MetatraderOrder order) {
        int index = -1;
        for (int i = 0; i < orders.size(); ++i) {
            if (orders.get(i).id.equals(order.id)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            orders.set(index, order);
        } else if (!completedOrders.containsKey(order.id)) {
            orders.add(order);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onOrderCompleted(String orderId) {
        Optional<MetatraderOrder> order = orders.stream().filter(o -> !o.id.equals(orderId)).findFirst();
        if (!order.isPresent()) {
            for (Entry<String, Date> e : completedOrders.entrySet()) {
                if (e.getValue().getTime() + 5 * 60 * 1000 < Date.from(Instant.now()).getTime()) {
                    completedOrders.remove(e.getKey());
                }
            }
            completedOrders.put(orderId, Date.from(Instant.now()));
        } else {
            orders.removeIf(o -> o.id.equals(orderId));
        }
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
    public CompletableFuture<Void> onSymbolPricesUpdated(List<MetatraderSymbolPrice> prices, Double equity,
        Double margin, Double freeMargin, Double marginLevel) {
        boolean pricesInitialized = false;
        for (MetatraderSymbolPrice price : prices) {
            pricesBySymbol.put(price.symbol, price);
            List<MetatraderPosition> positions = this.positions.stream()
                .filter(p -> p.symbol.equals(price.symbol)).collect(Collectors.toList());
            List<MetatraderPosition> otherPositions = this.positions.stream()
                .filter(p -> !p.symbol.equals(price.symbol)).collect(Collectors.toList());
            List<MetatraderOrder> orders = this.orders.stream()
                    .filter(o -> o.symbol.equals(price.symbol)).collect(Collectors.toList());
            pricesInitialized = true;
            for (MetatraderPosition position : otherPositions) {
                MetatraderSymbolPrice p = pricesBySymbol.get(position.symbol);
                if (p != null) {
                    if (position.unrealizedProfit == null) {
                        updatePositionProfits(position, p);
                    }
                } else {
                    pricesInitialized = false;
                }
            }
            for (MetatraderPosition position : positions) {
                updatePositionProfits(position, price);
            }
            for (MetatraderOrder order : orders) {
                order.currentPrice = (order.type == OrderType.ORDER_TYPE_BUY
                    || order.type == OrderType.ORDER_TYPE_BUY_LIMIT
                    || order.type == OrderType.ORDER_TYPE_BUY_STOP
                    || order.type == OrderType.ORDER_TYPE_BUY_STOP_LIMIT
                ? price.ask : price.bid);
            }
        }
        if (accountInformation != null) {
            if (positionsInitialized && pricesInitialized) {
                double profitSum = 0;
                for (MetatraderPosition position : this.positions) profitSum += position.unrealizedProfit;
                accountInformation.equity = accountInformation.balance + profitSum;
            } else {
                accountInformation.equity = equity != null ? equity : accountInformation.equity;
            }
            accountInformation.margin = margin != null ? margin : accountInformation.margin;
            accountInformation.freeMargin = freeMargin != null ? freeMargin : accountInformation.freeMargin;
            accountInformation.marginLevel = freeMargin != null ? marginLevel : accountInformation.marginLevel;
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private void updatePositionProfits(MetatraderPosition position, MetatraderSymbolPrice price) {
        Optional<MetatraderSymbolSpecification> specification = getSpecification(position.symbol);
        if (specification.isPresent()) {
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
            position.unrealizedProfit = unrealizedProfit;
            position.profit = position.unrealizedProfit + position.realizedProfit;
            position.currentPrice = newPositionPrice;
            position.currentTickValue = currentTickValue;
        }
    }
}