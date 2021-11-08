package cloud.metaapi.sdk.meta_api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.Async;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Manages account connections
 */
public class ConnectionRegistry {
  
  private MetaApiWebsocketClient metaApiWebsocketClient;
  private Map<String, MetaApiConnection> connections;
  private Map<String, CompletableFuture<Void>> connectionLocks;
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
    this.connections = new ConcurrentHashMap<>();
    this.connectionLocks = new ConcurrentHashMap<>();
  }
  
  /**
   * Creates and returns a new account connection if doesnt exist, otherwise returns old
   * @param account MetaTrader account id to connect to
   * @param historyStorage terminal history storage
   * @return Completable future resolving with connection object
   */
  public CompletableFuture<MetaApiConnection> connect(MetatraderAccount account, HistoryStorage historyStorage) {
    return connect(account, historyStorage, null);
  }
  
  /**
   * Creates and returns a new account connection if doesnt exist, otherwise returns old
   * @param account MetaTrader account id to connect to
   * @param historyStorage terminal history storage
   * @param historyStartTime history start time, or {@code null}
   * @return Completable future resolving with connection object
   */
  public CompletableFuture<MetaApiConnection> connect(MetatraderAccount account, HistoryStorage historyStorage,
    IsoTime historyStartTime) {
    if (connections.containsKey(account.getId())) {
      return CompletableFuture.completedFuture(connections.get(account.getId()));
    } else {
      return Async.supply(() -> {
        while (connectionLocks.containsKey(account.getId())) {
          connectionLocks.get(account.getId()).join();
        }
        if (connections.containsKey(account.getId())) {
          return connections.get(account.getId());
        }
        CompletableFuture<Void> connectionLockResolve = new CompletableFuture<>();
        connectionLocks.put(account.getId(), connectionLockResolve);
        MetaApiConnection connection = ServiceProvider.createMetaApiConnection(
          metaApiWebsocketClient, account, historyStorage, this, historyStartTime);
        try {
          connection.initialize().join();
          connection.subscribe().join();
          connections.put(account.getId(), connection);
        } finally {
          connectionLocks.remove(account.getId());
          connectionLockResolve.complete(null);
        }
        return connection;
      });
    }
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