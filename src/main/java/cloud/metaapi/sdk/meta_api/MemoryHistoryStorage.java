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
  private Map<Integer, Long> lastDealTimeByInstanceIndex = new HashMap<>();
  private Map<Integer, Long> lastHistoryOrderTimeByInstanceIndex = new HashMap<>();
  
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
  public List<MetatraderDeal> getDeals() {
    return deals;
  }
  
  @Override
  public List<MetatraderOrder> getHistoryOrders() {
    return historyOrders;
  }
  
  @Override
  public Map<Integer, Long> getLastDealTimeByInstanceIndex() {
    return lastDealTimeByInstanceIndex;
  }
  
  @Override
  public Map<Integer, Long> getLastHistoryOrderTimeByInstanceIndex() {
    return lastHistoryOrderTimeByInstanceIndex;
  }
  
  @Override
  public void reset() {
    deals.clear();
    historyOrders.clear();
    lastDealTimeByInstanceIndex.clear();
    lastHistoryOrderTimeByInstanceIndex.clear();
    fileManager.deleteStorageFromDisk();
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
  public CompletableFuture<IsoTime> getLastHistoryOrderTime(Integer instanceIndex) {
    long result = 0;
    if (instanceIndex != null) {
      result = lastHistoryOrderTimeByInstanceIndex.containsKey(instanceIndex)
        ? lastHistoryOrderTimeByInstanceIndex.get(instanceIndex) : 0;
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
  public CompletableFuture<IsoTime> getLastDealTime(Integer instanceIndex) {
    long result = 0;
    if (instanceIndex != null) {
      result = lastDealTimeByInstanceIndex.containsKey(instanceIndex)
        ? lastDealTimeByInstanceIndex.get(instanceIndex) : 0;
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
  public CompletableFuture<Void> onHistoryOrderAdded(int instanceIndex, MetatraderOrder historyOrder) {
    int insertIndex = 0;
    int replacementIndex = -1;
    Date newHistoryOrderTime = getOrderDoneTime(historyOrder);
    if (!lastHistoryOrderTimeByInstanceIndex.containsKey(instanceIndex)
      || lastHistoryOrderTimeByInstanceIndex.get(instanceIndex) < newHistoryOrderTime.getTime()) {
      lastHistoryOrderTimeByInstanceIndex.put(instanceIndex, newHistoryOrderTime.getTime());
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
  public CompletableFuture<Void> onDealAdded(int instanceIndex, MetatraderDeal newDeal) {
    int insertIndex = 0;
    int replacementIndex = -1;
    Date newDealTime = newDeal.time.getDate();
    if (!lastDealTimeByInstanceIndex.containsKey(instanceIndex)
      || lastDealTimeByInstanceIndex.get(instanceIndex) < newDealTime.getTime()) {
      lastDealTimeByInstanceIndex.put(instanceIndex, newDealTime.getTime());
    }
    for (int i = deals.size() - 1; i >= 0; i--) {
      MetatraderDeal deal = deals.get(i);
      Date dealTime = deal.time.getDate();
      int timeComparing = dealTime.compareTo(newDealTime);
      if (timeComparing < 0 || (timeComparing == 0 && deal.id.compareTo(newDeal.id) <= 0)) {
        if (timeComparing == 0 && deal.id.equals(newDeal.id) && deal.entryType == newDeal.entryType) {
          replacementIndex = i;
        } else {
          insertIndex = i + 1;
        }
        break;
      }
    }
    if (replacementIndex != -1) {
      deals.set(replacementIndex, newDeal);
      fileManager.setStartNewDealIndex(replacementIndex);
    }
    else {
      deals.add(insertIndex, newDeal);
      fileManager.setStartNewDealIndex(insertIndex);
    }
    return CompletableFuture.completedFuture(null);
  }
  
  private Date getOrderDoneTime(MetatraderOrder order) {
    return order.doneTime != null ? order.doneTime.getDate() : Date.from(Instant.ofEpochSecond(0));
  }
}