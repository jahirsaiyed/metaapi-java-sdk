14.0.0
  - breaking change: change type of `magic` field to `long`

13.3.3
  - added platform field to new metatrader account dto model

13.3.2
  - added accountType field to MT5 demo account model

13.3.1
  - fixed dependency vulnerabilities

13.3.0
  - added region selection

13.2.3
  - fixed order state update error in terminal state

13.2.2
  - fixed hanging because of parallelism problems

13.2.1
  - fixed dependency vulnerabilities
  - use ConcurrentHashMap instead HashMap in order to increase thread safety 

13.2.0
  - added clientId to query websocket url
  - added equity curve filter to CopyFactory
  - fixed health state tracking for multiple replicas
  - extended synchronization throttler options
  - move CopyFactory trade copying API to a separate npm module
  - increase socket connection stability
  - added API for advanced market data subscriptions
  - added API to increase account reliability
  - added subscription manager to handle account subscription process
  - fixed error on socket reconnect
  - improved handling of too many requests error
  - added getSymbols RPC API
  - added baseCurrency field to the MetaTraderAccount model
  - handle TooManyRequestsError in HTTP client
  - limit max concurrent synchronizations based on the number of subscribed accounts
  - implement proper rounding for position profits and account equity
  - breaking change: refactored specifications updated events
  - fix equity calculation
  - implemented API to retrieve historical market data
  - do not query specification fields until it is received in TerminalState
  - upgraded CopyFactory API to 2.1.1
  - increased default demo account request timeout to 240 seconds
  - swapRollover3Days can take value of NONE for some brokers
  - added MetaStats SDK
  - fixed deal sorting in memory history store
  - improve stability for connection host migration
  - disable synchronization after connection is closed
  - make it possible to specify relative SL/TP
  - fixed synchronization queue
  - breaking change: added sequential packet processing
  - increased health status tracking interval to decrease CPU load
  - added copyFactoryRoles field to MetatraderAccount entity
  - added resourceSlots field to MetatraderAccount model so that user can request extra resource allocation for specific accounts for an extra fee
  - added logging URL on websocket connection
  - fixed initializing websocket connection for multiple accounts
  - remove synchronization listeners on connection close
  - added options validation
  - added waitForPrice method into TerminalState class to make it possible to wait for price to arrive

12.3.1
  - added feature to unsubscribe from market data (remove symbol from market watch)
  - fixed synchronization throttling
  - bugfix for unsubscribeFromMarketData API

12.3.0
  - added retryOpts option to configure retries of certain REST/RPC requests upon failure
  - improve account connection reliability
  - added credit account property
  - removed maximum reliability value

12.1.0
  - added API to retrieve CopyFactory slave trading log
  - fixed race condition when orders are being added and completed fast
  - breaking change: changed signatures of SynchronizationListener methods
  - add reliability field
  - add symbol mapping setting to CopyFactory
  - fix quote health check logic
  - add name and login to account information
  - add a feature to select trade scaling mode in CopyFactory (i.e. if we want the trade size to be preserved or scaled according to balance when copying)

11.0.0
  - breaking change: MetaApi options are now specified via an object
  - breaking change: CopyFactory options are now specified via an object
  - added packet logger
  - added close by order support
  - added stop limit order support
  - bugfix MetatraderAccount.connect method to throw an error to avoid creating broken connections
  - add marginMode, tradeAllowed, investorMode fields to account information
  - breaking change: waitSynchronized to synchronize CopyFactory and RPC applications by default
  - improvements to position profit and account equity tracking on client side
  - real-time updates for margin fields in account information
  - breaking change: uptime now returns uptime measurements over several timeframes (1h, 1d, 1w)
  - do not retry synchronization after MetaApiConnection is closed
  - added option for reverse copying in CopyFactory API
  - added ConnectionHealthMonitor.getServerHealthStatus API to retrieve health status of server-side applications
  - added option to specify account-wide stopout and risk limits in CopyFactory API
  - track MetaApi application latencies
  - send RPC requests via RPC application
  - added extensions for accounts
  - added metadata field for accounts to store extra information together with account
  - increased synchronization stability

10.1.0
  - added support for portfolio strategies (i.e. the strategies which include several other member strategies) to CopyFactory API

10.0.0
  - added incoming commissions to CopyFactory history API
  - breaking change: refactored resetStopout method in CopyFactory trading API. Changed method name, added strategyId parameter.
  - retry synchronization if synchronization attempt have failed
  - restore market data subscriptions on successful synchronization
  - added capability to monitor terminal connection health and measure terminal connection uptime
  - change packet orderer timeout from 10 seconds to 1 minute to accomodate for slower connections

9.1.0
  - added API to register MetaTrader demo accounts
  - fixed packet orderer to do not cause unnecessary resynchronization

9.0.0
  - added contractSize field to MetatraderSymbolSpecification model
  - added quoteSessions and tradeSessions to MetatraderSymbolSpecification model
  - added more fields to MetatraderSymbolSpecification model
  - breaking change: add onPositionsReplaced and onOrderReplaced events into SynchronizationListener and no longer invoke onPositionUpdated and onOrderUpdated during initial synchronization
  - removed excessive log message from subscribe API
  - breaking change: introduced synchronizationStarted event to increase synchronization stability
  - fixed wrong expected sequence number of synchronization packet in the log message
  - added positionId field to CopyFactoryTransaction model

8.0.3
  - fixed failing reconnection attempts after disconnection from MetaApi

8.0.2
  - bugfix packet ordering algorithm

8.0.1
  - bugfix packet ordering algorithm

8.0.0
  - added latency and slippage metrics to CopyFactory trade copying API
  - added CopyFactory configuration client method retrieving active resynchronization tasks
  - improved description of CopyFactory account resynchronizing in readme
  - made it possible to use MetaApi class in interaction tests
  - renamed tradeCopyingSlippageInPercentPoints -> tradeCopyingSlippageInBasisPoints in CopyFactory history API
  - added application setting to MetaApi class to make it possible to launch several MetaApi applications in parallel on the same account
  - breaking change: removed the `timeConverter` field from the account, replaced it with `brokerTimezone` and `brokerDSTSwitchTimezone` fields in the provisioning profile instead
  - added originalComment and clientId fields to MetatraderPosition
  - examples extracted into own maven projects
  - fixed occasional fake synchronization timeouts in waitSynchronized method
  - breaking change: changed API contract of MetaApiConnection.waitSynchronized method
  - added tags for MetaApi accounts
  - minor adjustments to equity calculation algorithm
  - added method to wait for active resynchronization tasks are completed in configuration CopyFactory api
  - added the ability to set the start time for synchronization, used for tests
  - resynchronize on lost synchronization packet to ensure local terminal state consistency

7.2.0
  - Added time fields in broker timezone to objects
  - Added time fields to MetatraderSymbolPrice model
  - Added accountCurrencyExchangeRate fields to models
  - Fixed javadoc errors

7.1.0
  - now only one MetaApiConnection can be created per account at the same time to avoid history storage errors
  - fix simultaneous multiple file writes by one connection
  - сhanges to load balancing algorithm
  - adjust CopyFactory defaults
  - fix minimum compatible Java SDK version

7.0.0
  - Prepared for upcoming breaking change in API: added sticky session support
  - added quoteStreamingIntervalInSeconds field to account to configure quote streaming interval
  - added description field to CopyFactory strategy

6.0.0
  - added CopyFactory trade-copying API
  - added reason field to position, order and deal
  - added fillingMode field to MetaTraderOrder model
  - added order expiration time and type
  - added ability to select filling mode when placing a market order, in trade options
  - added ability to set expiration options when placing a pending order, in trade options
  - initial release, version is set to be in sync with other SDKs