package cloud.metaapi.sdk.clients.meta_api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloud.metaapi.sdk.clients.error_handler.TooManyRequestsException;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.Js;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Subscription manager to handle account subscription logic
 */
public class SubscriptionManager {
  
  private static Logger logger = LogManager.getLogger(SubscriptionManager.class);
  private MetaApiWebsocketClient websocketClient;
  private Map<String, Subscription> subscriptions = new HashMap<>();
  private Set<String> awaitingResubscribe = new HashSet<>();
  
  private static class Subscription {
    public boolean shouldRetry;
    public Task task;
    public Timer waitTask;
    public CompletableFuture<Boolean> future;
    public boolean isDisconnectedRetryMode;
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
   * Returns whether an account is currently subscribing
   * @param accountId account id
   * @param instanceNumber instance index number, or {@code null}
   * @return whether an account is currently subscribing
   */
  public boolean isAccountSubscribing(String accountId, Integer instanceNumber) {
    if (instanceNumber != null) {
      return subscriptions.keySet().stream().filter(value ->
        value.equals(accountId + ":" + instanceNumber)).findFirst().isPresent();
    } else {
      for (String key : subscriptions.keySet()) {
        if (key.startsWith(accountId)) {
          return true;
        }
      }
      return false;
    }
  }
  
  /**
   * Returns whether an instance is in disconnected retry mode
   * @param accountId account id
   * @param instanceNumber instance index number
   * @returns whether an account is currently subscribing
   */
  boolean isDisconnectedRetryMode(String accountId, Integer instanceNumber) {
    String instanceId = accountId + ":" + Js.or(instanceNumber, 0);
    return subscriptions.containsKey(instanceId) ?
      subscriptions.get(instanceId).isDisconnectedRetryMode : false;
  }
  
  /**
   * Schedules to send subscribe requests to an account until cancelled
   * @param accountId id of the MetaTrader account
   * @param instanceNumber instance index number
   * @param isDisconnectedRetryMode whether to start subscription in disconnected retry mode.
   * Subscription task in disconnected mode will be immediately replaced when the status packet is received
   * @return completable future when the operation is completed
   */
  public CompletableFuture<Void> subscribe(String accountId, Integer instanceNumber,
    boolean isDisconnectedRetryMode) {
    String instanceId = accountId + ":" + Js.or(instanceNumber, 0);
    if (!subscriptions.containsKey(instanceId)) {
      Subscription newSubscription = new Subscription() {{
        shouldRetry = true;
        task = null;
        waitTask = null;
        future = null;
      }}; 
      newSubscription.isDisconnectedRetryMode = isDisconnectedRetryMode;
      subscriptions.put(instanceId, newSubscription);
      return CompletableFuture.runAsync(() -> {
        int subscribeRetryIntervalInSeconds = 3;
        while (subscriptions.get(instanceId).shouldRetry) {
          CompletableFuture<Boolean> resolveSubscribe = new CompletableFuture<>();
          subscriptions.get(instanceId).task = new Task() {{ future = resolveSubscribe; }};
          subscribeTask(accountId, instanceNumber, subscribeRetryIntervalInSeconds, resolveSubscribe);
          subscriptions.get(instanceId).task.future.join();
          if (!subscriptions.get(instanceId).shouldRetry) {
            break;
          }
          int retryInterval = subscribeRetryIntervalInSeconds;
          subscribeRetryIntervalInSeconds = Math.min(subscribeRetryIntervalInSeconds * 2, 300);
          CompletableFuture<Boolean> subscribeFuture = new CompletableFuture<>();
          Timer waitTask = Js.setTimeout(() -> subscribeFuture.complete(true), retryInterval * 1000);
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
  
  private CompletableFuture<Void> subscribeTask(String accountId, Integer instanceNumber,
    int subscribeRetryIntervalInSeconds, CompletableFuture<Boolean> subscribeFuture) {
    return CompletableFuture.runAsync(() -> {
      try {
        websocketClient.subscribe(accountId, instanceNumber).join();
      } catch (CompletionException err) {
        if (err.getCause() instanceof TooManyRequestsException) {
          TooManyRequestsException tooManyRequestsErr = (TooManyRequestsException) err.getCause();
          int socketInstanceIndex = websocketClient.getSocketInstancesByAccounts().get(accountId);
          if (tooManyRequestsErr.metadata.type.equals("LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER")) {
            logger.info(err);
          }
          if (Arrays.asList("LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER", "LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_SERVER", 
            "LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER_PER_SERVER").indexOf(tooManyRequestsErr.metadata.type) != -1) {
            websocketClient.getSocketInstancesByAccounts().remove(accountId);
            websocketClient.lockSocketInstance(socketInstanceIndex, tooManyRequestsErr.metadata);
          } else {
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
   * @param instanceNumber instance index
   */
  public void onTimeout(String accountId, Integer instanceNumber) {
    if (websocketClient.getSocketInstancesByAccounts().containsKey(accountId) &&
      websocketClient.isConnected(websocketClient.getSocketInstancesByAccounts().getOrDefault(accountId, null))) {
      subscribe(accountId, instanceNumber, true);
    }
  }

  /**
   * Invoked when connection to MetaTrader terminal terminated
   * @param accountId id of the MetaTrader account
   * @param instanceNumber instance index number
   * @return completable future when the operation is completed
   */
  public CompletableFuture<Void> onDisconnected(String accountId, int instanceNumber) {
    return CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep((long) (Math.max(ServiceProvider.getRandom() * 5, 1) * 1000));
      } catch (InterruptedException e) {
        logger.error(e);
      }
      if (websocketClient.getSocketInstancesByAccounts().containsKey(accountId)) {
        subscribe(accountId, instanceNumber, true);
      }
    });
  }

  /**
   * Invoked when connection to MetaApi websocket API restored after a disconnect.
   * @param socketInstanceIndex socket instance index
   * @param reconnectAccountIds account ids to reconnect
   */
  public void onReconnected(int socketInstanceIndex, List<String> reconnectAccountIds) {
    try {
      Map<String, Integer> socketInstancesByAccounts = websocketClient.getSocketInstancesByAccounts();
      for (String instanceId : new ArrayList<>(subscriptions.keySet())) {
        String accountId = instanceId.split(":")[0];
        if (socketInstancesByAccounts.getOrDefault(accountId, -1) == socketInstanceIndex) {
          cancelSubscribe(instanceId);
        }
      }
      reconnectAccountIds.forEach(accountId -> {
        CompletableFuture.runAsync(() -> {
          try {
            if (!awaitingResubscribe.contains(accountId)) {
              awaitingResubscribe.add(accountId);
              while (isAccountSubscribing(accountId, null)) {
                Thread.sleep(1000);
              }
              awaitingResubscribe.remove(accountId);
              Thread.sleep((long) (ServiceProvider.getRandom() * 5000));
              subscribe(accountId, null, false);
            }
          } catch (Throwable err) {
            logger.error("[" + new IsoTime() + "] Account " + accountId + " resubscribe task failed", err);
          }
        });
      });
    } catch (Throwable err) {
      logger.error("[" + new IsoTime() + "] Failed to process subscribe manager reconnected event", err);
    }
  }
}