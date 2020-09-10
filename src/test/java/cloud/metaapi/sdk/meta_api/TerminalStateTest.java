package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * Tests {@link TerminalState}
 */
class TerminalStateTest {

    private TerminalState state;
    
    @BeforeEach
    void setUp() {
        state = new TerminalState();
    }
    
    /**
     * Tests 
     * {@link TerminalState#onConnected()},
     * {@link TerminalState#onDisconnected()},
     * {@link TerminalState#isConnected()}
     */
    @Test
    void testReturnsConnectionState() {
        assertFalse(state.isConnected());
        state.onConnected();
        assertTrue(state.isConnected());
        state.onDisconnected();
        assertFalse(state.isConnected());
    }
    
    /**
     * Tests 
     * {@link TerminalState#onBrokerConnectionStatusChanged(boolean)},
     * {@link TerminalState#isConnectedToBroker()}
     */
    @Test
    void testReturnsBrokerConnectionState() {
        assertFalse(state.isConnectedToBroker());
        state.onBrokerConnectionStatusChanged(true);
        assertTrue(state.isConnectedToBroker());
        state.onBrokerConnectionStatusChanged(false);
        assertFalse(state.isConnectedToBroker());
        state.onBrokerConnectionStatusChanged(true);
        state.onDisconnected();
        assertFalse(state.isConnectedToBroker());
    }
    
    /**
     * Tests {@link TerminalState#onBrokerConnectionStatusChanged(boolean)}
     */
    @Test
    void testCallsDisconnectEventIfThereWasNoSignalForALongTime() {
        TerminalState state = new TerminalState() {{ statusTimerTimeoutInMilliseconds = 200; }};
        assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
            state.onBrokerConnectionStatusChanged(true);
            Thread.sleep(500);
            assertFalse(state.isConnectedToBroker());
            assertFalse(state.isConnected());
        });
    }
    
    /**
     * Tests
     * {@link TerminalState#onAccountInformationUpdated(MetatraderAccountInformation)},
     * {@link TerminalState#getAccountInformation()}
     */
    @Test
    void testReturnsAccountInformation() {
        MetatraderAccountInformation expected = new MetatraderAccountInformation() {{ balance = 1000; }};
        assertTrue(state.getAccountInformation().isEmpty());
        state.onAccountInformationUpdated(expected);
        assertEquals(expected, state.getAccountInformation().get());
    }
    
    /**
     * Tests
     * {@link TerminalState#onPositionUpdated(MetatraderPosition)},
     * {@link TerminalState#onPositionRemoved(String)},
     * {@link TerminalState#getPositions()}
     */
    @Test
    void testReturnsPositions() {
        assertTrue(state.getPositions().isEmpty());
        state.onPositionUpdated(new MetatraderPosition() {{ id = "1"; profit = 10; }});
        state.onPositionUpdated(new MetatraderPosition() {{ id = "2"; }});
        state.onPositionUpdated(new MetatraderPosition() {{ id = "1"; profit = 11; }});
        state.onPositionRemoved("2");
        assertEquals(1, state.getPositions().size());
        assertThat(state.getPositions()).usingRecursiveComparison().isEqualTo(List.of(
            new MetatraderPosition() {{ id = "1"; profit = 11; }}
        ));
    }
    
    /**
     * Tests
     * {@link TerminalState#onOrderUpdated(MetatraderOrder)},
     * {@link TerminalState#onOrderCompleted(String)},
     * {@link TerminalState#getOrders()}
     */
    @Test
    void testReturnsOrders() {
        assertTrue(state.getOrders().isEmpty());
        state.onOrderUpdated(new MetatraderOrder() {{ id = "1"; openPrice = 10.0; }});
        state.onOrderUpdated(new MetatraderOrder() {{ id = "2"; }});
        state.onOrderUpdated(new MetatraderOrder() {{ id = "1"; openPrice = 11.0; }});
        state.onOrderCompleted("2");
        assertEquals(1, state.getOrders().size());
        assertThat(state.getOrders()).usingRecursiveComparison().isEqualTo(List.of(
            new MetatraderOrder() {{ id = "1"; openPrice = 11.0; }}
        ));
    }
    
    /**
     * Tests
     * {@link TerminalState#onSymbolSpecificationUpdated(MetatraderSymbolSpecification)},
     * {@link TerminalState#getSpecifications()},
     * {@link TerminalState#getSpecification(String)}
     */
    @Test
    void testReturnsSpecifications() {
        assertTrue(state.getSpecifications().isEmpty());
        state.onSymbolSpecificationUpdated(new MetatraderSymbolSpecification() {{ symbol = "EURUSD"; tickSize = 0.00001; }});
        state.onSymbolSpecificationUpdated(new MetatraderSymbolSpecification() {{ symbol = "GBPUSD"; }});
        state.onSymbolSpecificationUpdated(new MetatraderSymbolSpecification() {{ symbol = "EURUSD"; tickSize = 0.0001; }});
        assertEquals(2, state.getSpecifications().size());
        assertThat(state.getSpecifications()).usingRecursiveComparison().isEqualTo(List.of(
            new MetatraderSymbolSpecification() {{ symbol = "EURUSD"; tickSize = 0.0001; }},
            new MetatraderSymbolSpecification() {{ symbol = "GBPUSD"; }}
        ));
        assertThat(state.getSpecification("EURUSD").get()).usingRecursiveComparison().isEqualTo(
            new MetatraderSymbolSpecification() {{ symbol = "EURUSD"; tickSize = 0.0001; }}
        );
    }
    
    /**
     * Tests
     * {@link TerminalState#onSymbolPriceUpdated(MetatraderSymbolPrice)},
     * {@link TerminalState#getPrice(String)}
     */
    @Test
    void testReturnsPrice() {
        assertTrue(state.getPrice("EURUSD").isEmpty());
        state.onSymbolPriceUpdated(new MetatraderSymbolPrice() {{ symbol = "EURUSD"; bid = 1; ask = 1.1; }});
        state.onSymbolPriceUpdated(new MetatraderSymbolPrice() {{ symbol = "GBPUSD"; }});
        state.onSymbolPriceUpdated(new MetatraderSymbolPrice() {{ symbol = "EURUSD"; bid = 1; ask = 1.2; }});
        assertThat(state.getPrice("EURUSD").get()).usingRecursiveComparison().isEqualTo(new MetatraderSymbolPrice() {{
            symbol = "EURUSD"; bid = 1; ask = 1.2;
        }});
    }
    
    /**
     * Tests
     * {@link TerminalState#onSymbolPriceUpdated(MetatraderSymbolPrice)},
     * {@link TerminalState#getAccountInformation()},
     * {@link TerminalState#getPositions()}
     */
    @Test
    void testUpdatesAccountEquityAndPositionProfitOnPriceUpdate() {
        state.onAccountInformationUpdated(new MetatraderAccountInformation() {{ equity = 1000; }});
        state.onPositionUpdated(new MetatraderPosition() {{
            id = "1";
            symbol = "EURUSD";
            type = PositionType.POSITION_TYPE_BUY;
            currentPrice = 9;
            currentTickValue = 0.5;
            openPrice = 8;
            profit = 100;
            volume = 2;
        }});
        state.onPositionUpdated(new MetatraderPosition() {{
            id = "2";
            symbol = "AUDUSD";
            type = PositionType.POSITION_TYPE_BUY;
            currentPrice = 9;
            currentTickValue = 0.5;
            openPrice = 8;
            profit = 100;
            volume = 10;
        }});
        state.onSymbolSpecificationUpdated(new MetatraderSymbolSpecification() {{ 
            symbol = "EURUSD";
            tickSize = 0.01;
        }});
        state.onSymbolPriceUpdated(new MetatraderSymbolPrice() {{
            symbol = "EURUSD";
            profitTickValue = 0.5;
            lossTickValue = 0.5;
            bid = 10;
            ask = 11;
        }});
        assertThat(state.getPositions().stream().map(position -> position.profit).toArray())
            .isEqualTo(Stream.of(200.0, 100.0).toArray());
        assertThat(state.getPositions().stream().map(position -> position.currentPrice).toArray())
            .isEqualTo(Stream.of(10.0, 9.0).toArray());
        assertEquals(1100, state.getAccountInformation().get().equity);
    }
    
    /**
     * Tests
     * {@link TerminalState#onSymbolPriceUpdated(MetatraderSymbolPrice)},
     * {@link TerminalState#getOrders()}
     */
    @Test
    void testUpdatesOrderCurrentPriceOnPriceUpdate() {
        state.onOrderUpdated(new MetatraderOrder() {{
            id = "1";
            symbol = "EURUSD";
            type = OrderType.ORDER_TYPE_BUY_LIMIT;
            currentPrice = 9;
        }});
        state.onOrderUpdated(new MetatraderOrder() {{
            id = "2";
            symbol = "AUDUSD";
            type = OrderType.ORDER_TYPE_SELL_LIMIT;
            currentPrice = 9;
        }});
        state.onSymbolSpecificationUpdated(new MetatraderSymbolSpecification() {{
            symbol = "EURUSD";
            tickSize = 0.01;
        }});
        state.onSymbolPriceUpdated(new MetatraderSymbolPrice() {{
            symbol = "EURUSD";
            profitTickValue = 0.5;
            lossTickValue = 0.5;
            bid = 10;
            ask = 11;
        }});
        assertThat(state.getOrders().stream().map(order -> order.currentPrice).toArray())
            .isEqualTo(Stream.of(11.0, 9.0).toArray());
    }
}