package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.meta_api.HistoryFileManager.History;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link HistoryFileManager}
 */
public class HistoryFileManagerTest {
    
    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private HistoryStorage storage;
    private HistoryFileManager fileManager;
    private MetatraderDeal testDeal;
    private MetatraderDeal testDeal2;
    private MetatraderDeal testDeal3;
    private MetatraderOrder testOrder;
    private MetatraderOrder testOrder2;
    private MetatraderOrder testOrder3;
    
    @BeforeAll
    static void setUpBeforeClass() throws IOException {
        Files.createDirectories(Path.of("./.metaapi"));
    }
    
    @AfterAll
    static void tearDownAfterClass() throws IOException {
        FileUtils.deleteDirectory(new File("./.metaapi"));
    }
    
    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Path.of("./.metaapi"));
        storage = Mockito.mock(HistoryStorage.class);
        fileManager = Mockito.spy(new HistoryFileManager("accountId", storage) {{
            updateJobIntervalInMilliseconds = 500; }});
        testDeal = new MetatraderDeal() {{ id = "37863643"; type = DealType.DEAL_TYPE_BALANCE; magic = 0;
            time = new IsoTime(new Date(100)); commission = 0.0; swap = 0.0; profit = 10000;
            platform = "mt5"; comment = "Demo deposit 1"; }};
        testDeal2 = new MetatraderDeal() {{ id = "37863644"; type = DealType.DEAL_TYPE_SELL; magic = 1;
            time = new IsoTime(new Date(200)); commission = 0.0; swap = 0.0; profit = 10000;
            platform = "mt5"; comment = "Demo deposit 2"; }};
        testDeal3 = new MetatraderDeal() {{ id = "37863645"; type = DealType.DEAL_TYPE_BUY; magic = 2;
            time = new IsoTime(new Date(300)); commission = 0.0; swap = 0.0; profit = 10000;
            platform = "mt5"; comment = "Demo deposit 3"; }};
        testOrder = new MetatraderOrder() {{ id = "61210463"; type = OrderType.ORDER_TYPE_SELL;
            state = OrderState.ORDER_STATE_FILLED; symbol = "AUDNZD"; magic = 0; time = new IsoTime(new Date(50));
            doneTime = new IsoTime(new Date(100)); currentPrice = 1; volume = 0.01; currentVolume = 0;
            positionId = "61206630"; platform = "mt5"; comment = "AS_AUDNZD_5YyM6KS7Fv:"; }};
        testOrder2 = new MetatraderOrder() {{ id = "61210464"; type = OrderType.ORDER_TYPE_BUY_LIMIT;
            state = OrderState.ORDER_STATE_FILLED; symbol = "AUDNZD"; magic = 1; time = new IsoTime(new Date(75));
            doneTime = new IsoTime(new Date(200)); currentPrice = 1; volume = 0.01; currentVolume = 0;
            positionId = "61206631"; platform = "mt5"; comment = "AS_AUDNZD_5YyM6KS7Fv:"; }};
        testOrder3 = new MetatraderOrder() {{ id = "61210465"; type = OrderType.ORDER_TYPE_BUY;
            state = OrderState.ORDER_STATE_FILLED; symbol = "AUDNZD"; magic = 1; time = new IsoTime(new Date(100));
            doneTime = new IsoTime(new Date(300)); currentPrice = 1; volume = 0.01; currentVolume = 0;
            positionId = "61206631"; platform = "mt5"; comment = "AS_AUDNZD_5YyM6KS7Fv:"; }};
    }
    
    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Path.of("./.metaapi/accountId-deals.bin"));
        Files.deleteIfExists(Path.of("./.metaapi/accountId-historyOrders.bin"));
    }
    
    /**
     * Tests
     * {@link HistoryFileManager#startUpdateJob()},
     * {@link HistoryFileManager#stopUpdateJob()}
     */
    @Test
    void testStartsAndStopsJob() throws InterruptedException {
        Mockito.doReturn(CompletableFuture.completedFuture(null)).when(fileManager).updateDiskStorage();
        fileManager.startUpdateJob();
        Thread.sleep(510);
        Mockito.verify(fileManager, Mockito.times(1)).updateDiskStorage();
        Thread.sleep(510);
        Mockito.verify(fileManager, Mockito.times(2)).updateDiskStorage();
        fileManager.stopUpdateJob();
        Thread.sleep(510);
        Mockito.verify(fileManager, Mockito.times(2)).updateDiskStorage();
        fileManager.startUpdateJob();
        Thread.sleep(510);
        Mockito.verify(fileManager, Mockito.times(3)).updateDiskStorage();
        fileManager.stopUpdateJob();
    }
    
    /**
     * Tests {@link HistoryFileManager#getHistoryFromDisk()}
     */
    @Test
    void testReadsHistoryFromFile() throws Exception {
        Files.write(Path.of("./.metaapi/accountId-deals.bin"), jsonMapper.writeValueAsBytes(List.of(testDeal)));
        Files.write(Path.of("./.metaapi/accountId-historyOrders.bin"), jsonMapper.writeValueAsBytes(List.of(testOrder)));
        History history = fileManager.getHistoryFromDisk().get();
        assertThat(history.deals).usingRecursiveComparison().isEqualTo(List.of(testDeal));
        assertThat(history.historyOrders).usingRecursiveComparison().isEqualTo(List.of(testOrder));
    }
    
    /**
     * Tests {@link HistoryFileManager#updateDiskStorage()}
     */
    @Test
    void testSavesItemsInAFile() 
        throws InterruptedException, ExecutionException, 
            JsonMappingException, JsonProcessingException, IOException
    {
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2));
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().get();
        History savedData = readHistoryStorageFile();
        assertThat(savedData.deals).usingRecursiveComparison().isEqualTo(List.of(testDeal, testDeal2));
        assertThat(savedData.historyOrders).usingRecursiveComparison().isEqualTo(List.of(testOrder, testOrder2));
    }
    
    /**
     * Tests {@link HistoryFileManager#updateDiskStorage()}
     */
    @Test
    void testReplacesNthItemInAFile() 
        throws InterruptedException, ExecutionException,
            JsonMappingException, JsonProcessingException, IOException
    {
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2));
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().get();
        testDeal2.magic = 100;
        testOrder2.magic = 100;
        fileManager.setStartNewDealIndex(1);
        fileManager.setStartNewOrderIndex(1);
        fileManager.updateDiskStorage().get();
        History savedData = readHistoryStorageFile();
        assertThat(savedData.deals).usingRecursiveComparison().isEqualTo(List.of(testDeal, testDeal2));
        assertThat(savedData.historyOrders).usingRecursiveComparison().isEqualTo(List.of(testOrder, testOrder2));
    }
    
    /**
     * Tests {@link HistoryFileManager#updateDiskStorage()}
     */
    @Test
    void testReplacesAllItemsInAFile()
        throws InterruptedException, ExecutionException,
            JsonMappingException, JsonProcessingException, IOException
    {
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2));
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().get();
        testDeal.magic = 100;
        testDeal2.magic = 100;
        testOrder.magic = 100;
        testOrder2.magic = 100;
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().get();
        History savedData = readHistoryStorageFile();
        assertThat(savedData.deals).usingRecursiveComparison().isEqualTo(List.of(testDeal, testDeal2));
        assertThat(savedData.historyOrders).usingRecursiveComparison().isEqualTo(List.of(testOrder, testOrder2));
    }
    
    /**
     * Tests {@link HistoryFileManager#updateDiskStorage()}
     */
    @Test
    void testAppendsANewObjectToAreadySavedOnes()
        throws InterruptedException, ExecutionException,
            JsonMappingException, JsonProcessingException, IOException
    {
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2));
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().get();
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2, testDeal3));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2, testOrder3));
        fileManager.setStartNewDealIndex(2);
        fileManager.setStartNewOrderIndex(2);
        fileManager.updateDiskStorage().get();
        History savedData = readHistoryStorageFile();
        assertThat(savedData.deals).usingRecursiveComparison().isEqualTo(List.of(testDeal, testDeal2, testDeal3));
        assertThat(savedData.historyOrders).usingRecursiveComparison().isEqualTo(List.of(testOrder, testOrder2, testOrder3));
    }
    
    /**
     * Tests {@link HistoryFileManager#updateDiskStorage()}
     */
    @Test
    void testDoesNotCorruptsTheDiskStorageIfUpdateCalledMultipleTimes() throws JsonProcessingException, IOException {
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2));
        fileManager.setStartNewDealIndex(0);
        fileManager.setStartNewOrderIndex(0);
        fileManager.updateDiskStorage().join();
        Mockito.when(storage.getDeals()).thenReturn(List.of(testDeal, testDeal2, testDeal3));
        Mockito.when(storage.getHistoryOrders()).thenReturn(List.of(testOrder, testOrder2, testOrder3));
        fileManager.setStartNewDealIndex(2);
        fileManager.setStartNewOrderIndex(2);
        CompletableFuture.allOf(
            fileManager.updateDiskStorage(),
            fileManager.updateDiskStorage(),
            fileManager.updateDiskStorage(),
            fileManager.updateDiskStorage(),
            fileManager.updateDiskStorage()
        ).join();
        JsonMapper.getInstance().readTree(new File("./.metaapi/accountId-historyOrders.bin"));
    }
    
    /**
     * Tests {@link HistoryFileManager#deleteStorageFromDisk()}
     */
    @Test
    void testRemovesHistoryFromDisk() throws IOException, InterruptedException, ExecutionException {
        Files.createFile(Path.of("./.metaapi/accountId-deals.bin"));
        Files.createFile(Path.of("./.metaapi/accountId-historyOrders.bin"));
        assertTrue(Files.exists(Path.of("./.metaapi/accountId-deals.bin")));
        assertTrue(Files.exists(Path.of("./.metaapi/accountId-historyOrders.bin")));
        fileManager.deleteStorageFromDisk().get();
        assertFalse(Files.exists(Path.of("./.metaapi/accountId-deals.bin")));
        assertFalse(Files.exists(Path.of("./.metaapi/accountId-historyOrders.bin")));
    }
    
    /**
     * Helper function to read saved history storage
     */
    private History readHistoryStorageFile() throws JsonMappingException, JsonProcessingException, IOException {
        History result = new History();
        Path dealsPath = Path.of("./.metaapi/accountId-deals.bin");
        Path historyOrdersPath = Path.of("./.metaapi/accountId-historyOrders.bin");
        result.deals = Files.exists(dealsPath)
            ? Arrays.asList(jsonMapper.readValue(Files.readString(dealsPath), MetatraderDeal[].class))
            : List.of();
        result.historyOrders = Files.exists(historyOrdersPath)
            ? Arrays.asList(jsonMapper.readValue(Files.readString(historyOrdersPath), MetatraderOrder[].class))
            : List.of();
        return result;
    }
}