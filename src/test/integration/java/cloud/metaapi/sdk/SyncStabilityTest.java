package cloud.metaapi.sdk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.SubscriptionManager;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountInformation;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.util.Js;
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
  static List<ObjectNode> errors = provideErrors();
  static int port = 6786;
  static SocketIOServer io;
  static Socket server;
  static long requestTimestamp;
  
  public static List<ObjectNode> provideErrors() {
    ObjectNode error1 = jsonMapper.createObjectNode();
    error1.put("id", 1);
    error1.put("error", "TooManyRequestsError");
    error1.put("message", "One user can connect to one server no more than 300 accounts. Current number of connected " + 
                          "accounts 300. For more information see https://metaapi.cloud/docs/client/rateLimiting/");
    ObjectNode error1Metadata = jsonMapper.createObjectNode();
    error1Metadata.put("maxAccountsPerUserPerServer", 300);
    error1Metadata.put("accountsCount", 300);
    error1Metadata.put("recommendedRetryTime", new IsoTime(Instant.now().plusMillis(20000)).toString());
    error1Metadata.put("type", "LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER_PER_SERVER");
    error1.set("metadata", error1Metadata);
    
    ObjectNode error2 = jsonMapper.createObjectNode();
    error2.put("id", 1);
    error2.put("error", "TooManyRequestsError");
    error2.put("message", "You have used all your account subscriptions quota. You have 50 account subscriptions available " + 
                          "and have used 50 subscriptions. Please deploy more accounts to get more subscriptions. For more " +
                          "information see https://metaapi.cloud/docs/client/rateLimiting/");
    ObjectNode error2Metadata = jsonMapper.createObjectNode();
    error2Metadata.put("maxAccountsPerUser", 50);
    error2Metadata.put("accountsCount", 50);
    error2Metadata.put("recommendedRetryTime", new IsoTime(Instant.now().plusMillis(20000)).toString());
    error2Metadata.put("type", "LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_USER");
    error2.set("metadata", error2Metadata);
    
    ObjectNode error3 = jsonMapper.createObjectNode();
    error3.put("id", 1);
    error3.put("error", "TooManyRequestsError");
    error3.put("message", "You can not subscribe to more accounts on this connection because server is out of capacity. " + 
                          "Please establish a new connection with a different client-id header value to switch to a " +
                          "different server. For more information see https://metaapi.cloud/docs/client/rateLimiting/");
    ObjectNode error3Metadata = jsonMapper.createObjectNode();
    error3Metadata.put("changeClientIdHeader", true);
    error3Metadata.put("recommendedRetryTime", new IsoTime(Instant.now().plusMillis(20000)).toString());
    error3Metadata.put("type", "LIMIT_ACCOUNT_SUBSCRIPTIONS_PER_SERVER");
    error3.set("metadata", error3Metadata);

    return Arrays.asList(error1, error2, error3);
  }
  
  static class Socket {
    public static Map<SocketIOClient, Socket> sockets = new HashMap<>();
    public SocketIOClient socket;
    public Consumer<JsonNode> requestListener = null;
    
    public static void removeRequestListeners() {
      sockets.values().forEach(socket -> socket.requestListener = null);
    }
  }
  
  static class FakeServer {
    
    public Map<String, Timer> statusTasks = new HashMap<>();
    public Consumer<Socket> connectListener = null;
    private boolean enableStatusTask = true;
    
    public CompletableFuture<Void> authenticate(Socket socket, JsonNode data) {
      return authenticate(socket, data, "ps-mpa-0");
    }
    
    public CompletableFuture<Void> authenticate(Socket socket, JsonNode data, String host) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "authenticated");
        response.set("accountId", data.get("accountId"));
        response.put("instanceIndex", 0);
        response.put("replicas", 1);
        response.put("host", host);
        socket.socket.sendEvent("synchronization", response.toString());
      });
    }
    
    public void deleteStatusTask(String accountId) {
      if (statusTasks.containsKey(accountId)) {
        statusTasks.get(accountId).cancel();
        statusTasks.remove(accountId);
      }
    }
    
    public CompletableFuture<Void> emitStatus(Socket socket, String accountId) {
      return emitStatus(socket, accountId, "ps-mpa-0");
    }
    
    public CompletableFuture<Void> emitStatus(Socket socket, String accountId, String host) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("connected", true);
        response.put("authenticated", true);
        response.put("type", "status");
        response.put("accountId", accountId);
        response.put("instanceIndex", 0);
        response.put("replicas", 1);
        response.put("host", host);
        ObjectNode healthStatus = jsonMapper.createObjectNode();
        healthStatus.put("rpcApiHealthy", true);
        response.set("healthStatus", healthStatus);
        response.put("connectionId", accountId);
        socket.socket.sendEvent("synchronization", response.toString());
      });
    }

    public CompletableFuture<Void> respondAccountInformation(Socket socket, JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        response.set("accountId", data.get("accountId"));
        response.set("requestId", data.get("requestId"));
        response.set("accountInformation", jsonMapper.valueToTree(accountInformation));
        socket.socket.sendEvent("response", response.toString());
      });
    }

    public CompletableFuture<Void> syncAccount(Socket socket, JsonNode data) {
      return syncAccount(socket, data, "ps-mpa-0");
    }
    
    public CompletableFuture<Void> syncAccount(Socket socket, JsonNode data, String host) {
      return CompletableFuture.runAsync(() -> {
        try {
          ObjectNode synchronizationStartedPacket = jsonMapper.createObjectNode();
          synchronizationStartedPacket.put("type", "synchronizationStarted");
          synchronizationStartedPacket.set("accountId", data.get("accountId"));
          synchronizationStartedPacket.put("instanceIndex", 0);
          synchronizationStartedPacket.set("synchronizationId", data.get("requestId"));
          synchronizationStartedPacket.put("host", host);
          socket.socket.sendEvent("synchronization", synchronizationStartedPacket.toString());
          Thread.sleep(50);
          ObjectNode accountInformationPacket = jsonMapper.createObjectNode();
          accountInformationPacket.put("type", "accountInformation");
          accountInformationPacket.set("accountId", data.get("accountId"));
          accountInformationPacket.set("accountInformation", jsonMapper.valueToTree(accountInformation));
          accountInformationPacket.put("instanceIndex", 0);
          accountInformationPacket.put("host", host);
          socket.socket.sendEvent("synchronization", accountInformationPacket.toString());
          Thread.sleep(50);
          ObjectNode specificationsPacket = jsonMapper.createObjectNode();
          specificationsPacket.put("type", "specifications");
          specificationsPacket.set("accountId", data.get("accountId"));
          specificationsPacket.set("specifications", jsonMapper.valueToTree(Lists.emptyList()));
          specificationsPacket.put("instanceIndex", 0);
          specificationsPacket.put("host", host);
          socket.socket.sendEvent("synchronization", specificationsPacket.toString());
          Thread.sleep(50);
          ObjectNode orderSynchronizationFinishedPacket = jsonMapper.createObjectNode();
          orderSynchronizationFinishedPacket.put("type", "orderSynchronizationFinished");
          orderSynchronizationFinishedPacket.set("accountId", data.get("accountId"));
          orderSynchronizationFinishedPacket.put("instanceIndex", 0);
          orderSynchronizationFinishedPacket.set("synchronizationId", data.get("requestId"));
          orderSynchronizationFinishedPacket.put("host", host);
          socket.socket.sendEvent("synchronization", orderSynchronizationFinishedPacket.toString());
          Thread.sleep(50);
          ObjectNode dealSynchronizationFinishedPacket = jsonMapper.createObjectNode();
          dealSynchronizationFinishedPacket.put("type", "dealSynchronizationFinished");
          dealSynchronizationFinishedPacket.set("accountId", data.get("accountId"));
          dealSynchronizationFinishedPacket.put("instanceIndex", 0);
          dealSynchronizationFinishedPacket.set("synchronizationId", data.get("requestId"));
          dealSynchronizationFinishedPacket.put("host", host);
          socket.socket.sendEvent("synchronization", dealSynchronizationFinishedPacket.toString());
        } catch (InterruptedException err) {
          err.printStackTrace();
        }
      });
    }

    public CompletableFuture<Void> respond(Socket socket, JsonNode data) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        response.set("accountId", data.get("accountId"));
        response.set("requestId", data.get("requestId"));
        socket.socket.sendEvent("response", response.toString());
      });
    }
    
    public CompletableFuture<Void> emitError(Socket socket, JsonNode data, int errorIndex,
      int retryAfterSeconds) {
      return CompletableFuture.runAsync(() -> {
        ObjectNode error = errors.get(errorIndex);
        ((ObjectNode) error.get("metadata")).put("recommendedRetryTime", new IsoTime(
          Instant.now().plusSeconds(retryAfterSeconds)).toString());
        error.set("requestId", data.get("requestId"));
        socket.socket.sendEvent("processingError", error.toString());
      });
    }
    
    public Consumer<Socket> enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            Thread.sleep(200);
            respond(socket, data).join();
            statusTasks.put(data.get("accountId").asText(), Js.setInterval(() -> {
              if (enableStatusTask) {
                emitStatus(socket, data.get("accountId").asText());
              }
            }, 100));
            Thread.sleep(50);
            authenticate(socket, data).join();
          } else if (type.equals("synchronize")) {
            respond(socket, data).join();
            Thread.sleep(50);
            syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            respondAccountInformation(socket, data).join();
          } else if (type.equals("unsubscribe")) {
            deleteStatusTask(data.get("accountId").asText());
            respond(socket, data).join();
          }
        } catch (Exception err) {
          err.printStackTrace();
        }
      };
    };

    public void enableSync(Socket socket) {
      enableSyncMethod.accept(socket);
    }

    public void disableSync() {
      server.requestListener = (data) -> {
        respond(server, data);
      };
    }
    
    public void disableStatusTask() {
      enableStatusTask = false;
      for (Timer statusTask : statusTasks.values()) {
        statusTask.cancel();
      }
    }
    
    public void start() {
      io.addConnectListener(new ConnectListener() {
        @Override
        public void onConnect(SocketIOClient connected) {
          if (connectListener != null) {
            Socket socket = new Socket() {{socket = connected;}};
            Socket.sockets.put(connected, socket);
            connectListener.accept(socket);
          }
        }
      });
      io.addEventListener("request", Object.class, new DataListener<Object>() {
        @Override
        public void onData(SocketIOClient client, Object request, AckRequest ackSender) throws Exception {
          Socket socket = Socket.sockets.get(client);
          if (socket.requestListener != null) {
            CompletableFuture.runAsync(() -> socket.requestListener.accept(jsonMapper.valueToTree(request)));
          }
        }
      });
      connectListener = (socket) -> {
        server = socket;
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("type", "response");
        server.socket.sendEvent("response", response.toString());
        enableSync(socket);
      };
    };
  }
  
  static void startWebsocketServer() {
    startWebsocketServer(port);
  }
  
  static void startWebsocketServer(int port) {
    Configuration serverConfiguration = new Configuration();
    serverConfiguration.setPort(port);
    serverConfiguration.setContext("/ws");
    io = new SocketIOServer(serverConfiguration);
    io.start();
  }
  
  static void stopWebsocketServer() {
    io.stop();
  }
  
  FakeServer fakeServer;
  MetaApiWebsocketClient websocketClient;
  MetaApiConnection connection;
  MetaApi api;
  boolean subscribeCalled;
  int synchronizeCounter;
  int subscribeCounter;
  
  @BeforeEach
  void setUp() throws Exception {
    port++;
    startWebsocketServer();
    api = new MetaApi("token", new MetaApi.Options() {{
      application = "application";
      domain = "project-stock.agiliumlabs.cloud";
      requestTimeout = 10;
      retryOpts = new RetryOptions() {{
        retries = 3;
        minDelayInSeconds = 1;
        maxDelayInSeconds = 5;
        subscribeCooldownInSeconds = 6;
      }};
    }});
    MetatraderAccountClient accountClient = Mockito.spy((MetatraderAccountClient) FieldUtils.readField(
      api.getMetatraderAccountApi(), "metatraderAccountClient", true));
    Mockito.doAnswer(new Answer<CompletableFuture<MetatraderAccountDto>>() {
      @Override
      public CompletableFuture<MetatraderAccountDto> answer(InvocationOnMock invocation) throws Throwable {
        return CompletableFuture.completedFuture(new MetatraderAccountDto() {{
          _id = invocation.getArgument(0);
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
        }});
      }
    }).when(accountClient).getAccount(Mockito.any());
    FieldUtils.writeField(api.getMetatraderAccountApi(), "metatraderAccountClient", accountClient, true);
    websocketClient = (MetaApiWebsocketClient) FieldUtils.readField(api, "metaApiWebsocketClient", true);
    websocketClient.setUrl("http://localhost:" + port);
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 7500, true);
    fakeServer = new FakeServer();
    fakeServer.start();
  }
  
  @AfterEach
  void tearDown() throws IllegalAccessException, InterruptedException {
    fakeServer.disableStatusTask();
    Socket.removeRequestListeners();
    fakeServer.connectListener = null;
    SubscriptionManager subscriptionManager = (SubscriptionManager) FieldUtils.readField(
      websocketClient, "subscriptionManager", true);
    subscriptionManager.cancelAccount("accountId");
    websocketClient.close();
    Socket.sockets.values().forEach(socket -> socket.socket.disconnect());
    io.removeAllListeners("connect");
    io.removeAllListeners("request");
    stopWebsocketServer();
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
    server.socket.disconnect();
    Thread.sleep(200);
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
  }

  @Test
  void testSetsStateToDisconnectedOnTimeout() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 10; }}).join();
    fakeServer.deleteStatusTask("accountId");
    fakeServer.connectListener = (connected) -> {
      connected.socket.disconnect();
    };
    server.socket.disconnect();
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
    fakeServer.deleteStatusTask("accountId");
    Thread.sleep(8500);
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  }

  @Test
  void testSynchronizesIfSubscribeResponseArrivesAfterSynchronization() {
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            Thread.sleep(200);
            fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
              fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
            fakeServer.authenticate(socket, data).join();
            Thread.sleep(400);
            fakeServer.respond(socket, data).join();
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          }
        } catch (Exception err) {
          err.printStackTrace();
        }
      };
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
    fakeServer.deleteStatusTask("accountId");
    fakeServer.disableSync();
    ObjectNode disconnectPacket = jsonMapper.createObjectNode();
    disconnectPacket.put("type", "disconnected");
    disconnectPacket.put("accountId", "accountId");
    disconnectPacket.put("host", "ps-mpa-0");
    disconnectPacket.put("instanceIndex", 0);
    server.socket.sendEvent("synchronization", disconnectPacket.toString());
    Thread.sleep(2500);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(25000);
    fakeServer.enableSync(server);
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
    fakeServer.deleteStatusTask("accountId");
    fakeServer.disableSync();
    ObjectNode disconnectPacket = jsonMapper.createObjectNode();
    disconnectPacket.put("type", "disconnected");
    disconnectPacket.put("accountId", "accountId");
    disconnectPacket.put("host", "ps-mpa-0");
    disconnectPacket.put("instanceIndex", 0);
    server.socket.sendEvent("synchronization", disconnectPacket.toString());
    Thread.sleep(2500);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(25000);
    fakeServer.enableSync(server);
    fakeServer.emitStatus(server, "accountId").join();
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
    fakeServer.deleteStatusTask("accountId");
    stopWebsocketServer();
    Thread.sleep(60000);
    startWebsocketServer();
    fakeServer.start();
    Thread.sleep(200);
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
  };
  
  @Test
  void testSynchronizesIfConnectingWhileServerIsRebooting() {
    stopWebsocketServer();
    startWebsocketServer(9000);
    fakeServer.start();
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    Js.setTimeout(() -> {
      stopWebsocketServer();
      Js.sleep(60000);
      startWebsocketServer();
      fakeServer.start();
    }, 3000);
    MetaApiConnection connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{ timeoutInSeconds = 120; }}).join();
    MetatraderAccountInformation response = connection.getAccountInformation().join();
    Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(accountInformation);
    assertTrue(connection.isSynchronized());
    assertTrue(connection.getTerminalState().isConnected());
    assertTrue(connection.getTerminalState().isConnectedToBroker());
  };
  
  @Test
  void testResubscribesOtherAccountsAfterOneOfConnectionsIsClosed() throws InterruptedException {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}});
    Thread.sleep(1000);
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}});
    Thread.sleep(1000);
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    connection3.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}});
    Thread.sleep(1000);
    connection.close().join();
    fakeServer.deleteStatusTask("accountId2");
    fakeServer.deleteStatusTask("accountId3");
    fakeServer.disableSync();
    server.socket.disconnect();
    Thread.sleep(2000);
    Thread.sleep(50);
    fakeServer.enableSync(server);
    server.socket.disconnect();
    Thread.sleep(3000);
    Thread.sleep(50);
    assertFalse(connection.isSynchronized());
    assertTrue(connection2.isSynchronized() && connection2.getTerminalState().isConnected()
      && connection2.getTerminalState().isConnectedToBroker());
    assertTrue(connection2.isSynchronized() && connection2.getTerminalState().isConnected()
      && connection2.getTerminalState().isConnectedToBroker());
  };
  
  @Test
  void testLimitsSubscriptionsDuringPerUser429error() throws InterruptedException {
    Set<String> subscribedAccounts = new HashSet<>();
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String type = data.get("type").asText();
          if (data.get("type").asText().equals("subscribe")) {
            if (subscribedAccounts.size() < 2) {
              subscribedAccounts.add(data.get("accountId").asText());
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            } else {
              fakeServer.emitError(socket, data, 1, 2).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          } else if (type.equals("unsubscribe")) {
            subscribedAccounts.remove(data.get("accountId").asText());
            fakeServer.respondAccountInformation(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    try {
      connection3.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
      throw new CompletionException(new Exception("TimeoutException expected"));
    } catch (Throwable err) {
      assertTrue(err.getCause() instanceof TimeoutException);
    }
    connection2.close().join();
    Thread.sleep(2000);
    assertTrue(connection3.isSynchronized());
  }
  
  @Test
  void testWaitsForRetryTimeAfterPerUser429error() throws InterruptedException {
    requestTimestamp = 0;
    Set<String> subscribedAccounts = new HashSet<>();
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            if (subscribedAccounts.size() < 2 || 
              (requestTimestamp != 0 && Date.from(Instant.now()).getTime() - 2 * 1000 > requestTimestamp)) {
              subscribedAccounts.add(data.get("accountId").asText());
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            } else {
              requestTimestamp = Date.from(Instant.now()).getTime();
              fakeServer.emitError(socket, data, 1, 3).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          } else if (type.equals("unsubscribe")) {
            subscribedAccounts.remove(data.get("accountId").asText());
            fakeServer.respond(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    try {
      connection3.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
      throw new CompletionException(new Exception("TimeoutException expected"));
    } catch (Throwable err) {
      assertTrue(err.getCause() instanceof TimeoutException);
    }
    Thread.sleep(2000);
    assertFalse(connection3.isSynchronized());
    Thread.sleep(2500);
    Thread.sleep(200);
    assertTrue(connection3.isSynchronized());
  }
  
  @Test
  void testWaitsForRetryTimeAfterPerServer429Error() throws InterruptedException {
    requestTimestamp = 0;
    Map<String, String> sidByAccounts = new HashMap<>();

    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String sid = socket.socket.getSessionId().toString();
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            if (sidByAccounts.values().stream().filter(accountSID -> accountSID.equals(sid)).collect(Collectors.toList()).size() >= 2 && 
              (requestTimestamp == 0 || Date.from(Instant.now()).getTime() - 5 * 1000 < requestTimestamp)) {
              requestTimestamp = Date.from(Instant.now()).getTime();
              fakeServer.emitError(socket, data, 2, 5).join();
            } else {
              sidByAccounts.put(data.get("accountId").asText(), sid);
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    connection3.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 5;}});
    Thread.sleep(5000);
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId2"));
    assertNotEquals(sidByAccounts.get("accountId2"), sidByAccounts.get("accountId3"));
    Thread.sleep(5000);
    MetatraderAccount account4 = api.getMetatraderAccountApi().getAccount("accountId4").join();
    MetaApiConnection connection4 = account4.connect().join();
    connection4.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId4"));
  };
  
  @Test
  void testReconnectsAfterPerServer429ErrorIfConnectionHasNoSubscribedAccounts() {
    List<String> sids = new ArrayList<>();
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String sid = socket.socket.getSessionId().toString();
          String type = data.get("type").asText();
          if(type.equals("subscribe")) {
            sids.add(sid);
            if (sids.size() == 1) {
              fakeServer.emitError(socket, data, 2, 2).join();
            } else {
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
        
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 5;}}).join();
    assertNotEquals(sids.get(0), sids.get(1));
  };
  
  @Test
  void testFreesASubscribeSlotOnUnsubscribeAfterPerServer429Error() throws InterruptedException {
    Map<String, String> sidByAccounts = new HashMap<>();
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String sid = socket.socket.getSessionId().toString();
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            if (sidByAccounts.values().stream().filter(accountSID -> accountSID.equals(sid)).collect(Collectors.toList()).size() >= 2) {
              fakeServer.emitError(socket, data, 2, 200).join();
            } else {
              sidByAccounts.put(data.get("accountId").asText(), sid);
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          } else if (type.equals("unsubscribe")) {
            sidByAccounts.remove(data.get("accountId").asText());
            fakeServer.respond(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    connection3.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 5;}});
    Thread.sleep(5000);
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId2"));
    assertNotEquals(sidByAccounts.get("accountI2"), sidByAccounts.get("accountId3"));
    connection2.close().join();
    MetatraderAccount account4 = api.getMetatraderAccountApi().getAccount("accountId4").join();
    MetaApiConnection connection4 = account4.connect().join();
    connection4.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join();
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId4"));
  }
  
  @Test
  void testWaitsForRetryTimeAfterPerServerPerUser429error() throws InterruptedException {
    requestTimestamp = 0;
    Map<String, String> sidByAccounts = new HashMap<>();
    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        try {
          String sid = socket.socket.getSessionId().toString();
          String type = data.get("type").asText();
          if (type.equals("subscribe")) {
            if (sidByAccounts.values().stream().filter(accountSID -> accountSID.equals(sid)).toArray().length >= 2 && 
              (requestTimestamp == 0 || Date.from(Instant.now()).getTime() - 2 * 1000 < requestTimestamp)) {
              requestTimestamp = Date.from(Instant.now()).getTime();
              fakeServer.emitError(socket, data, 0, 2).join();
            } else {
              sidByAccounts.put(data.get("accountId").asText(), sid);
              Thread.sleep(200);
              fakeServer.respond(socket, data).join();
              fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
                fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
              Thread.sleep(50);
              fakeServer.authenticate(socket, data).join();
            }
          } else if (type.equals("synchronize")) {
            fakeServer.respond(socket, data).join();
            fakeServer.syncAccount(socket, data).join();
          } else if (type.equals("waitSynchronized")) {
            fakeServer.respond(socket, data).join();
          } else if (type.equals("getAccountInformation")) {
            fakeServer.respondAccountInformation(socket, data).join();
          } else if (type.equals("unsubscribe")) {
            sidByAccounts.remove(data.get("accountId").asText());
            fakeServer.respond(socket, data).join();
          }
        } catch (Throwable err) {
          err.printStackTrace();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions () {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    connection2.waitSynchronized(new SynchronizationOptions () {{timeoutInSeconds = 3;}}).join();
    MetatraderAccount account3 = api.getMetatraderAccountApi().getAccount("accountId3").join();
    MetaApiConnection connection3 = account3.connect().join();
    connection3.waitSynchronized(new SynchronizationOptions () {{timeoutInSeconds = 5;}});
    Thread.sleep(5000);
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId2"));
    assertNotEquals(sidByAccounts.get("accountId2"), sidByAccounts.get("accountId3"));
    MetatraderAccount account4 = api.getMetatraderAccountApi().getAccount("accountId4").join();
    MetaApiConnection connection4 = account4.connect().join();
    connection4.waitSynchronized(new SynchronizationOptions () {{timeoutInSeconds = 3;}}).join();
    assertNotEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId4"));
    connection2.close().join();
    MetatraderAccount account5 = api.getMetatraderAccountApi().getAccount("accountId5").join();
    MetaApiConnection connection5 = account5.connect().join();
    connection5.waitSynchronized(new SynchronizationOptions () {{timeoutInSeconds = 3;}}).join();
    assertEquals(sidByAccounts.get("accountId"), sidByAccounts.get("accountId5"));
  }
  
  @Test
  void testAttemptsToResubscribeOnDisconnectedPacket() throws Exception {
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join(); 
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    fakeServer.deleteStatusTask("accountId");
    server.socket.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-0", "instanceIndex", 0)));
    Thread.sleep(50);
    Thread.sleep(200);
    Thread.sleep(50);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    Thread.sleep(5000);
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
  }
  
  @Test
  void testHandlesMultipleStreamsInOneInstanceNumber() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 7500, true);
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    subscribeCalled = false;

    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = (data) -> {
        String type = data.get("type").asText();
        if (type.equals("subscribe")) {
          subscribeCalled = true;
        } else if (type.equals("synchronize")) {
          fakeServer.respond(socket, data).join();
          fakeServer.syncAccount(socket, data, "ps-mpa-1").join();
        } else if (type.equals("waitSynchronized")) {
          fakeServer.respond(socket, data).join();
        } else if (type.equals("getAccountInformation")) {
          fakeServer.respondAccountInformation(socket, data).join();
        }
      };
    };
    fakeServer.enableSync(server);
    Timer statusTask = Js.setInterval(() -> fakeServer.emitStatus(server, "accountId", "ps-mpa-1"), 100);
    fakeServer.authenticate(server, jsonMapper.valueToTree(Js.asMap("accountId", "accountId")), "ps-mpa-1").join();
    Thread.sleep(50);
    Thread.sleep(2500);
    fakeServer.deleteStatusTask("accountId");
    server.socket.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-0", "instanceIndex", 0)));
    Thread.sleep(50);
    Thread.sleep(1250);
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    assertFalse(subscribeCalled);
    server.socket.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-1", "instanceIndex", 0)));
    statusTask.cancel();
    Thread.sleep(50);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
  };
  
  @Test
  void testDoesNotResubscribeIfMultipleStreamsAndOneTimedOut() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 7500, true);
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    subscribeCalled = false;

    fakeServer.enableSyncMethod = (socket) -> {
      socket.requestListener = data -> {
        String type = data.get("type").asText();
        if (type.equals("subscribe")) {
          subscribeCalled = true;
        } else if (type.equals("synchronize")) {
          fakeServer.respond(socket, data).join();
          fakeServer.syncAccount(socket, data, "ps-mpa-1").join();
        } else if (type.equals("waitSynchronized")) {
          fakeServer.respond(socket, data).join();
        } else if (type.equals("getAccountInformation")) {
          fakeServer.respondAccountInformation(socket, data).join();
        }
      };
    };
    fakeServer.enableSync(server);
    Timer statusTask = Js.setInterval(() -> fakeServer.emitStatus(server, "accountId", "ps-mpa-1"), 120);
    fakeServer.authenticate(server, jsonMapper.valueToTree(Js.asMap("accountId", "accountId")), "ps-mpa-1").join();
    Thread.sleep(50);
    Thread.sleep(2500);
    fakeServer.deleteStatusTask("accountId");
    Thread.sleep(6875);
    Thread.sleep(50);
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    assertFalse(subscribeCalled);
    statusTask.cancel();
    Thread.sleep(50);
    Thread.sleep(8125);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
    assertTrue(subscribeCalled);
  }
  
  @Test
  void testDoesNotSynchronizeIfConnectionIsClosed() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 60000, true);
    synchronizeCounter = 0;
    fakeServer.enableSyncMethod = socket -> {
      socket.requestListener = data -> {
        String type = data.get("type").asText();
        if (type.equals("subscribe")) {
          Js.sleep(200);
          fakeServer.respond(socket, data).join();
          fakeServer.statusTasks.put(data.get("accountId").asText(), Js.setInterval(() ->
            fakeServer.emitStatus(socket, data.get("accountId").asText()), 100));
          fakeServer.authenticate(socket, data).join();
        } else if (type.equals("synchronize")) {
          synchronizeCounter++;
          fakeServer.respond(socket, data).join();
          fakeServer.syncAccount(socket, data, "ps-mpa-1").join();
        } else if (type.equals("waitSynchronized")) {
          fakeServer.respond(socket, data).join();
        } else if (type.equals("getAccountInformation")) {
          fakeServer.respondAccountInformation(socket, data).join();
        } else if (type.equals("unsubscribe")) {
          fakeServer.deleteStatusTask(data.get("accountId").asText());
          fakeServer.respond(socket, data).join();
        }
      };
    };
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    assertEquals(1, synchronizeCounter);
    MetatraderAccount account2 = api.getMetatraderAccountApi().getAccount("accountId2").join();
    MetaApiConnection connection2 = account2.connect().join();
    Thread.sleep(50);
    connection2.close().join();
    try {
      connection2.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 3;}}).join(); 
      throw new CompletionException(new Exception("TimeoutException expected"));
    } catch (Throwable err) {
      assertTrue(err.getCause() instanceof TimeoutException);
    }
    assertEquals(1, synchronizeCounter);
  }
  
  @Test
  void testDoesNotResubscribeAfterConnectionIsClosed() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 3750, true);
    subscribeCounter = 0;

    fakeServer.enableSyncMethod = socket -> {
      socket.requestListener = data -> {
        String type = data.get("type").asText();
        String accountId = data.get("accountId").asText();
        if (type.equals("subscribe")) {
          subscribeCounter++;
          Js.sleep(100);
          fakeServer.respond(socket, data).join();
          fakeServer.deleteStatusTask(accountId);
          fakeServer.statusTasks.put(accountId, Js.setInterval(() -> fakeServer.emitStatus(socket, accountId), 100));
          fakeServer.authenticate(socket, data).join();
        } else if (type.equals("synchronize")) {
          fakeServer.respond(socket, data).join();
          fakeServer.syncAccount(socket, data).join();
        } else if (type.equals("waitSynchronized")) {
          fakeServer.respond(socket, data).join();
        } else if (type.equals("getAccountInformation")) {
          fakeServer.respondAccountInformation(socket, data).join();
        } else if (type.equals("unsubscribe")) {
          fakeServer.deleteStatusTask(accountId);
          fakeServer.respond(socket, data).join();
        }
      };
    };

    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    assertEquals(1, subscribeCounter);
    server.socket.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-0", "instanceIndex", 0)));
    Thread.sleep(300);
    Thread.sleep(6250);
    Thread.sleep(200);
    assertTrue(subscribeCounter > 1);
    int previousSubscribeCounter = subscribeCounter;
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    server.socket.sendEvent("synchronization", jsonMapper.writeValueAsString(Js.asMap("type", "disconnected",
      "accountId", "accountId", "host", "ps-mpa-0", "instanceIndex", 0)));
    connection.close().join();
    Thread.sleep(1000);
    Thread.sleep(6250);
    Thread.sleep(1000);
    assertEquals(previousSubscribeCounter, subscribeCounter);
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());
  }
  
  @Test
  void testDoesNotResubscribeOnTimeoutIfConnectionIsClosed() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 3750, true);
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    fakeServer.deleteStatusTask("accountId");
    assertTrue(connection.isSynchronized());
    connection.close().join();
    Thread.sleep(100);
    Thread.sleep(3875);
    assertFalse(connection.isSynchronized());
  }
  
  @Test
  void testDoesNotSendMultipleSubscribeRequestsIfStatusArrivesFasterThanSubscribe() throws Exception {
    FieldUtils.writeField(websocketClient, "resetDisconnectTimerTimeout", 3750, true);
    subscribeCounter = 0;
    MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").join();
    connection = account.connect().join();
    connection.waitSynchronized(new SynchronizationOptions() {{timeoutInSeconds = 10;}}).join(); 
    fakeServer.disableSync();
    fakeServer.deleteStatusTask("accountId");
    Thread.sleep(100);
    Thread.sleep(7500); 
    assertFalse(connection.isSynchronized());
    assertFalse(connection.getTerminalState().isConnected());
    assertFalse(connection.getTerminalState().isConnectedToBroker());

    server.requestListener = data -> {
      String type = data.get("type").asText();
      String accountId = data.get("accountId").asText();
      if(type.equals("subscribe")) {
        subscribeCounter++;
        Js.sleep(2800);
        fakeServer.respond(server, data).join();
        fakeServer.deleteStatusTask(accountId);
        fakeServer.statusTasks.put("accountId", Js.setInterval(() -> fakeServer.emitStatus(server, accountId), 1000));
        fakeServer.authenticate(server, data).join();
      } else if (type.equals("synchronize")) {
        fakeServer.respond(server, data).join();
        fakeServer.syncAccount(server, data).join();
      } else if (type.equals("waitSynchronized")) {
        fakeServer.respond(server, data).join();
      } else if (type.equals("getAccountInformation")) {
        fakeServer.respondAccountInformation(server, data).join();
      } else if (type.equals("unsubscribe")) {
        fakeServer.deleteStatusTask(accountId);
        fakeServer.respond(server, data).join();
      }
    };
    fakeServer.statusTasks.put("accountId", Js.setInterval(() -> fakeServer.emitStatus(server, "accountId"), 1000));
    Thread.sleep(100);
    Thread.sleep(12500);
    Thread.sleep(100); 
    assertTrue(connection.isSynchronized() && connection.getTerminalState().isConnected()
      && connection.getTerminalState().isConnectedToBroker());
    assertEquals(1, subscribeCounter);
  }
}
