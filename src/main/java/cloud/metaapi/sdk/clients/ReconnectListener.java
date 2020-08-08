package cloud.metaapi.sdk.clients;

import java.util.concurrent.CompletableFuture;

/**
 * Defines interface for a websocket reconnect listener class
 */
public interface ReconnectListener {

    /**
     * Invoked when connection to MetaTrader terminal re-established
     * @return completable future which resolves when the asynchronous event is processed
     */
    public CompletableFuture<Void> onReconnected();
}