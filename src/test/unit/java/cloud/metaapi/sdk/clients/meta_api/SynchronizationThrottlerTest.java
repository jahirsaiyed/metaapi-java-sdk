package cloud.metaapi.sdk.clients.meta_api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Maps;
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
    throttler = new SynchronizationThrottler(websocketClient, 2);
    throttler.start();
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
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    tick(11000);
    Mockito.verify(websocketClient, Mockito.times(3)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    tick(11000);
    throttler.updateSynchronizationId("test1");
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    throttler.scheduleSynchronize("accountId5", provideRequest("test5"));
    Thread.sleep(20);
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
    throttler.scheduleSynchronize("accountId3", provideRequest("test3"));
    Thread.sleep(20);
    Mockito.verify(websocketClient, Mockito.times(2)).rpcRequest(Mockito.anyString(), Mockito.any(), Mockito.any());
    throttler.onDisconnect();
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
    tick(11000);
    tick(11000);
    tick(11000);
    tick(11000);
    tick(11000);
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
    throttler.scheduleSynchronize("accountId4", provideRequest("test4"));
    for (int i = 0; i < 20; ++i) {
      tick(8000);
      throttler.updateSynchronizationId("test1");
      throttler.updateSynchronizationId("test2");
    }
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
  
  private void tick(long milliseconds) throws InterruptedException {
    ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(milliseconds));
    Thread.sleep(1100);
  }
}
