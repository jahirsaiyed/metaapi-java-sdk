package cloud.metaapi.sdk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.RetryOptions;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.SubscriptionManager;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.util.JsonMapper;

class SyncStabilityTest {

  static ObjectMapper jsonMapper = JsonMapper.getInstance();
  static MetatraderAccountInformation accountInformation = new MetatraderAccountInformation() {{
    broker = "True ECN Trading Ltd";
    currency = "USD";
    server = "ICMarketsSC-Demo";
    balance = 7319.9;
    equity = 7306.649913200001;
    margin = 184.1;
    freeMargin = 7120.22;
    leverage = 100;
    marginLevel = 3967.58283542;
  }};
  static SocketIOServer server;
  static SocketIOClient socket;
  
  static class FakeServer {
    
    public Timer statusTask;
    public Consumer<SocketIOClient> connectListener = null;
    public Consumer<JsonNode> requestListener = null;
    private boolean enableStatusTask = true;
    
    public CompletableFuture<Void> authenticate(JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "authenticated");
        response.set("accountId", data.get("accountId"));
        response.put("instanceIndex", 0);
        response.put("replicas", 1);
        response.put("host", "ps-mpa-0");
        socket.sendEvent("synchronization", response.toString());
      });
    }
    
    public CompletableFuture<Void> emitStatus(String accountId) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("connected", true);
        response.put("authenticated", true);
        response.put("type", "status");
        response.put("accountId", accountId);
        response.put("instanceIndex", 0);
        response.put("replicas", 1);
        response.put("host", "ps-mpa-0");
        ObjectNode healthStatus = jsonMapper.createObjectNode();
        healthStatus.put("rpcApiHealthy", true);
        response.set("healthStatus", healthStatus);
        response.put("connectionId", accountId);
        socket.sendEvent("synchronization", response.toString());
      });
    }

    public CompletableFuture<Void> respondAccountInformation(JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        response.set("accountId", data.get("accountId"));
        response.set("requestId", data.get("requestId"));
        response.set("accountInformation", jsonMapper.valueToTree(accountInformation));
        socket.sendEvent("response", response.toString());
      });
    }

    public CompletableFuture<Void> syncAccount(JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        try {
          ObjectNode synchronizationStartedPacket = jsonMapper.createObjectNode();
          synchronizationStartedPacket.put("type", "synchronizationStarted");
          synchronizationStartedPacket.set("accountId", data.get("accountId"));
          synchronizationStartedPacket.put("instanceIndex", 0);
          synchronizationStartedPacket.set("synchronizationId", data.get("requestId"));
          synchronizationStartedPacket.put("host", "ps-mpa-0");
          socket.sendEvent("synchronization", synchronizationStartedPacket.toString());
          Thread.sleep(50);
          ObjectNode accountInformationPacket = jsonMapper.createObjectNode();
          accountInformationPacket.put("type", "accountInformation");
          accountInformationPacket.set("accountId", data.get("accountId"));
          accountInformationPacket.set("accountInformation", jsonMapper.valueToTree(accountInformation));
          accountInformationPacket.put("instanceIndex", 0);
          accountInformationPacket.put("host", "ps-mpa-0");
          socket.sendEvent("synchronization", accountInformationPacket.toString());
          Thread.sleep(50);
          ObjectNode specificationsPacket = jsonMapper.createObjectNode();
          specificationsPacket.put("type", "specifications");
          specificationsPacket.set("accountId", data.get("accountId"));
          specificationsPacket.set("specifications", jsonMapper.valueToTree(Lists.emptyList()));
          specificationsPacket.put("instanceIndex", 0);
          specificationsPacket.put("host", "ps-mpa-0");
          socket.sendEvent("synchronization", specificationsPacket.toString());
          Thread.sleep(50);
          ObjectNode orderSynchronizationFinishedPacket = jsonMapper.createObjectNode();
          orderSynchronizationFinishedPacket.put("type", "orderSynchronizationFinished");
          orderSynchronizationFinishedPacket.set("accountId", data.get("accountId"));
          orderSynchronizationFinishedPacket.put("instanceIndex", 0);
          orderSynchronizationFinishedPacket.set("synchronizationId", data.get("requestId"));
          orderSynchronizationFinishedPacket.put("host", "ps-mpa-0");
          socket.sendEvent("synchronization", orderSynchronizationFinishedPacket.toString());
          Thread.sleep(50);
          ObjectNode dealSynchronizationFinishedPacket = jsonMapper.createObjectNode();
          dealSynchronizationFinishedPacket.put("type", "dealSynchronizationFinished");
          dealSynchronizationFinishedPacket.set("accountId", data.get("accountId"));
          dealSynchronizationFinishedPacket.put("instanceIndex", 0);
          dealSynchronizationFinishedPacket.set("synchronizationId", data.get("requestId"));
          dealSynchronizationFinishedPacket.put("host", "ps-mpa-0");
          socket.sendEvent("synchronization", dealSynchronizationFinishedPacket.toString());
        } catch (InterruptedException err) {
          err.printStackTrace();
        }
      });
    }

    public CompletableFuture<Void> respond(JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        response.set("accountId", data.get("accountId"));
        response.set("requestId", data.get("requestId"));
        socket.sendEvent("response", response.toString());
      });
    }

    public void enableSync() {
      requestListener = (data) -> {
        try {
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            Thread.sleep(200);
            respond(data).join();
            statusTask = new Timer();
            if (enableStatusTask) {
              statusTask.schedule(new TimerTask() {
                @Override
                public void run() {
                  if (enableStatusTask) {
                    emitStatus(data.get("accountId").asText());
                  }
                }
              }, 100, 100);
            }
            Thread.sleep(50);
            authenticate(data).join();
          } else if (type.equals("synchronize")) {
            respond(data).join();
            Thread.sleep(50);
            syncAccount(data).join();
          } else if (type.equals("waitSynchronized") || type.equals("unsubscribe")) {
            respond(data).join();
          } else if (type.equals("getAccountInformation")) {
            respondAccountInformation(data).join();
          }
        } catch (Exception err) {
          err.printStackTrace();
        }
      };
    }

    public void disableSync() {
      requestListener = (data) -> {
        respond(data);
      };
    }
    
    public void enableStatusTask() {
      enableStatusTask = true;
    }
    
    public void disableStatusTask() {
      enableStatusTask = false;
      statusTask.cancel();
    }
    
    public void start() {
      server.addConnectListener(new ConnectListener() {
        @Override
        public void onConnect(SocketIOClient connected) {
          if (connectListener != null) {
            connectListener.accept(connected);
          }
        }
      });
      server.addEventListener("request", Object.class, new DataListener<Object>() {
        @Override
        public void onData(SocketIOClient client, Object request, AckRequest ackSender) throws Exception {
          if (client == socket && requestListener != null) {
            requestListener.accept(jsonMapper.valueToTree(request));
          }
        }
      });
      connectListener = (connected) -> {
        socket = connected;
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        socket.sendEvent("response", response.toString());
        enableSync();
      };
    };
  }
  
  static void startWebsocketServer() {
    startWebsocketServer(6785);
  }
  
  static void startWebsocketServer(int port) {
    Configuration serverConfiguration = new Configuration();
    serverConfiguration.setPort(port);
    serverConfiguration.setContext("/ws");
    server = new SocketIOServer(serverConfiguration);
    server.start();
  }
  
  static void stopWebsocketServer() {
    server.stop();
  }
  
  FakeServer fakeServer;
  MetaApiWebsocketClient websocketClient;
  MetaApiConnection connection;
  MetaApi api;
  
  @BeforeAll
  static void setUpBeforeClass() {
    startWebsocketServer();
  }
  
  @AfterAll
  static void tearDownAfterClass() {
    stopWebsocketServer();
  }
  
  @BeforeEach
  void setUp() throws Exception {
    Thread.sleep(5000);
    api = new MetaApi("token", new MetaApi.Options() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 10;
      retryOpts = new RetryOptions() {{
        retries = 3;
        minDelayInSeconds = 1;
        maxDelayInSeconds = 5;
      }};
    }});
    MetatraderAccountClient accountClient = Mockito.spy((MetatraderAccountClient) FieldUtils.readField(
      api.getMetatraderAccountApi(), "metatraderAccountClient", true));
    Mockito.doReturn(CompletableFuture.completedFuture(new MetatraderAccountDto() {{
      _id = "accountId";
      login = "50194988";
      name = "mt5a";
      server = "ICMarketsSC-Demo";
      provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
      magic = 123456;
      application = "MetaApi";
      connectionStatus = ConnectionStatus.DISCONNECTED;
      state = DeploymentState.DEPLOYED;
      type = "cloud";
      accessToken = "2RUnoH1ldGbnEneCoqRTgI4QO1XOmVzbH5EVoQsA";
    }})).when(accountClient).getAccount(Mockito.any());
    FieldUtils.writeField(api.getMetatraderAccountApi(), "metatraderAccountClient", accountClient, true);
    websocketClient = (MetaApiWebsocketClient) FieldUtils.readField(api, "metaApiWebsocketClient", true);
    websocketClient.setUrl("http://localhost:6785");
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 7500, true);
    fakeServer = new FakeServer();
    fakeServer.start();
    websocketClient.connect().join();
  }
  
  @AfterEach
  void tearDown() throws IllegalAccessException, InterruptedException {
    fakeServer.disableStatusTask();
    fakeServer.requestListener = null;
    fakeServer.connectListener = null;
    SubscriptionManager subscriptionManager = (SubscriptionManager) FieldUtils.readField(
      websocketClient, "subscriptionManager", true);
    subscriptionManager.cancelAccount("accountId");
    websocketClient.close();
    socket.disconnect();
    server.removeAllListeners("connect");
    server.removeAllListeners("request");
  }
  
  @Test
  void testSynchronizesAccount() {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  }
  
  @Test
  void testReconnectsOnServerSocketCrash() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    socket.disconnect();
    Thread.sleep(100);
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
  }

  @Test
  void testSetsStateToDisconnectedOnTimeout() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    fakeServer.disableStatusTask();
    fakeServer.connectListener = (connected) -> {
      connected.disconnect();
    };
    socket.disconnect();
    Thread.sleep(10000);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
  }

  @Test
  void testResubscribesOnTimeout() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    fakeServer.statusTask.cancel();
    Thread.sleep(8500);
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  }

  @Test
  void testSynchronizesIfSubscribeResponseArrivesAfterSynchronization() {
    fakeServer.requestListener = (data) -> {
      try {
        String type = data.get("type").asText();
        if (type.equals("subscribe")) {
          Thread.sleep(200);
          fakeServer.statusTask = new Timer();
          fakeServer.statusTask.schedule(new TimerTask() {
            @Override
            public void run() {
              fakeServer.emitStatus(data.get("accountId").asText());
            }
          }, 100, 100);
          fakeServer.authenticate(data).join();
          Thread.sleep(400);
          fakeServer.respond(data).join();
        } else if (type.equals("synchronize")) {
          fakeServer.respond(data).join();
          fakeServer.syncAccount(data).join();
        } else if (type.equals("waitSynchronized")) {
          fakeServer.respond(data).join();
        } else if (type.equals("getAccountInformation")) {
          fakeServer.respondAccountInformation(data).join();
        }
      } catch (Exception err) {
        err.printStackTrace();
      }
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  }

  @Test
  void testWaitsUntilAccountIsRedeployedAfterDisconnect() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    fakeServer.statusTask.cancel();
    fakeServer.disableSync();
    ObjectNode disconnectPacket = jsonMapper.createObjectNode();
    disconnectPacket.put("type", "disconnected");
    disconnectPacket.put("accountId", "accountId");
    disconnectPacket.put("host", "ps-mpa-0");
    disconnectPacket.put("instanceIndex", 0);
    socket.sendEvent("synchronization", disconnectPacket.toString());
    Thread.sleep(2500);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(25000);
    fakeServer.enableSync();
    Thread.sleep(2500);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(25000);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  }

  @Test
  void testResubscribesImmediatelyAfterDisconnectOnStatusPacket() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    fakeServer.statusTask.cancel();
    fakeServer.disableSync();
    ObjectNode disconnectPacket = jsonMapper.createObjectNode();
    disconnectPacket.put("type", "disconnected");
    disconnectPacket.put("accountId", "accountId");
    disconnectPacket.put("host", "ps-mpa-0");
    disconnectPacket.put("instanceIndex", 0);
    socket.sendEvent("synchronization", disconnectPacket.toString());
    Thread.sleep(2500);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(25000);
    fakeServer.enableSync();
    fakeServer.emitStatus("accountId").join();
    Thread.sleep(2500);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  };
  
  @Test
  void testReconnectsAfterServerRestarts() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    for (int i = 0; i < 2; i++) {
      fakeServer.statusTask.cancel();
      stopWebsocketServer();
      Thread.sleep(25000);
      startWebsocketServer();
      fakeServer.start();
      Thread.sleep(200);
    }
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
  };
  
  @Test
  void testSynchronizesIfConnectingWhileServerIsRebooting() {
    stopWebsocketServer();
    startWebsocketServer(9000);
    fakeServer.start();
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        stopWebsocketServer();
        startWebsocketServer();
        fakeServer.start();
      }
    }, 3000);
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  };
}
