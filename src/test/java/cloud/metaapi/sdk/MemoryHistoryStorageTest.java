package cloud.metaapi.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.clients.models.MetatraderDeal.DealType;
import cloud.metaapi.sdk.clients.models.MetatraderOrder.OrderType;

/**
 * Tests {@link MemoryHistoryStorage}
 */
class MemoryHistoryStorageTest {

    private static MemoryHistoryStorage storage;
    
    @BeforeAll
    static void setUpBeforeClass() {
        storage = new MemoryHistoryStorage();
    }
    
    @BeforeEach
    void setUp() {
        storage.reset();
        storage.onConnected();
    }

    /**
     * Tests {@link MemoryHistoryStorage#getLastHistoryOrderTime()}
     */
    @Test
    void testReturnsLastHistoryOrderTime() throws Exception {
        storage.onHistoryOrderAdded(createOrder(null));
        storage.onHistoryOrderAdded(createOrder("2020-01-01T00:00:00.000Z"));
        storage.onHistoryOrderAdded(createOrder("2020-01-02T00:00:00.000Z"));
        assertThat(storage.getLastHistoryOrderTime().get()).usingRecursiveComparison()
            .isEqualTo(new IsoTime("2020-01-02T00:00:00.000Z"));
    }
    
    /**
     * Tests {@link MemoryHistoryStorage#getLastDealTime()}
     */
    @Test
    void testReturnsLastHistoryDealTime() throws Exception {
        storage.onDealAdded(new MetatraderDeal() {{ time = new IsoTime(Date.from(Instant.ofEpochSecond(0))); }});
        storage.onDealAdded(new MetatraderDeal() {{ time = new IsoTime("2020-01-01T00:00:00.000Z"); }});
        storage.onDealAdded(new MetatraderDeal() {{ time = new IsoTime("2020-01-02T00:00:00.000Z"); }});
        assertThat(storage.getLastDealTime().get()).usingRecursiveComparison()
            .isEqualTo(new IsoTime("2020-01-02T00:00:00.000Z"));
    }
    
    /**
     * Tests {@link MemoryHistoryStorage#getDeals()}
     */
    @Test
    void testReturnsSavedDeals() {
        storage.onDealAdded(createDeal("1", "2020-01-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("7", "2020-05-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY));
        storage.onDealAdded(createDeal("8", "2020-02-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("6", "2020-10-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY));
        storage.onDealAdded(createDeal("4", "2020-02-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("5", "2020-06-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY));
        storage.onDealAdded(createDeal("11", null, DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("3", "2020-09-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY));
        storage.onDealAdded(createDeal("5", "2020-06-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY));
        storage.onDealAdded(createDeal("2", "2020-08-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("10", null,  DealType.DEAL_TYPE_SELL));
        storage.onDealAdded(createDeal("12", null,  DealType.DEAL_TYPE_BUY));
        assertThat(storage.getDeals()).usingRecursiveComparison().isEqualTo(List.of(
            createDeal("10", null,  DealType.DEAL_TYPE_SELL),
            createDeal("11", null, DealType.DEAL_TYPE_SELL),
            createDeal("12", null,  DealType.DEAL_TYPE_BUY),
            createDeal("1", "2020-01-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL),
            createDeal("4", "2020-02-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL),
            createDeal("8", "2020-02-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL),
            createDeal("7", "2020-05-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY),
            createDeal("5", "2020-06-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY),
            createDeal("2", "2020-08-01T00:00:00.000Z", DealType.DEAL_TYPE_SELL),
            createDeal("3", "2020-09-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY),
            createDeal("6", "2020-10-01T00:00:00.000Z", DealType.DEAL_TYPE_BUY)
        ));
    }
    
    /**
     * Tests {@link MemoryHistoryStorage#getHistoryOrders()}
     */
    @Test
    void testReturnsSavedHistoryOrders() {
        storage.onHistoryOrderAdded(createOrder("1", "2020-01-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("7", "2020-05-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY));
        storage.onHistoryOrderAdded(createOrder("8", "2020-02-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("6", "2020-10-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY));
        storage.onHistoryOrderAdded(createOrder("4", "2020-02-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("5", "2020-06-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY));
        storage.onHistoryOrderAdded(createOrder("11", null, OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("3", "2020-09-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY));
        storage.onHistoryOrderAdded(createOrder("5", "2020-06-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY));
        storage.onHistoryOrderAdded(createOrder("2", "2020-08-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("10", null,  OrderType.ORDER_TYPE_SELL));
        storage.onHistoryOrderAdded(createOrder("12", null,  OrderType.ORDER_TYPE_BUY));
        assertThat(storage.getHistoryOrders()).usingRecursiveComparison().isEqualTo(List.of(
            createOrder("10", null,  OrderType.ORDER_TYPE_SELL),
            createOrder("11", null, OrderType.ORDER_TYPE_SELL),
            createOrder("12", null,  OrderType.ORDER_TYPE_BUY),
            createOrder("1", "2020-01-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL),
            createOrder("4", "2020-02-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL),
            createOrder("8", "2020-02-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL),
            createOrder("7", "2020-05-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY),
            createOrder("5", "2020-06-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY),
            createOrder("2", "2020-08-01T00:00:00.000Z", OrderType.ORDER_TYPE_SELL),
            createOrder("3", "2020-09-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY),
            createOrder("6", "2020-10-01T00:00:00.000Z", OrderType.ORDER_TYPE_BUY)
        ));
    }
    
    /**
     * Tests {@link MemoryHistoryStorage#isOrderSynchronizationFinished()}
     */
    @Test
    void testReturnsSavedOrderSynchronizationStatus() {
        assertFalse(storage.isOrderSynchronizationFinished());
        storage.onOrderSynchronizationFinished();
        assertTrue(storage.isOrderSynchronizationFinished());
    }
    
    /**
     * Tests {@link MemoryHistoryStorage#isDealSynchronizationFinished()}
     */
    @Test
    void testReturnsSavedDealSynchronizationStatus() {
        assertFalse(storage.isDealSynchronizationFinished());
        storage.onDealSynchronizationFinished();
        assertTrue(storage.isDealSynchronizationFinished());
    }
    
    private MetatraderOrder createOrder(String doneIsoTime) {
        return new MetatraderOrder() {{
            this.doneTime = Optional.ofNullable(doneIsoTime != null ? new IsoTime(doneIsoTime) : null);
        }};
    }
    
    private MetatraderOrder createOrder(String id, String doneIsoTime, OrderType type) {
        MetatraderOrder result = new MetatraderOrder();
        result.id = id;
        result.doneTime = Optional.ofNullable(doneIsoTime != null ? new IsoTime(doneIsoTime) : null); 
        result.type = type;
        return result;
    }
    
    private MetatraderDeal createDeal(String id, String isoTime, DealType type) {
        MetatraderDeal result = new MetatraderDeal();
        result.id = id;
        result.time = (isoTime != null ? new IsoTime(isoTime) : new IsoTime(Date.from(Instant.ofEpochSecond(0)))); 
        result.type = type;
        return result;
    }
}