package cloud.metaapi.sdk.clients.meta_api;

import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Receives notifications about server-side communication latencies
 */
public abstract class LatencyListener {

    /**
     * Contains request latency information
     */
    public static class ResponseTimestamps {
        /**
         * Time when request processing have started on client side
         */
        public IsoTime clientProcessingStarted;
        /**
         * Time when request processing have started on server side
         */
        public IsoTime serverProcessingStarted;
        /**
         * Time when request processing have finished on server side
         */
        public IsoTime serverProcessingFinished;
        /**
         * Time when request processing have finished on client side
         */
        public IsoTime clientProcessingFinished;
    }
    
    /**
     * Invoked with latency information when application receives a response to RPC request
     * @param accountId account id
     * @param type request type
     * @param timestamps request timestamps object containing latency information
     * @return completable future which resolves when latency information is processed
     */
    public CompletableFuture<Void> onResponse(String accountId, String type, ResponseTimestamps timestamps) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Contains latency information about price streaming
     */
    public static class SymbolPriceTimestamps {
        /**
         * Time the event was generated on exchange side
         */
        public IsoTime eventGenerated;
        /**
         * Time the event processing have started on server side
         */
        public IsoTime serverProcessingStarted;
        /**
         * Time the event processing have finished on server side
         */
        public IsoTime serverProcessingFinished;
        /**
         * Time the event processing have finished on client side
         */
        public IsoTime clientProcessingFinished;
    }
    
    /**
     * Invoked with latency information when application receives symbol price update event
     * @param accountId account id
     * @param symbol price symbol
     * @param timestamps timestamps object containing latency information about price streaming
     * @return completable future which resolves when latency information is processed
     */
    public CompletableFuture<Void> onSymbolPrice(String accountId, String symbol, SymbolPriceTimestamps timestamps) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Contains latency information about update streaming
     */
    public static class UpdateTimestamps {
        /**
         * Time the event was generated on exchange side
         */
        public IsoTime eventGenerated;
        /**
         * Time the event processing have started on server side
         */
        public IsoTime serverProcessingStarted;
        /**
         * Time the event processing have finished on server side
         */
        public IsoTime serverProcessingFinished;
        /**
         * Time the event processing have finished on client side
         */
        public IsoTime clientProcessingFinished;
    }
    
    /**
     * Invoked with latency information when application receives update event
     * @param accountId account id
     * @param timestamps timestamps object containing latency information about update streaming
     * @return completable future which resolves when latency information is processed
     */
    public CompletableFuture<Void> onUpdate(String accountId, UpdateTimestamps timestamps) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Contains latency information about a trade
     */
    public static class TradeTimestamps {
        /**
         * Time when request processing have started on client side
         */
        public IsoTime clientProcessingStarted;
        /**
         * Time when event processing have started on server side
         */
        public IsoTime serverProcessingStarted;
        /**
         * Time when event processing have finished on server side
         */
        public IsoTime serverProcessingFinished;
        /**
         * Time when event processing have finished on client side
         */
        public IsoTime clientProcessingFinished;
        /**
         * Time the trade execution was started on server side
         */
        public IsoTime tradeStarted;
        /**
         * Time the trade was executed on exchange side
         */
        public IsoTime tradeExecuted;
    }
    
    /**
     * Invoked with latency information when application receives trade response
     * @param accountId account id
     * @param timestamps timestamps object containing latency information about a trade
     * @return completable future which resolves when latency information is processed
     */
    public CompletableFuture<Void> onTrade(String accountId, TradeTimestamps timestamps) {
        return CompletableFuture.completedFuture(null);
    }
}