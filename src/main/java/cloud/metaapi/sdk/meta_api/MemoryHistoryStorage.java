package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    
    /**
     * Constructs the in-memory history store instance
     * @param accountId account id
     */
    public MemoryHistoryStorage(String accountId) {
        super();
        this.accountId = accountId;
        fileManager = ServiceProvider.createHistoryFileManager(this.accountId, this);
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
    public void reset() {
        deals.clear();
        historyOrders.clear();
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
    public CompletableFuture<IsoTime> getLastHistoryOrderTime() {
        Date maxOrderDoneTime = Date.from(Instant.ofEpochSecond(0));
        for (MetatraderOrder order : historyOrders) {
            Date orderDoneTime = getOrderDoneTime(order);
            if (orderDoneTime.compareTo(maxOrderDoneTime) > 0) maxOrderDoneTime = orderDoneTime;
        }
        return CompletableFuture.completedFuture(new IsoTime(maxOrderDoneTime));
    }

    @Override
    public CompletableFuture<IsoTime> getLastDealTime() {
        Date maxDealTime = Date.from(Instant.ofEpochSecond(0));
        for (MetatraderDeal deal : deals)
            if (deal.time.getDate().compareTo(maxDealTime) > 0) maxDealTime = deal.time.getDate();
        return CompletableFuture.completedFuture(new IsoTime(maxDealTime));
    }

    @Override
    public CompletableFuture<Void> onHistoryOrderAdded(MetatraderOrder historyOrder) {
        int insertIndex = 0;
        int replacementIndex = -1;
        Date newHistoryOrderTime = getOrderDoneTime(historyOrder);
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
    public CompletableFuture<Void> onDealAdded(MetatraderDeal newDeal) {
        int insertIndex = 0;
        int replacementIndex = -1;
        Date newDealTime = newDeal.time.getDate();
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