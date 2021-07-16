package cloud.metaapi.sdk.clients.meta_api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException;
import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException.TooManyRequestsExceptionMetadata;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.Js;
import cloud.metaapi.sdk.util.ServiceProvider;
import io.socket.client.Socket;

/**
 * Tests {@link SubscriptionManager}
 */
class SubscriptionManagerTest {

  private SubscriptionManager manager;
  private MetaApiWebsocketClient client;
  
  @BeforeAll
  static void setUpBeforeClass() {
    ServiceProvider.setRandom(0.2);
  }
  
  @AfterAll
  static void tearDownAftterClass() {
    ServiceProvider.setRandom(null);
  }
  
  @BeforeEach
  void setUp() throws Exception {
    client = Mockito.mock(MetaApiWebsocketClient.class);
    client.socketInstances = Arrays.asList(
      new MetaApiWebsocketClient.SocketInstance() {{ socket = Mockito.mock(Socket.class); }},
      new MetaApiWebsocketClient.SocketInstance() {{ socket = Mockito.mock(Socket.class); }}
    );
    Mockito.when(client.socketInstances.get(0).socket.connected()).thenReturn(true);
    Mockito.when(client.socketInstances.get(1).socket.connected()).thenReturn(false);
    Mockito.when(client.isConnected(Mockito.anyInt())).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        Integer socketInstanceIndex = invocation.getArgument(0, Integer.class);
        return client.socketInstances.get(socketInstanceIndex).socket.connected();
      }
    });
    Mockito.when(client.getSocketInstancesByAccounts()).thenReturn(Maps.newHashMap("accountId", 0));
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
    manager.subscribe("accountId", null, false).join();
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
    manager.subscribe("accountId", null, false);
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
      type = "LIMIT_REQUEST_RATE_PER_USER";
      recommendedRetryTime = new IsoTime(Date.from(Instant.now().plusMillis(5000)));
    }}));
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(rejectedFuture)
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", null, false);
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
    Map<String, Integer> socketInstancesByAccounts = new HashMap<>();
    socketInstancesByAccounts.put("accountId", 0);
    socketInstancesByAccounts.put("accountId2", 0);
    socketInstancesByAccounts.put("accountId3", 1);
    Mockito.when(client.getSocketInstancesByAccounts()).thenReturn(socketInstancesByAccounts);
    manager.subscribe("accountId", null, false);
    manager.subscribe("accountId2", null, false);
    manager.subscribe("accountId3", null, false);
    Thread.sleep(1000);
    manager.onReconnected(0, new ArrayList<>());
    Thread.sleep(5000);
    Mockito.verify(client, Mockito.times(4)).subscribe(Mockito.any(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#onReconnected}
   */
  @Test
  void testRestartsSubscriptionsOnReconnect() throws InterruptedException {
    Mockito.when(client.connect()).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.subscribe(Mockito.anyString(), Mockito.anyInt())).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.getSocketInstancesByAccounts()).thenReturn(Js.asMap(
      Pair.of("accountId", 0), Pair.of("accountId2", 0), Pair.of("accountId3", 0)
    ));
    manager.subscribe("accountId", null, false);
    Thread.sleep(50);
    manager.subscribe("accountId2", null, false);
    Thread.sleep(50);
    manager.subscribe("accountId3", null, false);
    Thread.sleep(2000);
    manager.onReconnected(0, Arrays.asList("accountId", "accountId2"));
    Thread.sleep(3000);
    Mockito.verify(client, Mockito.times(5)).subscribe(Mockito.anyString(), Mockito.any());
  };

  /**
   * Tests {@link SubscriptionManager#onReconnected}
   */
  @Test
  void testWaitsUntilPreviousSubscriptionEndsOnReconnect() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.anyString(), Mockito.anyInt())).thenAnswer(
      new Answer<CompletableFuture<Void>>() {
      @Override
      public CompletableFuture<Void> answer(InvocationOnMock invocation) {
        return CompletableFuture.runAsync(() -> {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        });
      }
    });

    Mockito.when(client.connect()).thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(client.getSocketInstancesByAccounts()).thenReturn(Js.asMap(Pair.of("accountId", 0)));
    manager.subscribe("accountId", null, false);
    Thread.sleep(1000);
    manager.onReconnected(0, Arrays.asList("accountId"));
    Thread.sleep(3000);
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.anyString(), Mockito.nullable(Integer.class));
  };
  
  /**
   * Tests {@link SubscriptionManager#subscribe(String, Integer)}
   */
  @Test
  void testDoesNotSendMultipleSubscribeRequestsAtTheSameTime() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    manager.subscribe("accountId", null, false);
    manager.subscribe("accountId", null, false);
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
    Socket socketInstanceSocket = Mockito.spy(client.socketInstances.get(0).socket);
    Mockito.doReturn(true).when(socketInstanceSocket).connected();
    client.socketInstances.get(0).socket = socketInstanceSocket;
    client.getSocketInstancesByAccounts().put("accountId2", 1);
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        manager.cancelSubscribe("accountId:0");
        manager.cancelSubscribe("accountId2:0");
      }
    }, 100);
    manager.onTimeout("accountId", null);
    manager.onTimeout("accountId2", null);
    Thread.sleep(200);
    Mockito.verify(client, Mockito.times(1)).subscribe("accountId", null);
  };

  /**
   * Tests {@link SubscriptionManager#onTimeout}
   */
  @Test
  void testDoesNotRetrySubscribeToTerminalIfConnectionIsClosed() throws InterruptedException {
    Mockito.when(client.subscribe(Mockito.any(), Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Socket socketInstanceSocket = Mockito.spy(client.socketInstances.get(0).socket);
    Mockito.doReturn(false).when(socketInstanceSocket).connected();
    client.socketInstances.get(0).socket = socketInstanceSocket;
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
    manager.subscribe("accountId", 0, false);
    manager.subscribe("accountId", 1, false);
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
    manager.subscribe("accountId", null, false);
    Thread.sleep(250);
    manager.cancelSubscribe("accountId:0");
    Thread.sleep(250);
    manager.subscribe("accountId", null, false);
    Thread.sleep(250);
    Mockito.verify(client, Mockito.times(2)).subscribe(Mockito.any(), Mockito.any());
  };
  
  /**
   * Tests {@link SubscriptionManager#cancelSubscribe}
   */
  @Test
  void testChecksIfAccountIsSubscribing() throws InterruptedException {
    manager.subscribe("accountId", 1, false);
    Thread.sleep(50);
    assertTrue(manager.isAccountSubscribing("accountId", null));
    assertFalse(manager.isAccountSubscribing("accountId", 0));
    assertTrue(manager.isAccountSubscribing("accountId", 1));
  };
}