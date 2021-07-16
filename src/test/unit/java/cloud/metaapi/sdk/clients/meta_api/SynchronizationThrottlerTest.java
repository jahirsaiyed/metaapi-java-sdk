package cloud.metaapi.sdk.clients.meta_api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.JsonMapper;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link SynchronizationThrottler}
 */
class SynchronizationThrottlerTest {

  private SynchronizationThrottler throttler;
  private MetaApiWebsocketClient websocketClient;
  
  @BeforeEach
  void setUp() throws Exception {
    ServiceProvider.setNowInstantMock(new IsoTime("2020-10-05T10:00:00.000Z").getDate().toInstant());
    websocketClient = Mockito.mock(MetaApiWebsocketClient.class);
    Mockito.when(websocketClient.rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(websocketClient.getSubscribedAccountIds(Mockito.anyInt())).thenReturn(provideListOfSize(11));
    SynchronizationThrottler socketInstanceSyncThrottler = Mockito.mock(SynchronizationThrottler.class);
    Mockito.when(socketInstanceSyncThrottler.getSynchronizingAccounts()).thenReturn(Arrays.asList());
    Mockito.when(websocketClient.getSocketInstances()).thenReturn(Arrays.asList(
      new MetaApiWebsocketClient.SocketInstance() {{ synchronizationThrottler = socketInstanceSyncThrottler; }}
    ));
    throttler = new SynchronizationThrottler(websocketClient, 0, new SynchronizationThrottler.Options());
    throttler.start();
  }
  
  @AfterEach
  void tearDown() {
	throttler.stop();
    ServiceProvider.reset();
  }

  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testImmediatelySendsRequestIfFreeSlotsExist() {
    throttler.scheduleSynchronize("accountId", provideRequest("test")).join();
    Assertions.assertThat(throttler.synchronizationIds).usingRecursiveComparison()
      .isEqualTo(Maps.newHashMap("test", 1601892000000L));
    throttler.removeSynchronizationId("test");
    Mockito.verify(websocketClient).rpcRequest("accountId", provideRequest("test"), null);
    Assertions.assertThat(throttler.synchronizationIds).usingRecursiveComparison()
      .isEqualTo(new HashMap<>());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testDoesNotRemovesSyncIfDifferentInstanceIndex() {
    throttler.scheduleSynchronize("accountId", provideRequest("test", 0)).join();
    throttler.scheduleSynchronize("accountId", provideRequest("test1", 1)).join();
    Map<String, Long> expectedSynchronizationIds = new HashMap<>();
    expectedSynchronizationIds.put("test", 1601892000000L);
    expectedSynchronizationIds.put("test1", 1601892000000L);
    Assertions.assertThat(throttler.synchronizationIds).usingRecursiveComparison()
      .isEqualTo(expectedSynchronizationIds);
    throttler.removeSynchronizationId("test");
    Assertions.assertThat(throttler.synchronizationIds).usingRecursiveComparison()
      .isEqualTo(Maps.newHashMap("test1", 1601892000000L));
    Mockito.verify(websocketClient).rpcRequest("accountId", provideRequest("test", 0), null);
    Mockito.verify(websocketClient).rpcRequest("accountId", provideRequest("test1", 1), null);
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testWaitsForOtherSyncRequestsToFinishIfSlotsAreFull() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    Mockito.verify(websocketClient).rpcRequest("accountId1", provideRequest("test1"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId2", provideRequest("test2"), null);
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    throttler.removeSynchronizationId("test1");
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize}
   */
  @Test
  void testIncreasesSlotAmountWithMoreSubscribedAccounts() {
    Mockito.when(websocketClient.getSubscribedAccountIds(Mockito.anyInt())).thenReturn(provideListOfSize(21));
    ObjectNode request1 = provideRequest("test1");
    ObjectNode request2 = provideRequest("test2");
    ObjectNode request3 = provideRequest("test3");
    throttler.scheduleSynchronize("accountId1", request1).join();
    throttler.scheduleSynchronize("accountId2", request2).join();
    throttler.scheduleSynchronize("accountId3", request3).join();
    Mockito.verify(websocketClient).rpcRequest(Mockito.eq("accountId1"), Mockito.eq(request1), Mockito.any());
    Mockito.verify(websocketClient).rpcRequest(Mockito.eq("accountId2"), Mockito.eq(request2), Mockito.any());
    Mockito.verify(websocketClient).rpcRequest(Mockito.eq("accountId3"), Mockito.eq(request3), Mockito.any());
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize}
   */
  @Test
  void testSetsHardLimitForConcurrentSynchronizationsViaOptions() throws Exception {
    Mockito.when(websocketClient.getSubscribedAccountIds(Mockito.anyInt())).thenReturn(provideListOfSize(21));
    throttler = new SynchronizationThrottler(websocketClient, 0, new SynchronizationThrottler.Options() {{
      maxConcurrentSynchronizations = 3;
    }});
    SynchronizationThrottler socketInstanceSyncThrottler = Mockito.mock(SynchronizationThrottler.class);
    Mockito.when(socketInstanceSyncThrottler.getSynchronizingAccounts()).thenReturn(Arrays.asList("accountId4"));
    Mockito.when(websocketClient.getSocketInstances()).thenReturn(Arrays.asList(
      new MetaApiWebsocketClient.SocketInstance() {{ synchronizationThrottler = throttler; }},
      new MetaApiWebsocketClient.SocketInstance() {{ synchronizationThrottler = socketInstanceSyncThrottler; }}
    ));
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    Thread.sleep(50);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    throttler.removeSynchronizationId("test1");
    Thread.sleep(50);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  };
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testDoesNotTakeExtraSlotsIfSyncIdsBelongToTheSameAccount() throws InterruptedException {
    throttler.scheduleSynchronize("accountId", provideRequest("test", 0));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId", provideRequest("test1", 1));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId2", provideRequest("test2"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    Mockito.verify(websocketClient).rpcRequest("accountId", provideRequest("test", 0), null);
    Mockito.verify(websocketClient).rpcRequest("accountId", provideRequest("test1", 1), null);
    Mockito.verify(websocketClient).rpcRequest("accountId2", provideRequest("test2"), null);
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testClearsExpiredSynchronizationSlotsIfNoPacketsFor10Seconds() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    tick(11000);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#updateSynchronizationId(String)}
   */
  @Test
  void testRenewsSyncOnUpdate() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(50);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    tick(11000);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    tick(11000);
    throttler.updateSynchronizationId("test1");
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId5", provideRequest("test5"));
    Thread.sleep(50);
    Mockito.verify(websocketClient, Mockito.times(4)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testReplacesPreviousSyncs() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId1", provideRequest("test2"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId1", provideRequest("test3"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId2", provideRequest("test4"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test5"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test3", 0));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(4)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Test {@link SynchronizationThrottler#onDisconnect()}
   */
  @Test
  void testClearsExistingSyncIdsOnDisconnect() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    throttler.onDisconnect();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Test {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testRemovesSynchronizationsFromQueue() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test4", 0));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId4", provideRequest("test5"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test6"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId4", provideRequest("test7"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test8"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId5", provideRequest("test9"));
    Thread.sleep(20);
    throttler.scheduleSynchronize("accountId3", provideRequest("test10", 0));
    for (int i = 0; i < 8; ++i) {
      tick(6625);
    }
    Mockito.verify(websocketClient, Mockito.times(6)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    Mockito.verify(websocketClient).rpcRequest("accountId1", provideRequest("test1"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId2", provideRequest("test2"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId3", provideRequest("test8"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId3", provideRequest("test10", 0), null);
    Mockito.verify(websocketClient).rpcRequest("accountId4", provideRequest("test7"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId5", provideRequest("test9"), null);
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize(String, ObjectNode)}
   */
  @Test
  void testRemovesExpiredSynchronizationsFromQueue() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    Thread.sleep(50);
    for (int i = 0; i < 20; ++i) {
      tick(8000);
      throttler.updateSynchronizationId("test1");
      throttler.updateSynchronizationId("test2");
    }
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId5", provideRequest("test5"));
    for (int i = 0; i < 20; ++i) {
      tick(8000);
      throttler.updateSynchronizationId("test1");
      throttler.updateSynchronizationId("test2");
    }
    tick(33000);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    Mockito.verify(websocketClient).rpcRequest("accountId1", provideRequest("test1"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId2", provideRequest("test2"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId5", provideRequest("test5"), null);
  }
  
  /**
   * Tests {@link SynchronizationThrottler#scheduleSynchronize}
   */
  @Test
  void testDoesNotGetQueueStuckDueToAppSynchronizationsLimit() throws Exception {
    SynchronizationThrottler otherThrottler = Mockito.mock(SynchronizationThrottler.class);
    List<String> otherSynchronizingAccounts = Arrays.asList(
      "accountId21", "accountId22", "accountId23", "accountId24", "accountId25", "accountId26",
      "accountId27", "accountId28", "accountId29", "accountId210", "accountId211", "accountId212",
      "accountId213", "accountId214", "accountId215");
    Mockito.when(otherThrottler.getSynchronizingAccounts()).thenReturn(otherSynchronizingAccounts);
    Mockito.when(websocketClient.getSocketInstances()).thenReturn(Arrays.asList(
      new MetaApiWebsocketClient.SocketInstance() {{synchronizationThrottler = otherThrottler;}},
      new MetaApiWebsocketClient.SocketInstance() {{synchronizationThrottler = throttler;}}
    ));
    throttler.scheduleSynchronize("accountId1", provideRequest("test1"));
    throttler.scheduleSynchronize("accountId2", provideRequest("test2"));
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    tick(5000);
    Mockito.verify(websocketClient, Mockito.never()).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    Mockito.when(otherThrottler.getSynchronizingAccounts())
      .thenReturn(otherSynchronizingAccounts.subList(1, otherSynchronizingAccounts.size()));
    tick(5000);
    Mockito.verify(websocketClient, Mockito.times(1)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    Mockito.when(otherThrottler.getSynchronizingAccounts())
      .thenReturn(otherSynchronizingAccounts.subList(2, otherSynchronizingAccounts.size()));
    tick(5000);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#removeSynchronizationId}
   */
  @Test
  void testDoesNotSkipQueueItemsWhenSynchronizationIdIsRemoved() throws Exception {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId2", provideRequest("test2"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId5", provideRequest("test5"));
    Thread.sleep(3000);
    throttler.removeSynchronizationId("test3");
    Thread.sleep(3000);
    throttler.removeSynchronizationId("test1");
    throttler.removeSynchronizationId("test2");
    Thread.sleep(3000);
    Mockito.verify(websocketClient, Mockito.times(4)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  }
  
  /**
   * Tests {@link SynchronizationThrottler#removeIdByParameters}
   */
  @Test
  void testRemovesIdByParameters() throws InterruptedException {
    throttler.scheduleSynchronize("accountId1", provideRequest("test1")).join();
    throttler.scheduleSynchronize("accountId2", provideRequest("test2", 0, "ps-mpa-0")).join();
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId2", provideRequest("test4", 1, "ps-mpa-1"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId2", provideRequest("test5", 0, "ps-mpa-2"));
    Thread.sleep(50);
    throttler.scheduleSynchronize("accountId4", provideRequest("test6"));
    Thread.sleep(50);
    throttler.removeIdByParameters("accountId2", 0, "ps-mpa-0");
    throttler.removeIdByParameters("accountId2", 1, "ps-mpa-1");
    throttler.removeSynchronizationId("test1");
    Thread.sleep(50);
    Mockito.verify(websocketClient).rpcRequest("accountId3", provideRequest("test3"), null);
    Mockito.verify(websocketClient).rpcRequest("accountId2", provideRequest("test5", 0, "ps-mpa-2"), null);
    Mockito.verify(websocketClient, Mockito.times(4)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
  };
  
  private ObjectNode provideRequest(String requestId) {
    ObjectNode request = JsonMapper.getInstance().createObjectNode();
    request.put("requestId", requestId);
    return request;
  }
  
  private ObjectNode provideRequest(String requestId, int instanceIndex) {
    ObjectNode request = provideRequest(requestId);
    request.put("instanceIndex", instanceIndex);
    return request;
  }

  private ObjectNode provideRequest(String requestId, int instanceIndex, String host) {
    ObjectNode request = provideRequest(requestId, instanceIndex);
    request.put("host", host);
    return request;
  }
  
  private List<String> provideListOfSize(int size) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      result.add(String.valueOf(i));
    }
    return result;
  }
  
  private void tick(long milliseconds) throws InterruptedException {
    ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(milliseconds));
    Thread.sleep(1100);
  }
}
