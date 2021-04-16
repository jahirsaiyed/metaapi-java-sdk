package cloud.metaapi.sdk.clients.meta_api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Subscription manager to handle account subscription logic
 */
public class SubscriptionManager {
  
  private static Logger logger = Logger.getLogger(SubscriptionManager.class);
  private MetaApiWebsocketClient websocketClient;
  private Map<String, Subscription> subscriptions = new HashMap<>();
  
  private static class Subscription {
    public boolean shouldRetry;
    public Task task;
    public Timer waitTask;
    public CompletableFuture<Boolean> future;
  }
  
  private static class Task {
    public CompletableFuture<Boolean> future;
  }
  
  /**
   * Constructs the subscription manager
   * @param websocketClient websocket client to use for sending requests
   */
  public SubscriptionManager(MetaApiWebsocketClient websocketClient) {
    this.websocketClient = websocketClient;
  }
  
  /**
   * Schedules to send subscribe requests to an account until cancelled
   * @param accountId id of the MetaTrader account
   * @param instanceIndex instance index
   */
  public CompletableFuture<Void> subscribe(String accountId, Integer instanceIndex) {
    String instanceId = accountId + ":" + (instanceIndex != null ? instanceIndex : 0);
    if (!subscriptions.containsKey(instanceId)) {
      subscriptions.put(instanceId, new Subscription() {{
        shouldRetry = true;
        task = null;
        waitTask = null;
        future = null;
      }});
      return CompletableFuture.runAsync(() -> {
        int subscribeRetryIntervalInSeconds = 3;
        while (subscriptions.get(instanceId).shouldRetry) {
          CompletableFuture<Boolean> resolveSubscribe = new CompletableFuture<>(); // resolveSubscribe
          subscriptions.get(instanceId).task = new Task() {{ future = resolveSubscribe; }};
          subscribeTask(accountId, instanceIndex, subscribeRetryIntervalInSeconds, resolveSubscribe);
          subscriptions.get(instanceId).task.future.join();
          if (!subscriptions.get(instanceId).shouldRetry) {
            break;
          }
          int retryInterval = subscribeRetryIntervalInSeconds;
          subscribeRetryIntervalInSeconds = Math.min(subscribeRetryIntervalInSeconds * 2, 300);
          CompletableFuture<Boolean> subscribeFuture = new CompletableFuture<>();
          Timer waitTask = new Timer();
          waitTask.schedule(new TimerTask() {
            @Override
            public void run() {
              subscribeFuture.complete(true);
            }
          }, retryInterval * 1000);
          subscriptions.get(instanceId).waitTask = waitTask;
          subscriptions.get(instanceId).future = subscribeFuture;
          boolean result = subscriptions.get(instanceId).future.join();
          subscriptions.get(instanceId).future = null;
          if (!result) {
            break;
          }
        }
        subscriptions.remove(instanceId);
      });
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }
  
  private CompletableFuture<Void> subscribeTask(String accountId, Integer instanceIndex,
    int subscribeRetryIntervalInSeconds, CompletableFuture<Boolean> subscribeFuture) {
    return CompletableFuture.runAsync(() -> {
      try {
        websocketClient.subscribe(accountId, instanceIndex).join();
      } catch (CompletionException err) {
        if (err.getCause() instanceof TooManyRequestsException) {
          long retryTime = ((TooManyRequestsException) err.getCause()).metadata.recommendedRetryTime.getDate().getTime();
          if (new IsoTime().getDate().getTime() + subscribeRetryIntervalInSeconds * 1000 < retryTime) {
            try {
              Thread.sleep(retryTime - new IsoTime().getDate().getTime() - subscribeRetryIntervalInSeconds * 1000);
            } catch (InterruptedException e) {
              logger.error(e);
            }
          }
        }
      }
      subscribeFuture.complete(null);
    });
  }
  
  /**
   * Cancels active subscription tasks for an instance id
   * @param instanceId instance id to cancel subscription task for
   */
  public void cancelSubscribe(String instanceId) {
    if (subscriptions.containsKey(instanceId)) {
      Subscription subscription = subscriptions.get(instanceId);
      if (subscription.future != null) {
        subscription.future.complete(false);
        subscription.waitTask.cancel();
      }
      if (subscription.task != null) {
        subscription.task.future.complete(false);
      }
      subscription.shouldRetry = false;
    }
  }

  /**
   * Cancels active subscription tasks for an account
   * @param accountId account id to cancel subscription tasks for
   */
  public void cancelAccount(String accountId) {
    for (String instanceId : subscriptions.keySet().stream().filter(key -> key.startsWith(accountId))
      .collect(Collectors.toList())) {
      cancelSubscribe(instanceId);
    }
  }

  /**
   * Invoked on account timeout.
   * @param accountId id of the MetaTrader account
   * @param instanceIndex instance index
   */
  public void onTimeout(String accountId, Integer instanceIndex) {
    if (websocketClient.isConnected()) {
      subscribe(accountId, instanceIndex);
    }
  }

  /**
   * Invoked when connection to MetaTrader terminal terminated
   * @param accountId id of the MetaTrader account
   * @param instanceIndex instance index
   */
  public CompletableFuture<Void> onDisconnected(String accountId, int instanceIndex) {
    return CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep((long) (Math.max(Math.random() * 5, 1) * 1000));
      } catch (InterruptedException e) {
        logger.error(e);
      }
      subscribe(accountId, instanceIndex);
    });
  }

  /**
   * Invoked when connection to MetaApi websocket API restored after a disconnect.
   */
  public void onReconnected() {
    for (String instanceId : new ArrayList<>(subscriptions.keySet())) {
      cancelSubscribe(instanceId);
    }
  }
}