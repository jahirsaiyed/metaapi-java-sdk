package cloud.metaapi.sdk.meta_api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Manages account connections
 */
public class ConnectionRegistry {
    
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private Map<String, MetaApiConnection> connections;
    private String application;
    
    /**
     * Constructs a MetaTrader connection registry instance with default parameters
     * @param metaApiWebsocketClient MetaApi websocket client
     */
    public ConnectionRegistry(MetaApiWebsocketClient metaApiWebsocketClient) {
        this(metaApiWebsocketClient, null);
    }
    
    /**
     * Constructs a MetaTrader connection registry instance
     * @param metaApiWebsocketClient MetaApi websocket client
     * @param application id, or {@code null}. By default is {@code MetaApi}
     */
    public ConnectionRegistry(MetaApiWebsocketClient metaApiWebsocketClient, String application) {
        this.metaApiWebsocketClient = metaApiWebsocketClient;
        this.application = (application != null ? application : "MetaApi");
        this.connections = new HashMap<>();
    }
    
    /**
     * Creates and returns a new account connection if doesnt exist, otherwise returns old
     * @param account MetaTrader account id to connect to
     * @param historyStorage terminal history storage
     */
    public CompletableFuture<MetaApiConnection> connect(MetatraderAccount account, HistoryStorage historyStorage) {
        return CompletableFuture.supplyAsync(() -> {
            if (connections.containsKey(account.getId())) {
                return connections.get(account.getId());
            } else {
                MetaApiConnection connection = ServiceProvider.createMetaApiConnection(
                    metaApiWebsocketClient, account, historyStorage, this);
                connection.initialize().join();
                connection.subscribe().join();
                connections.put(account.getId(), connection);
                return connection;
            }
        });
    }
    
    /**
     * Removes an account from registry
     * @param accountId MetaTrader account id to remove
     */
    public void remove(String accountId) {
        connections.remove(accountId);
    }
    
    /**
     * Returns application type
     * @return application type
     */
    public String getApplication() {
        return application;
    }
}