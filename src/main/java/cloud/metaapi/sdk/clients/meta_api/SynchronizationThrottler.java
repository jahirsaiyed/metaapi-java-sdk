package cloud.metaapi.sdk.clients.meta_api;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.OptionsValidator;
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.Js;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Synchronization throttler used to limit the amount of concurrent synchronizations to prevent application
 * from being overloaded due to excessive number of synchronisation responses being sent.
 */
public class SynchronizationThrottler {
  
  private static Logger logger = LogManager.getLogger(SynchronizationThrottler.class);
  private int maxConcurrentSynchronizations;
  private int queueTimeoutInSeconds;
  private int synchronizationTimeoutInSeconds;
  private MetaApiWebsocketClient client;
  private int socketInstanceIndex;
  protected Map<String, Long> synchronizationIds = new ConcurrentHashMap<>();
  private Map<String, AccountData> accountsBySynchronizationIds = new ConcurrentHashMap<>();
  private List<SynchronizationQueueItem> synchronizationQueue = new ArrayList<>();
  private Timer removeOldSyncIdsTimer = null;
  private Timer processQueueTimer = null;
  
  /**
   * Options for synchronization throttler
   */
  public static class Options {
    /**
     * Amount of maximum allowed concurrent synchronizations. If 0, it is calculated
     * automatically
     */
    public int maxConcurrentSynchronizations = 15;
    /**
     * Allowed time for a synchronization in queue
     */
    public int queueTimeoutInSeconds = 300;
    /**
     * Time after which a synchronization slot is freed to be used by another
     * synchronization
     */
    public int synchronizationTimeoutInSeconds = 10;
  }
  
  private static class AccountData {
    public String accountId;
    public int instanceIndex;
    public String host;
  }
  
  private static class SynchronizationQueueItem {
    public String synchronizationId;
    public CompletableFuture<String> future;
    public long queueTime;
  }
  
  /**
   * Constructs the synchronization throttler
   * @param client MetaApi websocket client
   * @param socketInstanceIndex index of socket instance that uses the throttler
   * @param opts Synchronization throttler options
   * @throws ValidationException if specified options are invalid 
   */
  public SynchronizationThrottler(MetaApiWebsocketClient client, int socketInstanceIndex, Options opts)
    throws ValidationException {
    OptionsValidator validator = new OptionsValidator();
    validator.validateNonZeroInt(opts.maxConcurrentSynchronizations, "synchronizationThrottler.maxConcurrentSynchronizations");
    validator.validateNonZeroInt(opts.queueTimeoutInSeconds, "synchronizationThrottler.queueTimeoutInSeconds");
    validator.validateNonZeroInt(opts.synchronizationTimeoutInSeconds, "synchronizationThrottler.synchronizationTimeoutInSeconds");
    
    this.maxConcurrentSynchronizations = opts.maxConcurrentSynchronizations;
    this.queueTimeoutInSeconds = opts.queueTimeoutInSeconds;
    this.synchronizationTimeoutInSeconds = opts.synchronizationTimeoutInSeconds;
    this.client = client;
    this.socketInstanceIndex = socketInstanceIndex;
  }
  
  /**
   * Initializes the synchronization throttler
   */
  void start() {
    if (removeOldSyncIdsTimer == null) {
      removeOldSyncIdsTimer = new Timer();
      removeOldSyncIdsTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          removeOldSyncIdsJob();
        }
      }, 1000, 1000);
      processQueueTimer = new Timer();
      processQueueTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          processQueueJob();
        }
      }, 1000, 1000);
    }
  }
  
  /**
   * Deinitializes the throttler
   */
  void stop() {
    if (removeOldSyncIdsTimer != null) {
      removeOldSyncIdsTimer.cancel();
      removeOldSyncIdsTimer = null;
      processQueueTimer.cancel();
      processQueueTimer = null;
    }
  }
  
  private void removeOldSyncIdsJob() {
    long now = ServiceProvider.getNow().toEpochMilli();
    for (String key : new ArrayList<>(synchronizationIds.keySet())) {
      if ((now - synchronizationIds.get(key)) > synchronizationTimeoutInSeconds * 1000) {
        synchronizationIds.remove(key);
      }
    }
    while (synchronizationQueue.size() > 0 && (ServiceProvider.getNow().toEpochMilli() 
      - synchronizationQueue.get(0).queueTime > queueTimeoutInSeconds * 1000)) {
      removeFromQueue(synchronizationQueue.get(0).synchronizationId, "timeout");
    }
    advanceQueue();
  }
  
  /**
   * Fills a synchronization slot with synchronization id
   * @param synchronizationId Synchronization id
   */
  public void updateSynchronizationId(String synchronizationId) {
    if (accountsBySynchronizationIds.containsKey(synchronizationId)) {
      synchronizationIds.put(synchronizationId, ServiceProvider.getNow().toEpochMilli());
    }
  }
  
  /**
   * Returns the list of currently synchronizing account ids
   * @return the list of currently synchronizing account ids
   */
  public List<String> getSynchronizingAccounts() {
    List<String> synchronizingAccounts = new ArrayList<>();
    synchronizationIds.keySet().forEach(key -> {
      AccountData accountData = accountsBySynchronizationIds.get(key);
      if(accountData != null && synchronizingAccounts.indexOf(accountData.accountId) == -1) {
        synchronizingAccounts.add(accountData.accountId);
      }
    });
    return synchronizingAccounts;
  }
  
  /**
   * Returns the list of currenly active synchronization ids
   * @return Synchronization ids
   */
  public List<String> getActiveSynchronizationIds() {
    return new ArrayList<>(accountsBySynchronizationIds.keySet());
  }
  
  /**
   * Returns the amount of maximum allowed concurrent synchronizations
   * @return maximum allowed concurrent synchronizations
   */
  public int getMaxConcurrentSynchronizations() {
    int calculatedMax = Math.max((int) Math.ceil(
      client.getSubscribedAccountIds(socketInstanceIndex).size() / 10.0), 1);
    return Math.min(calculatedMax, maxConcurrentSynchronizations);
  }
  
  /**
   * Returns flag whether there are free slots for synchronization requests
   * @return Flag whether there are free slots for synchronization requests
   */
  public boolean isSynchronizationAvailable() {
    if (Js.reduce(client.getSocketInstances(), (acc, socketInstance) -> 
      acc + socketInstance.synchronizationThrottler.getSynchronizingAccounts().size(), 0) >=
      maxConcurrentSynchronizations) {
      return false;
    }
    return getSynchronizingAccounts().size() < getMaxConcurrentSynchronizations();
  }
  
  /**
   * Removes synchronizations from queue and from the list by parameters
   * @param accountId account id
   * @param instanceIndex account instance index
   * @param host account host name
   */
  public void removeIdByParameters(String accountId, int instanceIndex, String host) {
    for (String key : new ArrayList<>(accountsBySynchronizationIds.keySet())) {
      if (accountsBySynchronizationIds.get(key).accountId.equals(accountId) &&
          accountsBySynchronizationIds.get(key).instanceIndex == instanceIndex &&
          Js.or(accountsBySynchronizationIds.get(key).host, "").equals(Js.or(host, ""))) {
        removeSynchronizationId(key);
      }
    }
  }
  
  /**
   * Removes synchronization id from slots and removes ids for the same account from the queue
   * @param synchronizationId Synchronization id
   */
  public void removeSynchronizationId(String synchronizationId) {
    if (accountsBySynchronizationIds.containsKey(synchronizationId)) {
      String accountId = accountsBySynchronizationIds.get(synchronizationId).accountId;
      int instanceIndex = accountsBySynchronizationIds.get(synchronizationId).instanceIndex;
      String host = Js.or(accountsBySynchronizationIds.get(synchronizationId).host, "");
      for (String key : new ArrayList<>(accountsBySynchronizationIds.keySet())) {
        if (accountsBySynchronizationIds.get(key).accountId.equals(accountId) &&
            accountsBySynchronizationIds.get(key).instanceIndex == instanceIndex &&
            Js.or(accountsBySynchronizationIds.get(key).host, "").equals(host)) {
          removeFromQueue(key, "cancel");
          accountsBySynchronizationIds.remove(key);
        }
      }
    }
    if (synchronizationIds.containsKey(synchronizationId)) {
      synchronizationIds.remove(synchronizationId);
    }
    advanceQueue();
  }
  
  /**
   * Clears synchronization ids on disconnect
   */
  public void onDisconnect() {
    synchronizationQueue.forEach(synchronization -> {
      synchronization.future.complete("cancel");
    });
    synchronizationIds.clear();
    accountsBySynchronizationIds.clear();
    synchronizationQueue.clear();
    stop();
    start();
  }
  
  private void advanceQueue() {
    int index = 0;
    while (isSynchronizationAvailable() && synchronizationQueue.size() > 0 && 
        index < synchronizationQueue.size()) {
      SynchronizationQueueItem queueItem = synchronizationQueue.get(index);
      queueItem.future.complete("synchronize");
      updateSynchronizationId(queueItem.synchronizationId);
      index++;
    }
  }
  
  private void removeFromQueue(String synchronizationId, String result) {
    new ArrayList<>(synchronizationQueue).forEach(syncItem -> {
      if (syncItem.synchronizationId.equals(synchronizationId)) {
        syncItem.future.complete(result);
      }
    });
    synchronizationQueue = synchronizationQueue.stream()
      .filter(item -> !item.synchronizationId.equals(synchronizationId))
      .collect(Collectors.toList());
  }
  
  private CompletableFuture<Void> processQueueJob() {
    return CompletableFuture.runAsync(() -> {
      try {
        while (synchronizationQueue.size() > 0) {
          SynchronizationQueueItem queueItem = synchronizationQueue.get(0);
          queueItem.future.join();
          
          // Often synchronizationQueue.remove(0) raises IndexOutOfBoundsException because
          // somehow synchronizationQueue size becomes 0 right after checking it. This is a
          // hook that tells Java to do not hurry
          CompletableFuture.runAsync(() -> {}).join();

          if (synchronizationQueue.size() > 0 && synchronizationQueue.get(0).synchronizationId
            .equals(queueItem.synchronizationId)) {
            synchronizationQueue.remove(0);
          }
        }
      } catch (Throwable err) {
        logger.info("[" + new IsoTime() + "] Error processing queue job", err);
      }
    });
  }
  
  /**
   * Schedules to send a synchronization request for account
   * @param accountId Account id
   * @param request Request to send
   * @return Completable future resolving when synchronization is scheduled
   */
  public CompletableFuture<Boolean> scheduleSynchronize(String accountId, ObjectNode request) {
    return CompletableFuture.supplyAsync(() -> {
      String synchronizationId = request.get("requestId").asText();
      int instanceIndex = request.has("instanceIndex") ? request.get("instanceIndex").asInt() : -1;
      for (String key : new ArrayList<>(accountsBySynchronizationIds.keySet())) {
        if (accountsBySynchronizationIds.get(key).accountId.equals(accountId) &&
            accountsBySynchronizationIds.get(key).instanceIndex == instanceIndex) {
          removeSynchronizationId(key);
        }
      }
      AccountData accountData = new AccountData();
      accountData.accountId = accountId;
      accountData.instanceIndex = instanceIndex;
      accountData.host = request.hasNonNull("host") ? request.get("host").asText() : null;
      accountsBySynchronizationIds.put(synchronizationId, accountData);
      if (!isSynchronizationAvailable()) {
        CompletableFuture<String> requestFuture = new CompletableFuture<>();
        String sid = synchronizationId;
        synchronizationQueue.add(new SynchronizationQueueItem() {{
          synchronizationId = sid;
          future = requestFuture;
          queueTime = ServiceProvider.getNow().toEpochMilli();
        }});
        String result = requestFuture.join();
        if (result.equals("cancel")) {
          return false;
        } else if (result.equals("timeout")) {
          throw new CompletionException(new TimeoutException("Account " + accountId 
            + " synchronization " + synchronizationId + "timed out in synchronization queue"));
        }
      }
      updateSynchronizationId(synchronizationId);
      client.rpcRequest(accountId, request, null).join();
      return true;
    });
  }
}
