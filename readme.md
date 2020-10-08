# metaapi.cloud SDK for Java

MetaApi is a powerful, fast, cost-efficient, easy to use and standards-driven cloud forex trading API for MetaTrader 4 and MetaTrader 5 platform designed for traders, investors and forex application developers to boost forex application development process. MetaApi can be used with any broker and does not require you to be a brokerage.

CopyFactory is a simple yet powerful copy-trading API which is a part of MetaApi. See below for CopyFactory readme section.

MetaApi is a paid service, but API access to one MetaTrader account is free of charge.

The [MetaApi pricing](https://metaapi.cloud/#pricing) was developed with the intent to make your charges less or equal to what you would have to pay for hosting your own infrastructure. This is possible because over time we managed to heavily optimize our MetaTrader infrastructure. And with MetaApi you can save significantly on application development and maintenance costs and time thanks to high-quality API, open-source SDKs and convenience of a cloud service.

Official REST and websocket API documentation: [https://metaapi.cloud/docs/client/](https://metaapi.cloud/docs/client/)

Please note that this SDK provides an abstraction over REST and websocket API to simplify your application logic.

For more information about SDK APIs please check esdoc documentation in source codes located inside lib folder of this npm package.

## Installation
If you use Apache Maven, add this to `<dependencies>` in your `pom.xml`:
```xml
<dependency>
  <groupId>cloud.metaapi.sdk</groupId>
  <artifactId>metaapi-java-sdk</artifactId>
  <version>8.0.0</version>
</dependency>
```

Other options can be found on [this page](https://search.maven.org/artifact/cloud.metaapi.sdk/metaapi-java-sdk/8.0.0/jar).

## Working code examples
Please check [this short video](https://youtu.be/dDOUWBjdfA4) to see how you can download samples via our web application.

You can also find code examples at [examples folder of our github repo](https://github.com/agiliumtrade-ai/metaapi-java-client/tree/master/examples) or in the examples folder of the project.

We have composed a [short guide explaining how to use the example code](https://metaapi.cloud/docs/client/usingCodeExamples)

## Connecting to MetaApi
Please use one of these ways: 
1. [https://app.metaapi.cloud/token](https://app.metaapi.cloud/token) web UI to obtain your API token.
2. An account access token which grants access to a single account. See section below on instructions on how to retrieve account access token.

Supply token to the MetaApi class constructor.

```java
import cloud.metaapi.sdk.metaApi.MetaApi;

String token = "...";
MetaApi api = new MetaApi(token);
```

## Retrieving account access token
Account access token grants access to a single account. You can retrieve account access token via API:
```java
String accountId = "...";
MetatraderAccount account = api.getMetatraderAccountApi().getAccount(accountId).get();
String accountAccessToken = account.getAccessToken();
System.out.println(accountAccessToken);
```

Alternatively, you can retrieve account access token via web UI on https://app.metaapi.cloud/accounts page (see [this video](https://youtu.be/PKYiDns6_xI)).

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
        // TODO: description
        brokerTimezone = "EET";
        // TODO: description
        brokerDSTTimezone = "EET";
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
    .getProvisioningProfiles(null, null).get();
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
  provisioningProfileId = provisioningProfile.getId();
  application = "MetaApi";
  magic = 123456;
  quoteStreamingIntervalInSeconds = 2.5; // set to 0 to receive quote per tick
});
```

### Retrieving existing accounts via API
```java
// filter and paginate accounts, see docs for full list of filter options available
List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts(new AccountsFilter() {{
  limit = 10;
  offset = 0;
  query = "ICMarketsSC-MT5";
  state = List.of(DeploymentState.DEPLOYED);
}}).get();
// get accounts without filter (returns 1000 accounts max)
List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts(null).get();

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
  quoteStreamingIntervalInSeconds = 2.5; // set to 0 to receive quote per tick
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

#### Synchronizing and reading teminal state
```java
MetatraderAccount account = api.getMetatraderAccountApi().getAccount("accountId").get();

// access local copy of terminal state
TerminalState terminalState = connection.getTerminalState();

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
HistoryStorage historyStorage = connection.getHistoryStorage();

// both orderSynchronizationFinished and dealSynchronizationFinished
// should be true once history synchronization have finished
System.out.println(historyStorage.isOrderSynchronizationFinished());
System.out.println(historyStorage.isDealSynchronizationFinished());
```

#### Overriding local history storage
By default history is stored in memory only. You can override history storage to save trade history to a persistent storage like MongoDB database.
```java
import cloud.metaapi.sdk.metaApi.HistoryStorage;

class MongodbHistoryStorage extends HistoryStorage {
  // implement the abstract methods, see MemoryHistoryStorage for sample
  // implementation
}

HistoryStorage historyStorage = new MongodbHistoryStorage();

// Note: if you will not specify history storage, then in-memory storage
// will be used (instance of MemoryHistoryStorage)
MetaApiConnection connection = account.connect(historyStorage).get();

// access history storage
HistoryStorage historyStorage = connection.getHistoryStorage();

// invoke other methods provided by your history storage implementation
System.out.println(((MongodbHistoryStorage) historyStorage).yourMethod().get());
```

#### Receiving synchronization events
You can override SynchronizationListener in order to receive synchronization event notifications, such as account/position/order/history updates or symbol quote updates.
```java
import cloud.metaapi.sdk.clients.metaApi.SynchronizationListener;

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

### Retrieve contract specifications and quotes via streaming API
```java
MetaApiConnection connection = account.connect().get();
connection.waitSynchronized().get();
// first, subscribe to market data
connection.subscribeToMarketData("GBPUSD").get();
// read constract specification
System.out.println(terminalState.getSpecification("EURUSD"));
// read current price
System.out.println(terminalState.getPrice("EURUSD"));
```

### Execute trades (both RPC and streaming APIs)
```java
MetaApiConnection connection = account.connect().get();

connection.waitSynchronized().get();

// trade
TradeOptions options = new TradeOptions() {{ comment = "comment"; clientId = "TE_GBPUSD_7hyINWqAl"; }};
System.out.println(connection.createMarketBuyOrder("GBPUSD", 0.07, 0.9, 2.0, options).get());
System.out.println(connection.createMarketSellOrder("GBPUSD", 0.07, 2.0, 0.9, options).get());
System.out.println(connection.createLimitBuyOrder("GBPUSD", 0.07, 1.0, 0.9, 2.0, options).get());
System.out.println(connection.createLimitSellOrder("GBPUSD", 0.07, 1.5, 2.0, 0.9, options).get());
System.out.println(connection.createStopBuyOrder("GBPUSD", 0.07, 1.5, 0.9, 2.0, options).get());
System.out.println(connection.createStopSellOrder("GBPUSD", 0.07, 1.0, 2.0, 0.9, options).get());
System.out.println(connection.modifyPosition("46870472", 2.0, 0.9).get());
System.out.println(connection.closePositionPartially("46870472", 0.9, null).get());
System.out.println(connection.closePosition("46870472", null).get());
System.out.println(connection.closePositionsBySymbol("EURUSD", null).get());
System.out.println(connection.modifyOrder("46870472", 1.0, 2.0, 0.9).get());
System.out.println(connection.cancelOrder("46870472").get());

// if you need to, check the extra result information in stringCode and numericCode properties of the response
MetatraderTradeResponse result = connection.createMarketBuyOrder("GBPUSD", 0.07, 0.9, 2.0, options).get();
System.out.println("Trade successful, result code is " + result.stringCode);
```

## CopyFactory copy trading API (experimental)

CopyFactory is a powerful trade copying API which makes developing forex
trade copying applications as easy as writing few lines of code.

At this point this feature is experimental and we have not yet defined a final price for it.

### Why do we offer CopyFactory API

We found that developing reliable and flexible trade copier is a task
which requires lots of effort, because developers have to solve a series
of complex technical tasks to create a product.

We decided to share our product as it allows developers to start with a
powerful solution in almost no time, save on development and
infrastructure maintenance costs.

### CopyFactory features
Features supported:

- low latency trade copying
- connect arbitrary number of strategy providers and subscribers
- subscribe accounts to multiple strategies at once
- select arbitrary copy ratio for each subscription
- apply advanced risk filters on strategy provider side
- override risk filters on subscriber side
- provide multiple strategies from a single account based on magic or symbol filters
- reliable trade copying
- supports manual trading on subscriber accounts while copying trades
- synchronize subscriber account with strategy providers
- monitor trading history
- calculate trade copying commissions for account managers

### Configuring trade copying

In order to configure trade copying you need to:

- add MetaApi MetaTrader accounts with CopyFactory as application field value (see above)
- create CopyFactory master and slave accounts and connect them to MetaApi accounts via connectionId field
- create a strategy being copied
- subscribe slave CopyFactory accounts to the strategy

```java
import cloud.metaapi.sdk.metaApi.MetaApi;
import cloud.metaapi.sdk.copyFactory.CopyFactory;

String token = "...";
MetaApi metaapi = new MetaApi(token);
CopyFactory copyFactory = new CopyFactory(token);

// retrieve MetaApi MetaTrader accounts with CopyFactory as application field value
MetatraderAccount masterMetaapiAccount = api.getMetatraderAccountApi().getAccount("masterMetaapiAccountId").get();
if (!masterMetaapiAccount.getApplication().equals("CopyFactory")) {
  throw new Exception("Please specify CopyFactory application field value in your MetaApi account in order to use it in CopyFactory API");
}
MetatraderAccount slaveMetaapiAccount = api.getMetatraderAccountApi().getAccount("slaveMetaapiAccountId").get();
if (!slaveMetaapiAccount.getApplication().equals("CopyFactory")) {
  throw new Exception("Please specify CopyFactory application field value in your MetaApi account in order to use it in CopyFactory API");
}

// create CopyFactory master and slave accounts and connect them to MetaApi accounts via connectionId field
ConfigurationClient configurationApi = copyFactory.getConfigurationApi();
String masterAccountId = configurationApi.generateAccountId();
String slaveAccountId = configurationApi.generateAccountId();
configurationApi.updateAccount(masterAccountId, new CopyFactoryAccountUpdate() {{
  name = "Demo account";
  connectionId = masterMetaapiAccount.getId();
  subscriptions = List.of();
}}).get();

// create a strategy being copied
StrategyId strategyId = configurationApi.generateStrategyId().get();
configurationApi.updateStrategy(strategyId.id, new CopyFactoryStrategyUpdate() {{
  name = "Test strategy";
  description = "Some useful description about your strategy";
  positionLifecycle = "hedging";
  connectionId = slaveMetaapiAccount.getId();
  maxTradeRisk = 0.1;
  stopOutRisk = new CopyFactoryStrategyStopOutRisk() {{
    value = 0.4;
    startTime = new IsoTime("2020-08-24T00:00:00.000Z");
  }},
  timeSettings = new CopyFactoryStrategyTimeSettings() {{
    lifetimeInHours = 192;
    openingIntervalInMinutes = 5;
  }}
}}).get();

// subscribe slave CopyFactory accounts to the strategy
configurationApi.updateAccount(masterAccountId, new CopyFactoryAccountUpdate() {{
  name = "Demo account";
  connectionId = masterMetaapiAccount.getId();
  subscriptions: List.of(new CopyFactoryStrategySubscription() {{
    strategyId = strategyId;
    multiplier = 1;
  }})
}}).get();
```

See jsdoc in-code documentation for full definition of possible configuration options.

### Retrieving trade copying history

CopyFactory allows you to monitor transactions conducted on trading accounts in real time.

#### Retrieving trading history on provider side
```java
HistoryClient historyApi = copyFactory.getHistoryApi();
// retrieve list of subscribers
System.out.println(historyApi.getSubscribers().get());
// retrieve list of strategies provided
System.out.println(historyApi.getProvidedStrategies().get());
// retrieve trading history, please note that this method support pagination and limits number of records
System.out.println(historyApi.getProvidedStrategiesTransactions(new IsoTime("2020-08-01T00:00:00.000Z"), new IsoTime("2020-09-01T00:00:00.000Z")).get();
```

#### Retrieving trading history on subscriber side
```java
HistoryApi historyApi = copyFactory.getHistoryApi();
// retrieve list of providers
System.out.println(historyApi.getProviders().get());
// retrieve list of strategies subscribed to
System.out.println(historyApi.getStrategiesSubscribed().get());
// retrieve trading history, please note that this method support pagination and limits number of records
System.out.println(historyApi.getStrategiesSubscribedTransactions(new IsoTime("2020-08-01T00:00:00.000Z"), new IsoTime("2020-09-01T00:00:00.000Z")).get();
```

#### Resynchronizing slave accounts to masters
There is a configurable time limit during which the trades can be opened. Sometimes trades can not open in time due to broker errors or trading session time discrepancy.
You can resynchronize a slave account to place such late trades. Please note that positions which were
closed manually on a slave account will also be reopened during resynchronization.

```java
String accountId = "..."; // CopyFactory account id
// resynchronize all strategies
copyFactory.getTradingApi().resynchronize(accountId).get();
// resynchronize specific strategy
copyFactory.tradingApi.resynchronize(accountId, List.of("ABCD")).get();
```

#### Managing stopouts
A subscription to a strategy can be stopped if the strategy have exceeded allowed risk limit.
```java
TradingClient tradingApi = copyFactory.getTradingApi();
String accountId = "..."; // CopyFactory account id
// retrieve list of strategy stopouts
System.out.println(tradingApi.getStopouts(accountId).get());
// reset a stopout so that subscription can continue
tradingApi.resetStopout(accountId, "daily-equity").get();
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