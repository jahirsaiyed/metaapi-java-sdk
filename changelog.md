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