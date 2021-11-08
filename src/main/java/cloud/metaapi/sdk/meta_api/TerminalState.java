package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder.OrderType;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderPosition.PositionType;
import cloud.metaapi.sdk.util.Async;
import cloud.metaapi.sdk.util.Js;

/**
 * Responsible for storing a local copy of remote terminal state
 */
public class TerminalState extends SynchronizationListener {

  /**
   * Defines timeout of waiting for a broker connection status changed signal.
   * Intended to be used in tests for reducing a test time execution.
   */
  protected int statusTimerTimeoutInMilliseconds = 60000;
  
  private Map<String, State> stateByInstanceIndex = new ConcurrentHashMap<>();
  private Map<String, List<CompletableFuture<Void>>> waitForPriceResolves = new ConcurrentHashMap<>(); 
  
  private static class State {
    public boolean connected = false;
    public boolean connectedToBroker = false;
    public MetatraderAccountInformation accountInformation = null;
    public List<MetatraderPosition> positions = new ArrayList<>();
    public List<MetatraderOrder> orders = new ArrayList<>();
    public List<MetatraderSymbolSpecification> specifications = new ArrayList<>();
    public Map<String, MetatraderSymbolSpecification> specificationsBySymbol = new ConcurrentHashMap<>();
    public Map<String, MetatraderSymbolPrice> pricesBySymbol = new ConcurrentHashMap<>();
    public Map<String, Date> completedOrders = new ConcurrentHashMap<>();
    public Map<String, Date> removedPositions = new ConcurrentHashMap<>();
    public boolean positionsInitialized = false;
    public long lastUpdateTime = 0;
  }
  
  /**
   * Returns true if MetaApi have connected to MetaTrader terminal
   * @return true if MetaApi have connected to MetaTrader terminal
   */
  public boolean isConnected() {
    return stateByInstanceIndex.values().stream().filter(state -> state.connected).findFirst().isPresent();
  }
  
  /**
   * Returns true if MetaApi have connected to MetaTrader terminal and MetaTrader terminal is connected to broker
   * @return true if MetaApi have connected to MetaTrader terminal and MetaTrader terminal is connected to broker
   */
  public boolean isConnectedToBroker() {
    return stateByInstanceIndex.values().stream().filter(state -> state.connectedToBroker).findFirst().isPresent();
  }
  
  /**
   * Returns a local copy of account information
   * @return local copy of account information
   */
  public Optional<MetatraderAccountInformation> getAccountInformation() {
    return Optional.ofNullable(getBestState().accountInformation);
  }
  
  /**
   * Returns a local copy of MetaTrader positions opened
   * @return a local copy of MetaTrader positions opened
   */
  public List<MetatraderPosition> getPositions() {
    return getBestState().positions;
  }
  
  /**
   * Returns a local copy of MetaTrader orders opened
   * @return a local copy of MetaTrader orders opened
   */
  public List<MetatraderOrder> getOrders() {
    return getBestState().orders;
  }
  
  /**
   * Returns a local copy of symbol specifications available in MetaTrader trading terminal
   * @return a local copy of symbol specifications available in MetaTrader trading terminal
   */
  public List<MetatraderSymbolSpecification> getSpecifications() {
    return getBestState().specifications;
  }
  
  /**
   * Returns MetaTrader symbol specification by symbol
   * @param symbol symbol (e.g. currency pair or an index)
   * @return {@link Optional} of {@link MetatraderSymbolSpecification} found or empty {@link Optional} 
   * if specification for a symbol is not found
   */
  public Optional<MetatraderSymbolSpecification> getSpecification(String symbol) {
    return Optional.ofNullable(getBestState().specificationsBySymbol.get(symbol));
  }
  
  /**
   * Returns MetaTrader symbol price by symbol
   * @param symbol symbol (e.g. currency pair or an index)
   * @return {@link Optional} of {@link MetatraderSymbolPrice} found or empty {@link Optional}
   * if price for a symbol is not found
   */
  public Optional<MetatraderSymbolPrice> getPrice(String symbol) {
    return Optional.ofNullable(getBestState().pricesBySymbol.get(symbol));
  }
  
  /**
   * Waits for price to be received
   * @param symbol symbol (e.g. currency pair or an index)
   * @return completable future resolving with price or null if price has not been received
   */
  public CompletableFuture<Optional<MetatraderSymbolPrice>> waitForPrice(String symbol) {
    return waitForPrice(symbol, null);
  }
  
  /**
   * Waits for price to be received
   * @param symbol symbol (e.g. currency pair or an index)
   * @param timeoutInSeconds timeout in seconds, or {@code null}. Default is 30
   * @return completable future resolving with price or empty optional value if price has not been received
   */
  public CompletableFuture<Optional<MetatraderSymbolPrice>> waitForPrice(String symbol, Long timeoutInSeconds) {
    if (timeoutInSeconds == null) {
      timeoutInSeconds = 30L;
    }
    long timeoutInSecondsFinal = timeoutInSeconds;
    return Async.supply(() -> {
      if (!getPrice(symbol).isPresent()) {
        if (!waitForPriceResolves.containsKey(symbol)) {
          waitForPriceResolves.put(symbol, new ArrayList<>());
        }
        CompletableFuture<Void> waitFuture = new CompletableFuture<>();
        waitForPriceResolves.get(symbol).add(waitFuture);
        try {
          waitFuture.get(timeoutInSecondsFinal, TimeUnit.SECONDS);
        } catch (TimeoutException err) {
          // Ignore timeout
        } catch (Throwable err) {
          throw new CompletionException(err);
        }
      }
      return getPrice(symbol);
    });
  }
  
  @Override
  public CompletableFuture<Void> onConnected(String instanceIndex, int replicas) {
    getState(instanceIndex).connected = true;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onDisconnected(String instanceIndex) {
    State state = getState(instanceIndex);
    state.connected = false;
    state.connectedToBroker = false;
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onBrokerConnectionStatusChanged(String instanceIndex, boolean connected) {
    getState(instanceIndex).connectedToBroker = connected;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onSynchronizationStarted(String instanceIndex) {
    State state = getState(instanceIndex);
    state.accountInformation = null;
    state.positions.clear();
    state.orders.clear();
    state.specifications.clear();
    state.specificationsBySymbol.clear();
    state.pricesBySymbol.clear();
    state.completedOrders.clear();
    state.removedPositions.clear();
    state.positionsInitialized = false;
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onAccountInformationUpdated(String instanceIndex,
    MetatraderAccountInformation accountInformation) {
    getState(instanceIndex).accountInformation = accountInformation;
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onPositionsReplaced(String instanceIndex,
    List<MetatraderPosition> positions) {
    State state = getState(instanceIndex);
    state.positions = new ArrayList<>(positions);
    state.removedPositions.clear();
    state.positionsInitialized = true;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onPositionUpdated(String instanceIndex, MetatraderPosition position) {
    State state = getState(instanceIndex);
    int index = -1;
    for (int i = 0; i < state.positions.size(); ++i) {
      if (state.positions.get(i).id.equals(position.id)) {
        index = i;
        break;
      }
    }
    if (index != -1) {
      state.positions.set(index, position);
    } else if (!state.removedPositions.containsKey(position.id)) {
      state.positions.add(position);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onPositionRemoved(String instanceIndex, String positionId) {
    State state = getState(instanceIndex);
    Optional<MetatraderPosition> position = state.positions.stream()
      .filter(p -> p.id.equals(positionId)).findFirst();
    if (!position.isPresent()) {
      for (Entry<String, Date> e : state.removedPositions.entrySet()) {
        if (e.getValue().getTime() + 5 * 60 * 1000 < Date.from(Instant.now()).getTime()) {
          state.removedPositions.remove(e.getKey());
        }
      }
      state.removedPositions.put(positionId, Date.from(Instant.now()));
    } else {
      state.positions.removeIf(p -> p.id.equals(positionId));
    }
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onOrdersReplaced(String instanceIndex, List<MetatraderOrder> orders) {
    State state = getState(instanceIndex);
    state.orders = orders;
    state.completedOrders.clear();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onOrderUpdated(String instanceIndex, MetatraderOrder order) {
    State state = getState(instanceIndex);
    int index = -1;
    for (int i = 0; i < state.orders.size(); ++i) {
      if (state.orders.get(i).id.equals(order.id)) {
        index = i;
        break;
      }
    }
    if (index != -1) {
      state.orders.set(index, order);
    } else if (!state.completedOrders.containsKey(order.id)) {
      state.orders.add(order);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onOrderCompleted(String instanceIndex, String orderId) {
    State state = getState(instanceIndex);
    Optional<MetatraderOrder> order = state.orders.stream()
      .filter(o -> !o.id.equals(orderId)).findFirst();
    if (!order.isPresent()) {
      for (Entry<String, Date> e : state.completedOrders.entrySet()) {
        if (e.getValue().getTime() + 5 * 60 * 1000 < Date.from(Instant.now()).getTime()) {
          state.completedOrders.remove(e.getKey());
        }
      }
      state.completedOrders.put(orderId, Date.from(Instant.now()));
    } else {
      state.orders.removeIf(o -> o.id.equals(orderId));
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onSymbolSpecificationsUpdated(String instanceIndex,
    List<MetatraderSymbolSpecification> specifications, List<String> removedSymbols) {
    State state = getState(instanceIndex);
    for (MetatraderSymbolSpecification specification : specifications) {
      int index = -1;
      for (int i = 0; i < state.specifications.size(); ++i) {
        if (state.specifications.get(i).symbol.equals(specification.symbol)) {
          index = i;
          break;
        }
      }
      if (index != -1) {
        state.specifications.set(index, specification);
      } else {
        state.specifications.add(specification);
      }
      state.specificationsBySymbol.put(specification.symbol, specification);
    }
    state.specifications = state.specifications.stream()
      .filter(s -> !removedSymbols.contains(s.symbol)).collect(Collectors.toList());
    for (String symbol : removedSymbols) {
      state.specificationsBySymbol.remove(symbol);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onSymbolPricesUpdated(String instanceIndex,
    List<MetatraderSymbolPrice> prices, Double equity, Double margin, Double freeMargin,
    Double marginLevel, Double accountCurrencyExchangeRate) {
    State state = getState(instanceIndex);
    state.lastUpdateTime = 0;
    for (MetatraderSymbolPrice price : prices) {
      if (price.time.getDate().getTime() > state.lastUpdateTime) {
        state.lastUpdateTime = price.time.getDate().getTime();
      }
    }
    boolean pricesInitialized = false;
    for (MetatraderSymbolPrice price : prices) {
      state.pricesBySymbol.put(price.symbol, price);
      List<MetatraderPosition> positions = state.positions.stream()
        .filter(p -> p.symbol.equals(price.symbol)).collect(Collectors.toList());
      List<MetatraderPosition> otherPositions = state.positions.stream()
        .filter(p -> !p.symbol.equals(price.symbol)).collect(Collectors.toList());
      List<MetatraderOrder> orders = state.orders.stream()
          .filter(o -> o.symbol.equals(price.symbol)).collect(Collectors.toList());
      pricesInitialized = true;
      for (MetatraderPosition position : otherPositions) {
        MetatraderSymbolPrice p = state.pricesBySymbol.get(position.symbol);
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
      List<CompletableFuture<Void>> priceResolves = waitForPriceResolves.get(price.symbol);
      if (priceResolves != null && priceResolves.size() > 0) {
        for (CompletableFuture<Void> resolve : priceResolves) {
          resolve.complete(null);
        }
        waitForPriceResolves.remove(price.symbol);
      }
    }
    if (state.accountInformation != null) {
      if (state.positionsInitialized && pricesInitialized) {
        String platform = state.accountInformation.platform;
        if (platform != null && platform.equals("mt5")) {
          state.accountInformation.equity = equity != null ? equity : state.accountInformation.balance +
            Js.reduce(state.positions, (acc, p) -> acc + 
              Math.round(Js.or(p.unrealizedProfit, 0.0) * 100.0) / 100.0 + Math.round(Js.or(p.swap, 0.0) * 100.0) / 100.0, 0.0);
        } else {
          state.accountInformation.equity = equity != null ? equity : state.accountInformation.balance +
            Js.reduce(state.positions, (acc, p) -> acc + Math.round(Js.or(p.swap, 0.0) * 100) / 100 +
              Math.round(Js.or(p.commission, 0.0) * 100) / 100 + Math.round(Js.or(p.unrealizedProfit, 0.0) * 100) / 100, 0.0);
        }
        state.accountInformation.equity = Math.round(state.accountInformation.equity * 100.0) / 100.0;
      } else {
        state.accountInformation.equity = equity != null ? equity : state.accountInformation.equity;
      }
      state.accountInformation.margin = margin != null ? margin : state.accountInformation.margin;
      state.accountInformation.freeMargin = freeMargin != null ? freeMargin : state.accountInformation.freeMargin;
      state.accountInformation.marginLevel = freeMargin != null ? marginLevel : state.accountInformation.marginLevel;
    }
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onStreamClosed(String instanceIndex) {
    stateByInstanceIndex.remove(instanceIndex);
    return CompletableFuture.completedFuture(null);
  }
  
  private void updatePositionProfits(MetatraderPosition position, MetatraderSymbolPrice price) {
    Optional<MetatraderSymbolSpecification> specification = getSpecification(position.symbol);
    if (specification.isPresent()) {
      double multiplier = Math.pow(10, specification.get().digits);
      if (position.profit != null) {
        position.profit = Math.round(position.profit * multiplier) / multiplier;
      }
      if (position.unrealizedProfit == null || position.realizedProfit == null) {
        position.unrealizedProfit = 
          (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
          (position.currentPrice - position.openPrice) * position.currentTickValue *
          position.volume / specification.get().tickSize;
        position.unrealizedProfit = Math.round(position.unrealizedProfit * multiplier) / multiplier;
        position.realizedProfit = position.profit - position.unrealizedProfit;
      }
      double newPositionPrice = (position.type == PositionType.POSITION_TYPE_BUY ? price.bid : price.ask);
      double isProfitable = (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
        (newPositionPrice - position.openPrice);
      double currentTickValue = (isProfitable > 0 ? price.profitTickValue : price.lossTickValue);
      double unrealizedProfit = (position.type == PositionType.POSITION_TYPE_BUY ? 1 : -1) *
        (newPositionPrice - position.openPrice) * currentTickValue *
        position.volume / specification.get().tickSize;
      unrealizedProfit = Math.round(unrealizedProfit * multiplier) / multiplier;
      position.unrealizedProfit = unrealizedProfit;
      position.profit = position.unrealizedProfit + position.realizedProfit;
      position.profit = Math.round(position.profit * multiplier) / multiplier;
      position.currentPrice = newPositionPrice;
      position.currentTickValue = currentTickValue;
    }
  }
  
  private State getState(String instanceIndex) {
    if (!stateByInstanceIndex.containsKey(instanceIndex)) {
      stateByInstanceIndex.put(instanceIndex, constructTerminalState());
    }
    return stateByInstanceIndex.get(instanceIndex);
  }
  
  private State constructTerminalState() {
    return new State();
  }
  
  private State getBestState() {
    State result = null;
    Long maxUpdateTime = null;
    for (State state : stateByInstanceIndex.values()) {
      if (maxUpdateTime == null || maxUpdateTime < state.lastUpdateTime) {
        maxUpdateTime = state.lastUpdateTime;
        result = state;
      }
    }
    return result != null ? result : constructTerminalState();
  }
}