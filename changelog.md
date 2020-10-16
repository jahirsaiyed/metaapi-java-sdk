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