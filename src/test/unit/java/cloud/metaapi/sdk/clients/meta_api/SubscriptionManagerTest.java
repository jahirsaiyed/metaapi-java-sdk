package cloud.metaapi.sdk.clients.meta_api;

import java.time.Instant;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException;
import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException.TooManyRequestsExceptionMetadata;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Tests {@link SubscriptionManager}
 */
class SubscriptionManagerTest {

  private SubscriptionManager manager;
  private MetaApiWebsocketClient client;
  
  @BeforeEach
  void setUp() throws Exception {
    client = Mockito.mock(MetaApiWebsocketClient.class);
    Mockito.when(client.isConnected()).thenReturn(true);
    manager = new SubscriptionManager(client);
  }

  /**
   * Tests {@link SubscriptionManager#subscribe(String, Integer)}
   */
  @Test
  void testSubscribesToTerminal() {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        manager.cancelSubscribe("accountId:0");
      }
    }, 50);
    manager.subscribe("accountId", null).join();
    Mockito.verify(client).subscribe("accountId", null);
  };

  /**
   * Tests {@link SubscriptionManager#subscribe(String, Integer)}
   */
  @Test
  void testRetriesSubscribeIfNoResponseReceived() throws InterruptedException {
    CompletableFuture<Void> rejectedFuture = new CompletableFuture<>();
    rejectedFuture.completeExceptionally(new TimeoutException("timeout"));
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(rejectedFuture)
      .thenReturn(CompletableFuture.completedFuture(null));
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        manager.cancelSubscribe("accountId:0");
      }
    }, 3600);
    manager.subscribe("accountId", null);
    Thread.sleep(10000);
    Mockito.verify(client, Mockito.times(2)).subscribe("accountId", null);
  };

  /**
   * Tests {@link SubscriptionManager#subscribe(String, Integer)}
   */
  @Test
  void testWaitsForRecommendedTimeIfTooManyRequestsErrorReceived() throws InterruptedException {
    CompletableFuture<Void> rejectedFuture = new CompletableFuture<>();
    rejectedFuture.completeExceptionally(new TooManyRequestsException("timeout",
      new TooManyRequestsExceptionMetadata() {{
      periodInMinutes = 60;
      requestsPerPeriodAllowed = 10000;
      recommendedRetryTime = new IsoTime(Date.from(Instant.now().plusMillis(5000)));
    }}));
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(rejectedFuture)
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", null);
    Thread.sleep(3600);
    Mockito.verify(client, Mockito.times(1)).subscribe(Mockito.any(), Mockito.any());
    Thread.sleep(2000);
    manager.cancelSubscribe("accountId:0");
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#onReconnected()}
   */
  @Test
  void testCancelsAllSubscriptionsOnReconnect() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", null);
    manager.subscribe("accountId2", null);
    Thread.sleep(1000);
    manager.onReconnected();
    Thread.sleep(5000);
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#subscribe(String, Integer)}
   */
  @Test
  void testDoesNotSendMultipleSubscribeRequestsAtTheSameTime() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", null);
    manager.subscribe("accountId", null);
    Thread.sleep(1000);
    manager.cancelSubscribe("accountId:0");
    Thread.sleep(2500);
    Mockito.verify(client, Mockito.times(1)).subscribe("accountId", null);
  };

  /**
   * Tests {@link SubscriptionManager#onTimeout(String, int)}
   */
  @Test
  void testShouldResubscribeOnTimeout() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        manager.cancelSubscribe("accountId:0");
      }
    }, 100);
    manager.onTimeout("accountId", null);
    Thread.sleep(200);
    Mockito.verify(client).subscribe("accountId", null);
  };

  /**
   * Tests {@link SubscriptionManager#onTimeout}
   */
  @Test
  void testDoesNotRetrySubscribeToTerminalIfConnectionIsClosed() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.isConnected()).thenReturn(false);
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        manager.cancelSubscribe("accountId:0");
      }
    }, 100);
    manager.onTimeout("accountId", null);
    Thread.sleep(200);
    Mockito.verify(client, Mockito.never()).subscribe(Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#cancelAccount}
   */
  @Test
  void testCancelsAllSubscriptionsForAnAccount() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", 0);
    manager.subscribe("accountId", 1);
    Thread.sleep(100);
    manager.cancelAccount("accountId");
    Thread.sleep(500);
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#cancelSubscribe}
   */
  @Test
  void testDestroysSubscribeProcessOnCancel() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenAnswer(new Answer<CompletableFuture<Void>>() {
      @Override
      public CompletableFuture<Void> answer(InvocationOnMock invocation) {
        return CompletableFuture.runAsync(() -> {
          try {
            Thread.sleep(400);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        });
      }
    });
    manager.subscribe("accountId", null);
    Thread.sleep(50);
    manager.cancelSubscribe("accountId:0");
    Thread.sleep(50);
    manager.subscribe("accountId", null);
    Thread.sleep(50);
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.any(), Mockito.any());
  };
}