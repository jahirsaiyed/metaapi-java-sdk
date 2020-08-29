# metaapi.cloud SDK for Java

MetaApi is a powerful forex trading API for MetaTrader 4 and MetaTrader 5 terminals.

MetaApi is available in cloud and self-hosted options.

Official REST and websocket API documentation: [https://metaapi.cloud/docs/client/](https://metaapi.cloud/docs/client/)

Please note that this SDK provides an abstraction over REST and websocket API to simplify your application logic.

For more information about SDK APIs please check esdoc documentation in source codes located inside lib folder of this npm package.

## Connecting to MetaApi
Please use [https://app.metaapi.cloud/token](https://app.metaapi.cloud/token) web UI to obtain your API token and supply it to the MetaApi class constructor.

```java
import cloud.metaapi.sdk.MetaApi;

String token = "...";
MetaApi api = new MetaApi(token);
```

## Managing MetaTrader accounts (API servers for MT accounts)
Before you can use the API you have to add an MT account to MetaApi and start an API server for it.

However, before you can create an account, you have to create a provisioning profile.

### Managing provisioning profiles via web UI
You can manage provisioning profiles here: [https://app.metaapi.cloud/provisioning-profiles](https://app.metaapi.cloud/provisioning-profiles)

### Creating a provisioning profile via API
```java
// if you do not have created a provisioning profile for your broker,
// you should do it before creating an account
ProvisioningProfile provisioningProfile = api.getProvisioningProfileApi()
    .createProvisioningProfile(new NewProvisioningProfileDto() {{
        name = "My profile";
        version = 5;
    }}).get();
// servers.dat file is required for MT5 profile and can be found inside
// config directory of your MetaTrader terminal data folder. It contains
// information about available broker servers
provisioningProfile.uploadFile("servers.dat", "/path/to/servers.dat").get();
// for MT4, you should upload an .srv file instead
provisioningProfile.uploadFile("broker.srv", "/path/to/broker.srv").get();
```

### Retrieving existing provisioning profiles via API
```java
List<ProvisioningProfile> provisioningProfiles = api.getProvisioningProfileApi()
    .getProvisioningProfiles(Optional.empty(), Optional.empty()).get();
ProvisioningProfile provisioningProfile = api.getProvisioningProfileApi().getProvisioningProfile("profileId").get();
```

### Updating a provisioning profile via API
```java
provisioningProfile.update(new ProvisioningProfileUpdateDto() {{ name = "New name" }}).get();
// for MT5, you should upload a servers.dat file
provisioningProfile.uploadFile("servers.dat", "/path/to/servers.dat").get();
// for MT4, you should upload an .srv file instead
provisioningProfile.uploadFile("broker.srv", "/path/to/broker.srv").get();
```

### Removing a provisioning profile
```java
provisioningProfile.remove().get();
```

### Managing MetaTrader accounts (API servers) via web UI
You can manage MetaTrader accounts here: [https://app.metaapi.cloud/accounts](https://app.metaapi.cloud/accounts)

### Create a MetaTrader account (API server) via API
```java
MetatraderAccount account = api.getMetatraderAccountApi().createAccount(new NewMetatraderAccountDto() {
  name = "Trading account #1";
  type = "cloud";
  login = "1234567";
  // password can be investor password for read-only access
  password = "qwerty";
  server = "ICMarketsSC-Demo";
  // synchronizationMode can be "automatic" for RPC access or "user" if you
  // want to keep track of terminal state in real-time (e.g. if you are
  // developing a EA or trading strategy)
  synchronizationMode = "automatic";
  provisioningProfileId = provisioningProfile.getId();
  //algorithm used to parse your broker timezone. Supported values are
  // icmarkets for America/New_York DST switch and roboforex for EET
  // DST switch (the values will be changed soon)
  timeConverter = "roboforex";
  application = "MetaApi";
  magic = 123456;
});
```

### Retrieving existing accounts via API
```java
// specifying provisioning profile id is optional
String provisioningProfileId = "...";
List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts(Optional.of(provisioningProfileId)).get();
MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").get();
```

### Updating an existing account via API
```java
account.update(new MetatraderAccountUpdateDto() {
  name = "Trading account #1";
  login = "1234567";
  // password can be investor password for read-only access
  password = "qwerty";
  server = "ICMarketsSC-Demo";
  // synchronizationMode can be "automatic" for RPC access or "user" if you
  // want to keep track of terminal state in real-time (e.g. if you are
  // developing a EA or trading strategy)
  synchronizationMode = "automatic";
}).get();
```

### Removing an account
```java
account.remove().get();
```

### Deploying, undeploying and redeploying an account (API server) via API
```java
account.deploy().get();
account.undeploy().get();
account.redeploy().get();
```

## Access MetaTrader account via RPC API
RPC API let you query the trading terminal state. You should use
RPC API if you develop trading monitoring apps like myfxbook or other
simple trading apps.

You should create your account with automatic synchronization mode if
all you need is RPC API.

### Query account information, positions, orders and history via RPC API
```java
MetaApiConnection connection = account.connect().get();

connection.waitSynchronized().get();

// retrieve balance and equity
System.out.println(connection.getAccountInformation().get());
// retrieve open positions
System.out.println(connection.getPositions().get());
// retrieve a position by id
System.out.println(connection.getPosition("1234567").get());
// retrieve pending orders
System.out.println(connection.getOrders().get());
// retrieve a pending order by id
System.out.println(connection.getOrder("1234567").get());
// retrieve history orders by ticket
System.out.println(connection.getHistoryOrdersByTicket("1234567").get());
// retrieve history orders by position id
System.out.println(connection.getHistoryOrdersByPosition("1234567").get());
// retrieve history orders by time range
System.out.println(connection.getHistoryOrdersByTimeRange(startTime, endTime).get());
// retrieve history deals by ticket
System.out.println(connection.getDealsByTicket("1234567").get());
// retrieve history deals by position id
System.out.println(connection.getDealsByPosition("1234567").get());
// retrieve history deals by time range
System.out.println(connection.getDealsByTimeRange(startTime, endTime).get());
```

### Query contract specifications and quotes via RPC API
```java
MetaApiConnection connection = account.connect().get();

connection.waitSynchronized().get();

// first, subscribe to market data
connection.subscribeToMarketData("GBPUSD").get();

// read constract specification
System.out.println(connection.getSymbolSpecification("GBPUSD").get());
// read current price
System.out.println(connection.getSymbolPrice("GBPUSD").get());
```

### Use real-time streaming API
Real-time streaming API is good for developing trading applications like trade copiers or automated trading strategies.
The API synchronizes the terminal state locally so that you can query local copy of the terminal state really fast.

In order to use this API you need to create an account with `user` synchronization mode.

#### Synchronizing and reading teminal state
```java
MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").get();

// account.getSynchronizationMode() must be equal to "user" at this point

// access local copy of terminal state
Optional<TerminalState> terminalState = connection.getTerminalState();

// wait until synchronization completed
connection.waitSynchronized().get();

System.out.println(terminalState.isConnected());
System.out.println(terminalState.isConnectedToBroker());
System.out.println(terminalState.getAccountInformation());
System.out.println(terminalState.getPositions());
System.out.println(terminalState.getOrders());
// symbol specifications
System.out.println(terminalState.getSpecifications());
System.out.println(terminalState.getSpecification("EURUSD"));
System.out.println(terminalState.getPrice("EURUSD"));

// access history storage
Optional<HistoryStorage> historyStorage = connection.getHistoryStorage();

// both orderSynchronizationFinished and dealSynchronizationFinished
// should be true once history synchronization have finished
System.out.println(historyStorage.get().isOrderSynchronizationFinished());
System.out.println(historyStorage.get().isDealSynchronizationFinished());
```

#### Overriding local history storage
By default history is stored in memory only. You can override history storage to save trade history to a persistent storage like MongoDB database.
```java
import cloud.metaapi.sdk.HistoryStorage;

class MongodbHistoryStorage extends HistoryStorage {
  // implement the abstract methods, see MemoryHistoryStorage for sample
  // implementation
}

HistoryStorage historyStorage = new MongodbHistoryStorage();

// Note: if you will not specify history storage, then in-memory storage
// will be used (instance of MemoryHistoryStorage)
MetaApiConnection connection = account.connect(Optional.of(historyStorage)).get();

// access history storage
Optional<HistoryStorage> historyStorage = connection.getHistoryStorage();

// invoke other methods provided by your history storage implementation
System.out.println(((MongodbHistoryStorage) historyStorage).yourMethod().get());
```

#### Receiving synchronization events
You can override SynchronizationListener in order to receive synchronization event notifications, such as account/position/order/history updates or symbol quote updates.
```java
import cloud.metaapi.sdk.clients.SynchronizationListener;

// receive synchronization event notifications
// first, implement your listener
class MySynchronizationListener extends SynchronizationListener {
  // override abstract methods you want to receive notifications for
}

// now add the listener
SynchronizationListener listener = new MySynchronizationListener();
connection.addSynchronizationListener(listener);

// remove the listener when no longer needed
connection.removeSynchronizationListener(listener);
```

### Execute trades (both RPC and streaming APIs)
```java
MetaApiConnection connection = account.connect().get();

connection.waitSynchronized().get();

// trade
System.out.println(connection.createMarketBuyOrder("GBPUSD", 0.07, 0.9, 2.0, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.createMarketSellOrder("GBPUSD", 0.07, 2.0, 0.9, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.createLimitBuyOrder("GBPUSD", 0.07, 1.0, 0.9, 2.0, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.createLimitSellOrder("GBPUSD", 0.07, 1.5, 2.0, 0.9, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.createStopBuyOrder("GBPUSD", 0.07, 1.5, 0.9, 2.0, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.createStopSellOrder("GBPUSD", 0.07, 1.0, 2.0, 0.9, "comment", "TE_GBPUSD_7hyINWqAlE").get());
System.out.println(connection.modifyPosition("46870472", 2.0, 0.9).get());
System.out.println(connection.closePositionPartially("46870472", 0.9).get());
System.out.println(connection.closePosition("46870472").get());
System.out.println(connection.closePositionBySymbol("EURUSD").get());
System.out.println(connection.modifyOrder("46870472", 1.0, 2.0, 0.9).get());
System.out.println(connection.cancelOrder("46870472").get());

// Note: trade methods does not throw an exception if terminal have refused
// the trade, thus you must check the returned value
const result = connection.createMarketBuyOrder("GBPUSD", 0.07, 0.9, 2.0, "comment", "TE_GBPUSD_7hyINWqAlE");
if (result.description !== "TRADE_RETCODE_DONE") {
  console.error("Trade was rejected by MetaTrader terminal with " + result.description + " error");
}
```

Keywords: MetaTrader API, MetaTrader REST API, MetaTrader websocket API,
MetaTrader 5 API, MetaTrader 5 REST API, MetaTrader 5 websocket API,
MetaTrader 4 API, MetaTrader 4 REST API, MetaTrader 4 websocket API,
MT5 API, MT5 REST API, MT5 websocket API, MT4 API, MT4 REST API,
MT4 websocket API, MetaTrader SDK, MetaTrader SDK, MT4 SDK, MT5 SDK,
MetaTrader 5 SDK, MetaTrader 4 SDK, MetaTrader Java SDK, MetaTrader 5
Java SDK, MetaTrader 4 Java SDK, MT5 Java SDK, MT4 Java SDK,
FX REST API, Forex REST API, Forex websocket API, FX websocket API, FX
SDK, Forex SDK, FX Java SDK, Forex Java SDK, Trading API, Forex
API, FX API, Trading SDK, Trading REST API, Trading websocket API,
Trading SDK, Trading Java SDK