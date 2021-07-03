package cloud.metaapi.sdk.clients.meta_api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Synchronization throttler used to limit the amount of concurrent synchronizations to prevent application
 * from being overloaded due to excessive number of synchronisation responses being sent.
 */
public class SynchronizationThrottler {
  
  private static Logger logger = Logger.getLogger(SynchronizationThrottler.class);
  private int maxConcurrentSynchronizations;
  private int queueTimeoutInSeconds;
  private int synchronizationTimeoutInSeconds;
  private MetaApiWebsocketClient client;
  private int socketInstanceIndex;
  protected Map<String, Long> synchronizationIds = new HashMap<>();
  private Map<String, AccountData> accountsBySynchronizationIds = new HashMap<>();
  private List<SynchronizationQueueItem> synchronizationQueue = new ArrayList<>();
  private Timer removeOldSyncIdsTimer = null;
  private Timer processQueueTimer = null;
  private boolean isProcessingQueue = false;
  
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
  }
  
  private static class SynchronizationQueueItem {
    public String synchronizationId;
    public CompletableFuture<Boolean> future;
    public long queueTime;
  }
  
  /**
   * Constructs the synchronization throttler
   * @param client MetaApi websocket client
   * @param socketInstanceIndex index of socket instance that uses the throttler
   * @param opts Synchronization throttler options
   */
  public SynchronizationThrottler(MetaApiWebsocketClient client, int socketInstanceIndex, Options opts) {
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
        advanceQueue();
        synchronizationIds.remove(key);
      }
    }
    while (synchronizationQueue.size() != 0 && (ServiceProvider.getNow().toEpochMilli() 
      - synchronizationQueue.get(0).queueTime > queueTimeoutInSeconds * 1000)) {
      removeFromQueue(synchronizationQueue.get(0).synchronizationId);
    }
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
    int synchronizingAccountsCount = 0;
    for (MetaApiWebsocketClient.SocketInstance socketInstance : client.getSocketInstances()) {
      synchronizingAccountsCount += socketInstance.synchronizationThrottler.getSynchronizingAccounts().size();
    }
    if (synchronizingAccountsCount >= maxConcurrentSynchronizations) {
      return false;
    }
    return getSynchronizingAccounts().size() < getMaxConcurrentSynchronizations();
  }
  
  /**
   * Removes synchronization id from slots and removes ids for the same account from the queue
   * @param synchronizationId Synchronization id
   */
  public void removeSynchronizationId(String synchronizationId) {
    if (accountsBySynchronizationIds.containsKey(synchronizationId)) {
      String accountId = accountsBySynchronizationIds.get(synchronizationId).accountId;
      int instanceIndex = accountsBySynchronizationIds.get(synchronizationId).instanceIndex;
      for (String key : new ArrayList<>(accountsBySynchronizationIds.keySet())) {
        if (accountsBySynchronizationIds.get(key).accountId.equals(accountId) &&
            instanceIndex == accountsBySynchronizationIds.get(key).instanceIndex) {
          removeFromQueue(key);
          accountsBySynchronizationIds.remove(key);;
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
    synchronizationIds.clear();
    advanceQueue();
  }
  
  private void advanceQueue() {
    if (isSynchronizationAvailable() && synchronizationQueue.size() != 0) {
      synchronizationQueue.get(0).future.complete(true);
    }
  }
  
  private void removeFromQueue(String synchronizationId) {
    for (int i = 0; i < synchronizationQueue.size(); ++i) {
      if (synchronizationQueue.get(i).synchronizationId.equals(synchronizationId)) {
        synchronizationQueue.get(i).future.complete(false);
      }
    }
    synchronizationQueue = synchronizationQueue.stream()
      .filter(item -> !item.synchronizationId.equals(synchronizationId))
      .collect(Collectors.toList());
  }
  
  private CompletableFuture<Void> processQueueJob() {
    return CompletableFuture.runAsync(() -> {
      if (!isProcessingQueue) {
        isProcessingQueue = true;
        try {
          while (synchronizationQueue.size() != 0
            && synchronizationIds.size() < getMaxConcurrentSynchronizations()) {
            synchronizationQueue.get(0).future.join();
            synchronizationQueue.remove(0);
          }
        } catch (Throwable err) {
          logger.info("Error processing queue job", err);
        }
        isProcessingQueue = false;
      }
    });
  }
  
  /**
   * Schedules to send a synchronization request for account
   * @param accountId Account id
   * @param request Request to send
   * @return Completable future resolving when synchronization is scheduled
   */
  public CompletableFuture<JsonNode> scheduleSynchronize(String accountId, ObjectNode request) {
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
      accountsBySynchronizationIds.put(synchronizationId, accountData);
      if (!isSynchronizationAvailable()) {
        CompletableFuture<Boolean> requestFuture = new CompletableFuture<>();
        String sid = synchronizationId;
        synchronizationQueue.add(new SynchronizationQueueItem() {{
          synchronizationId = sid;
          future = requestFuture;
          queueTime = ServiceProvider.getNow().toEpochMilli();
        }});
        boolean result = requestFuture.join();
        if (!result) {
          return null;
        }
      }
      updateSynchronizationId(synchronizationId);
      return client.rpcRequest(accountId, request, null).join();
    });
  }
}
