package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.meta_api.HistoryFileManager.History;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * History storage which stores MetaTrader history in RAM
 */
public class MemoryHistoryStorage extends HistoryStorage {

  private String accountId;
  private HistoryFileManager fileManager;
  private List<MetatraderDeal> deals = new ArrayList<>();
  private List<MetatraderOrder> historyOrders = new ArrayList<>();
  private Map<String, Long> lastDealTimeByInstanceIndex = new HashMap<>();
  private Map<String, Long> lastHistoryOrderTimeByInstanceIndex = new HashMap<>();
  
  /**
   * Constructs the in-memory history store instance with default parameters
   * @param accountId account id
   */
  public MemoryHistoryStorage(String accountId) {
    this(accountId, null);
  }
  
  /**
   * Constructs the in-memory history store instance
   * @param accountId account id
   * @param application id, or {@code null}. By default is {@code MetaApi}
   */
  public MemoryHistoryStorage(String accountId, String application) {
    super();
    this.accountId = accountId;
    if (application == null) application = "MetaApi";
    fileManager = ServiceProvider.createHistoryFileManager(this.accountId, application, this);
    fileManager.startUpdateJob();
  }
  
  @Override
  public CompletableFuture<Void> initialize() {
    return loadDataFromDisk();
  }
  
  @Override
  public List<MetatraderDeal> getDeals() {
    return deals;
  }
  
  @Override
  public List<MetatraderOrder> getHistoryOrders() {
    return historyOrders;
  }
  
  @Override
  public Map<String, Long> getLastDealTimeByInstanceIndex() {
    return lastDealTimeByInstanceIndex;
  }
  
  @Override
  public Map<String, Long> getLastHistoryOrderTimeByInstanceIndex() {
    return lastHistoryOrderTimeByInstanceIndex;
  }
  
  @Override
  public CompletableFuture<Void> clear() {
    deals.clear();
    historyOrders.clear();
    lastDealTimeByInstanceIndex.clear();
    lastHistoryOrderTimeByInstanceIndex.clear();
    return fileManager.deleteStorageFromDisk();
  }
  
  @Override
  public CompletableFuture<Void> loadData() {
    return loadDataFromDisk();
  }
  
  /**
   * Loads history data from the file manager. This method is an alias of {@link #loadData()}.
   * @return completable future which resolves when the history is loaded
   */
  public CompletableFuture<Void> loadDataFromDisk() {
    return CompletableFuture.runAsync(() -> {
      History history = fileManager.getHistoryFromDisk().join();
      deals = new ArrayList<>(history.deals);
      historyOrders = new ArrayList<>(history.historyOrders);
      lastDealTimeByInstanceIndex = history.lastDealTimeByInstanceIndex != null
        ? history.lastDealTimeByInstanceIndex : new HashMap<>();
      lastHistoryOrderTimeByInstanceIndex = history.lastHistoryOrderTimeByInstanceIndex != null
        ? history.lastHistoryOrderTimeByInstanceIndex : new HashMap<>();
    });
  }
  
  @Override
  public CompletableFuture<Void> updateStorage() {
    return updateDiskStorage();
  }
  
  /**
   * Saves unsaved history items to disk storage. This method is an alias of {@link #updateStorage()}.
   * @return completable future which resolves when disk storage is updated
   */
  public CompletableFuture<Void> updateDiskStorage() {
    return fileManager.updateDiskStorage();
  }
  
  @Override
  public CompletableFuture<IsoTime> getLastHistoryOrderTime(Integer instanceNumber) {
    long result = 0;
    if (instanceNumber != null) {
      result = lastHistoryOrderTimeByInstanceIndex.containsKey("" + instanceNumber)
        ? lastHistoryOrderTimeByInstanceIndex.get("" + instanceNumber) : 0;
    } else {
      for (long time : lastHistoryOrderTimeByInstanceIndex.values()) {
        if (time > result) {
          result = time;
        }
      }
    }
    return CompletableFuture.completedFuture(new IsoTime(Date.from(Instant.ofEpochMilli(result))));
  }

  @Override
  public CompletableFuture<IsoTime> getLastDealTime(Integer instanceNumber) {
    long result = 0;
    if (instanceNumber != null) {
      result = lastDealTimeByInstanceIndex.containsKey("" + instanceNumber)
        ? lastDealTimeByInstanceIndex.get("" + instanceNumber) : 0;
    } else {
      for (long time : lastDealTimeByInstanceIndex.values()) {
        if (time > result) {
          result = time;
        }
      }
    }
    return CompletableFuture.completedFuture(new IsoTime(Date.from(Instant.ofEpochMilli(result))));
  }

  @Override
  public CompletableFuture<Void> onHistoryOrderAdded(String instanceIndex, MetatraderOrder historyOrder) {
    Integer instance = getInstanceNumber(instanceIndex);
    int insertIndex = 0;
    int replacementIndex = -1;
    Date newHistoryOrderTime = getOrderDoneTime(historyOrder);
    if (!lastHistoryOrderTimeByInstanceIndex.containsKey("" + instance)
      || lastHistoryOrderTimeByInstanceIndex.get("" + instance) < newHistoryOrderTime.getTime()) {
      lastHistoryOrderTimeByInstanceIndex.put("" + instance, newHistoryOrderTime.getTime());
    }
    for (int i = historyOrders.size() - 1; i >= 0; i--) {
      MetatraderOrder order = historyOrders.get(i);
      Date historyOrderTime = getOrderDoneTime(order);
      int timeComparing = historyOrderTime.compareTo(newHistoryOrderTime);
      if (timeComparing < 0 || (timeComparing == 0 && order.id.compareTo(historyOrder.id) <= 0)) {
        if (timeComparing == 0 && order.id.equals(historyOrder.id) && order.type == historyOrder.type) {
          replacementIndex = i;
        } else {
          insertIndex = i + 1;
        }
        break;
      }
    }
    if (replacementIndex != -1) {
      historyOrders.set(replacementIndex, historyOrder);
      fileManager.setStartNewOrderIndex(replacementIndex);
    }
    else {
      historyOrders.add(insertIndex, historyOrder);
      fileManager.setStartNewOrderIndex(insertIndex);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onDealAdded(String instanceIndex, MetatraderDeal deal) {
    Integer instance = getInstanceNumber(instanceIndex);
    int insertIndex = 0;
    int replacementIndex = -1;
    Date newDealTime = deal.time.getDate();
    if (!lastDealTimeByInstanceIndex.containsKey("" + instance)
      || lastDealTimeByInstanceIndex.get("" + instance) < newDealTime.getTime()) {
      lastDealTimeByInstanceIndex.put("" + instance, newDealTime.getTime());
    }
    for (int i = deals.size() - 1; i >= 0; i--) {
      MetatraderDeal d = deals.get(i);
      Date dealTime = d.time.getDate();
      int timeComparing = dealTime.compareTo(newDealTime);
      if (timeComparing < 0 || (timeComparing == 0 && d.id.compareTo(deal.id) <= 0) ||
          (timeComparing == 0 && d.id.equals(deal.id) && d.entryType.toString().compareTo(deal.entryType.toString()) <= 0)) {
        if (timeComparing == 0 && d.id.equals(deal.id) && d.entryType == deal.entryType) {
          replacementIndex = i;
        } else {
          insertIndex = i + 1;
        }
        break;
      }
    }
    if (replacementIndex != -1) {
      deals.set(replacementIndex, deal);
      fileManager.setStartNewDealIndex(replacementIndex);
    }
    else {
      deals.add(insertIndex, deal);
      fileManager.setStartNewDealIndex(insertIndex);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  @Override
  public CompletableFuture<Void> onDealSynchronizationFinished(String instanceIndex, String synchronizationId) {
    Integer instance = getInstanceNumber(instanceIndex);
    dealSynchronizationFinished.add("" + instance);
    return updateDiskStorage();
  }
  
  private Date getOrderDoneTime(MetatraderOrder order) {
    return order.doneTime != null ? order.doneTime.getDate() : Date.from(Instant.ofEpochSecond(0));
  }
}