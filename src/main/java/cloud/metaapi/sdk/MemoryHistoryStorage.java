package cloud.metaapi.sdk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.clients.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.models.MetatraderOrder;

/**
 * History storage which stores MetaTrader history in RAM
 */
public class MemoryHistoryStorage extends HistoryStorage {

    private List<MetatraderDeal> deals = new ArrayList<>();
    private List<MetatraderOrder> historyOrders = new ArrayList<>();
    
    /**
     * Returns all deals stored in history storage
     * @return all deals stored in history storage
     */
    public List<MetatraderDeal> getDeals() {
        return deals;
    }
    
    /**
     * Returns all history orders stored in history storage
     * @return all history orders stored in history storage
     */
    public List<MetatraderOrder> getHistoryOrders() {
        return historyOrders;
    }
    
    /**
     * Resets the storage. Intended for use in tests
     */
    public void reset() {
        deals.clear();
        historyOrders.clear();
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
        if (replacementIndex != -1) historyOrders.set(replacementIndex, historyOrder);
        else historyOrders.add(insertIndex, historyOrder);
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
        if (replacementIndex != -1) deals.set(replacementIndex, newDeal);
        else deals.add(insertIndex, newDeal);
        return CompletableFuture.completedFuture(null);
    }
    
    private Date getOrderDoneTime(MetatraderOrder order) {
        return order.doneTime.isPresent() ? order.doneTime.get().getDate() : Date.from(Instant.ofEpochSecond(0));
    }
}