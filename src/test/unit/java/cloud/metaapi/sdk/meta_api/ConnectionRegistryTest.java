package cloud.metaapi.sdk.meta_api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link ConnectionRegistry}
 */
class ConnectionRegistryTest {
  
  private ConnectionRegistry registry;
  private MetaApiWebsocketClient metaApiWebsocketClient;
  private MemoryHistoryStorage storage;
  
  @BeforeEach
  void setUp() {
    ServiceProvider.setMetApiConnectionMock(new ServiceProvider.MetaApiConnectionProvider() {
      @Override
      public MetaApiConnection create(
        MetaApiWebsocketClient websocketClient,
        MetatraderAccount account,
        HistoryStorage historyStorage,
        ConnectionRegistry connectionRegistry,
        IsoTime historyStartTime
      ) {
        MetaApiConnection metaApiConnection = new MetaApiConnection(websocketClient, account, historyStorage,
          connectionRegistry, historyStartTime);
        MetaApiConnection metaApiConnectionSpy = Mockito.spy(metaApiConnection);
        Mockito.doReturn(CompletableFuture.completedFuture(null)).when(metaApiConnectionSpy).initialize();
        Mockito.doReturn(CompletableFuture.completedFuture(null)).when(metaApiConnectionSpy).subscribe();
        return metaApiConnectionSpy;
      }
    });
    
    metaApiWebsocketClient = Mockito.mock(MetaApiWebsocketClient.class, Mockito.RETURNS_DEEP_STUBS);
    storage = Mockito.mock(MemoryHistoryStorage.class, Mockito.RETURNS_DEEP_STUBS);
    
    Mockito.when(storage.getLastHistoryOrderTime())
      .thenReturn(CompletableFuture.completedFuture(new IsoTime("2020-01-01T00:00:00.000Z")));
    Mockito.when(storage.getLastDealTime())
      .thenReturn(CompletableFuture.completedFuture(new IsoTime("2020-01-02T00:00:00.000Z")));
    Mockito.when(storage.loadDataFromDisk())
      .thenReturn(CompletableFuture.completedFuture(null));
    registry = new ConnectionRegistry(metaApiWebsocketClient);
  }

  /**
   * Tests {@link ConnectionRegistry#connect(MetatraderAccount, HistoryStorage)}
   */
  @Test
  void testConnectsAndAddsConnectionToRegistry() throws IllegalAccessException {
    MetatraderAccount account = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account.getId()).thenReturn("id");
    MetaApiConnection connection = registry.connect(account, storage).join();
    assertEquals(storage, connection.getHistoryStorage());
    Mockito.verify(connection).initialize();
    Mockito.verify(connection).subscribe();
    @SuppressWarnings("unchecked")
    Map<String, MetaApiConnection> registryConnections = (Map<String, MetaApiConnection>) FieldUtils
      .readField(registry, "connections", true);
    assertEquals(connection, registryConnections.get("id"));
  }
  
  /**
   * Tests {@link ConnectionRegistry#connect(MetatraderAccount, HistoryStorage)}
   */
  @Test
  void testReturnsTheSameConnectionOnSecondConnectIfSameAccountId() throws IllegalAccessException  {
    MetatraderAccount account0 = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account0.getId()).thenReturn("id0");
    MetatraderAccount account1 = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account1.getId()).thenReturn("id1");
    MetaApiConnection connection0 = registry.connect(account0, storage).join();
    MetaApiConnection connection02 = registry.connect(account0, storage).join();
    MetaApiConnection connection1 = registry.connect(account1, storage).join();
    Mockito.verify(connection0).initialize();
    Mockito.verify(connection0).subscribe();
    Mockito.verify(connection1).initialize();
    Mockito.verify(connection1).subscribe();
    @SuppressWarnings("unchecked")
    Map<String, MetaApiConnection> registryConnections = (Map<String, MetaApiConnection>) FieldUtils
      .readField(registry, "connections", true);
    assertEquals(connection0, registryConnections.get("id0"));
    assertEquals(connection1, registryConnections.get("id1"));
    assertEquals(connection0, connection02);
    assertNotEquals(connection0, connection1);
  }
  
  /**
   * Test {@link ConnectionRegistry#remove(String)}
   */
  @Test
  void testRemovesTheAccountFromRegistry() throws IllegalAccessException {
    MetatraderAccount account0 = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account0.getId()).thenReturn("id0");
    MetatraderAccount account1 = Mockito.mock(MetatraderAccount.class);
    Mockito.when(account1.getId()).thenReturn("id1");
    MetaApiConnection connection0 = registry.connect(account0, storage).join();
    MetaApiConnection connection1 = registry.connect(account1, storage).join();
    @SuppressWarnings("unchecked")
    Map<String, MetaApiConnection> registryConnections = (Map<String, MetaApiConnection>) FieldUtils
      .readField(registry, "connections", true);
    assertEquals(connection0, registryConnections.get("id0"));
    assertEquals(connection1, registryConnections.get("id1"));
    registry.remove("id0");
    assertNull(registryConnections.get("id0"));
  }
}