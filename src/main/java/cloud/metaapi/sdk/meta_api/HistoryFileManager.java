package cloud.metaapi.sdk.meta_api;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderOrder;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * History storage file manager which saves and loads history on disk
 */
public class HistoryFileManager {
    
    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private static Logger logger = Logger.getLogger(HistoryFileManager.class);
    
    private String accountId;
    private String application;
    private HistoryStorage historyStorage;
    private List<Integer> dealsSize = new ArrayList<>();
    private int startNewDealIndex = -1;
    private List<Integer> historyOrdersSize = new ArrayList<>();
    private int startNewOrderIndex = -1;
    private Timer updateDiskStorageJob = null;
    private boolean isUpdating = false;
    
    /**
     * Defines interval between update jobs. Intended to be overriden in tests.
     */
    protected int updateJobIntervalInMilliseconds = 60000;
    
    /**
     * Class to store history deals and orders
     */
    public static class History {
        /**
         * List of history deals
         */
        public List<MetatraderDeal> deals;
        /**
         * List of history orders
         */
        public List<MetatraderOrder> historyOrders;
    }
    
    /**
     * Constructs the history file manager instance
     * @param accountId accound id
     * @param application MetaApi application id
     * @param historyStorage history storage
     */
    public HistoryFileManager(String accountId, String application, HistoryStorage historyStorage) {
        this.accountId = accountId;
        this.application = application;
        this.historyStorage = historyStorage;
    }
    
    /**
     * Starts a job to periodically save history on disk
     */
    public void startUpdateJob() {
        if (updateDiskStorageJob == null) {
            final HistoryFileManager self = this;
            updateDiskStorageJob = new Timer();
            updateDiskStorageJob.schedule(new TimerTask() {
                @Override
                public void run() {
                    self.updateDiskStorage().exceptionally(e -> {
                        logger.error("Failed update disk storage of account " + accountId, e);
                        return null;
                    });
                }
            }, updateJobIntervalInMilliseconds, updateJobIntervalInMilliseconds);
        }
    }
    
    /**
     * Stops a job to periodically save history on disk
     */
    public void stopUpdateJob() {
        if (updateDiskStorageJob != null) {
            updateDiskStorageJob.cancel();
            updateDiskStorageJob = null;
        }
    }
    
    /**
     * Helper function to calculate object size in bytes in utf-8 encoding
     * @return size of object in bytes
     */
    public int getItemSize(Object item) throws JsonProcessingException {
        return jsonMapper.writeValueAsBytes(item).length;
    }
    
    /**
     * Sets the index of the earliest changed historyOrder record
     * @param index of the earliest changed record 
     */
    public void setStartNewOrderIndex(int index) {
        if (startNewOrderIndex > index || startNewOrderIndex == -1) {
            startNewOrderIndex = index;
        }
    }
    
    /**
     * Sets the index of the earliest changed deal record
     * @param index of the earliest changed record 
     */
    public void setStartNewDealIndex(int index) {
        if (startNewDealIndex > index || startNewDealIndex == -1) {
            startNewDealIndex = index;
        }
    }
    
    /**
     * Retrieves history from saved file
     * @return completable future resolving with history of deals and orders
     */
    public CompletableFuture<History> getHistoryFromDisk() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                History history = new History();
                Pair<List<MetatraderDeal>, List<Integer>> dealsAndSizes = 
                    readHistoryItemsAndSizes(MetatraderDeal.class, "deals");
                history.deals = dealsAndSizes.getLeft();
                dealsSize = dealsAndSizes.getRight();
                Pair<List<MetatraderOrder>, List<Integer>> historyOrdersAndSizes =
                    readHistoryItemsAndSizes(MetatraderOrder.class, "historyOrders");
                history.historyOrders = historyOrdersAndSizes.getLeft();
                historyOrdersSize = historyOrdersAndSizes.getRight();
                return history;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Saves unsaved history items to disk storage
     * @return completable future which resolves when disk storage is updated
     */
    public CompletableFuture<Void> updateDiskStorage() {
        return CompletableFuture.runAsync(() -> {
            if (!isUpdating) {
                isUpdating = true;
                try {
                    Files.createDirectories(FileSystems.getDefault().getPath(".", ".metaapi"));
                    dealsSize = updateDiskStorageWith(
                        "deals", startNewDealIndex, historyStorage.getDeals(), dealsSize);
                    startNewDealIndex = -1;
                    historyOrdersSize = updateDiskStorageWith(
                        "historyOrders", startNewOrderIndex, historyStorage.getHistoryOrders(), historyOrdersSize);
                    startNewOrderIndex = -1;
                } catch (IOException e) {
                    logger.error("Error updating disk storage for account " + accountId, e);
                }
                isUpdating = false;
            }
        });
    }
    
    /**
     * Deletes storage files from disk
     * @return completable future which resolves when the storage is deleted
     */
    public CompletableFuture<Void> deleteStorageFromDisk() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.delete(getFilePath("deals"));
                Files.delete(getFilePath("historyOrders"));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    private <T> Pair<List<T>, List<Integer>> readHistoryItemsAndSizes(
        Class<T> itemType, String type
    ) throws IOException {
        Path path = getFilePath(type);
        if (Files.exists(path)) {
            List<T> items;
            List<Integer> sizes;
            try {
                String fileContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                items = jsonMapper.readValue(
                    fileContent, jsonMapper.getTypeFactory().constructCollectionType(List.class, itemType)
                );
                sizes = readItemSizes(items, type);
                return Pair.of(items, sizes);
            } catch (IOException e) {
                logger.error("Failed to read " + type + " history storage of account " + accountId, e);
                Files.delete(path);
            }
        }
        List<T> emptyTypedList = new ArrayList<>();
        List<Integer> emptyIntegerList = new ArrayList<>();
        return Pair.of(emptyTypedList, emptyIntegerList);
    }
    
    private <T> List<Integer> updateDiskStorageWith(
        String type, int startNewItemIndex, List<T> items, List<Integer> itemSizes
    ) throws IOException {
        if (startNewItemIndex != -1) {
            Path filePath = getFilePath(type);
            if (!Files.exists(filePath)) {
                try {
                    Files.write(filePath, jsonMapper.writeValueAsBytes(items));
                } catch (IOException e) {
                    logger.error("Error saving " + items + " on disk for account " + accountId, e);
                }
                return readItemSizes(items, type);
            } else {
                List<T> replaceItems = items.subList(startNewItemIndex, items.size());
                return replaceRecords(type, startNewItemIndex, replaceItems, itemSizes);
            }
        }
        return itemSizes;
    }
    
    private <T> List<Integer> replaceRecords(
        String type, int startIndex, List<T> replaceItems, List<Integer> sizeArray
    ) throws IOException {
        Path filePath = getFilePath(type);
        long fileSize = Files.size(filePath);
        if (startIndex == 0) {
            Files.write(filePath, jsonMapper.writeValueAsBytes(replaceItems));
        } else {
            List<Integer> replacedItems = sizeArray.subList(startIndex, sizeArray.size());
            // replacedItems.size - skip commas, replacedItems.reduce - skip item sizes, 1 - skip ] at the end
            long startPosition = fileSize - replacedItems.size()
                - replacedItems.stream().reduce((a, b) -> a + b).orElse(0) - 1;
            FileOutputStream fileStream = new FileOutputStream(filePath.toString(), true);
            FileChannel fileChannel = fileStream.getChannel();
            fileChannel.truncate(startPosition);
            fileChannel.write(
                Charset.forName("UTF-8").encode("," + jsonMapper.writeValueAsString(replaceItems).substring(1)),
                startPosition
            );
            fileStream.close();
        }
        return Stream.concat(
            sizeArray.subList(0, startIndex).stream(),
            readItemSizes(replaceItems, type).stream()
        ).collect(Collectors.toList());
    }
    
    private <T> List<Integer> readItemSizes(List<T> items, String type) {
        return items.stream().map(item -> {
            try {
                return getItemSize(item);
            } catch (JsonProcessingException e) {
                logger.error("Failed to read the " + type + " size of account " + accountId, e);
                return null;
            }
        }).collect(Collectors.toList());
    }
    
    private Path getFilePath(String type) {
        return FileSystems.getDefault().getPath(".", ".metaapi", accountId + "-" + application + "-" + type + ".bin");
    }
}