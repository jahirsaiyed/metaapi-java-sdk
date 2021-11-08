package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.NotFoundException;
import cloud.metaapi.sdk.clients.meta_api.HistoricalMarketDataClient;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient.ExpertAdvisorDto;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient.NewExpertAdvisorDto;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountUpdateDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderCandle;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTick;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.ConnectionStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.CopyFactoryRole;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.Extension;
import cloud.metaapi.sdk.util.Async;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Implements a MetaTrader account entity
 */
public class MetatraderAccount {
  
  private MetatraderAccountDto data;
  private MetatraderAccountClient metatraderAccountClient;
  private ConnectionRegistry connectionRegistry;
  private HistoricalMarketDataClient historicalMarketDataClient;
  private ExpertAdvisorClient expertAdvisorClient;
  
  /**
   * Constructs a MetaTrader account entity
   * @param data MetaTrader account data
   * @param metatraderAccountClient MetaTrader account REST API client
   * @param metaApiWebsocketClient MetaApi websocket client
   * @param connectionRegistry metatrader account connection registry
   * @param expertAdvisorClient expert advisor REST API client
   * @param historicalMarketDataClient historical market data REST API client
   */
  public MetatraderAccount(MetatraderAccountDto data, MetatraderAccountClient metatraderAccountClient,
    MetaApiWebsocketClient metaApiWebsocketClient, ConnectionRegistry connectionRegistry,
    ExpertAdvisorClient expertAdvisorClient, HistoricalMarketDataClient historicalMarketDataClient) {
    this.data = data;
    this.metatraderAccountClient = metatraderAccountClient;
    this.connectionRegistry = connectionRegistry;
    this.expertAdvisorClient = expertAdvisorClient;
    this.historicalMarketDataClient = historicalMarketDataClient;
  }
  
  /**
   * Returns account id
   * @return account id
   */
  public String getId() {
    return data._id;
  }
  
  /**
   * Returns account name
   * @return account name
   */
  public String getName() {
    return data.name;
  }
  
  /**
   * Returns account type. Possible values are cloud, cloud-g1, cloud-g2 and self-hosted.
   * @return account type
   */
  public String getType() {
    return data.type;
  }
  
  /**
   * Returns account login
   * @return account login
   */
  public String getLogin() {
    return data.login;
  }
  
  /**
   * Returns MetaTrader server which hosts the account
   * @return MetaTrader server which hosts the account
   */
  public String getServer() {
    return data.server;
  }
  
  /**
   * Returns id of the account's provisioning profile
   * @return id of the account's provisioning profile
   */
  public String getProvisioningProfileId() {
    return data.provisioningProfileId;
  }
  
  /**
   * Returns application name to connect the account to. Currently allowed values are MetaApi and AgiliumTrade
   * @return application name to connect the account to
   */
  public String getApplication() {
    return data.application;
  }
  
  /**
   * Returns MetaTrader magic to place trades using
   * @return MetaTrader magic to place trades using
   */
  public int getMagic() {
    return data.magic;
  }
  
  /**
   * Returns account deployment state
   * @return account deployment state
   */
  public DeploymentState getState() {
    return data.state;
  }
  
  /**
   * Returns terminal and broker connection status
   * @return terminal and broker connection status
   */
  public ConnectionStatus getConnectionStatus() {
    return data.connectionStatus;
  }
  
  /**
   * Returns authorization access token to be used for accessing single account data.
   * Intended to be used in browser API.
   * @return authorization token
   */
  public String getAccessToken() {
    return data.accessToken;
  }
  
  /**
   * Returns flag indicating if trades should be placed as manual trades on this account
   * @return flag indicating if trades should be placed as manual trades on this account
   */
  public boolean isManualTrades() {
    return data.manualTrades;
  }
  
  /**
   * Returns API extensions
   * @return API extensions
   */
  public List<Extension> getExtensions() {
    return data.extensions;
  }
  
  /**
   * Returns extra information which can be stored together with your account
   * @return extra information which can be stored together with your account
   */
  public Map<String, Object> getMetadata() {
    return data.metadata;
  }
  
  /**
   * Returns user-defined account tags
   * @return user-defined account tags
   */
  public List<String> getTags() {
    return data.tags;
  }

  /**
   * Returns account roles for CopyFactory2 application
   * @return account roles for CopyFactory2 application
   */
  public List<CopyFactoryRole> getCopyFactoryRoles() {
    return data.copyFactoryRoles;
  }

  /**
   * Returns number of resource slots to allocate to account. Allocating extra resource slots
   * results in better account performance under load which is useful for some applications. E.g. if you have many
   * accounts copying the same strategy via CopyFactory API, then you can increase resourceSlots to get a lower trade
   * copying latency. Please note that allocating extra resource slots is a paid option. Default is 1
   * @return number of resource slots to allocate to account
   */
  public int getResourceSlots() {
    return data.resourceSlots;
  }

  /**
   * Returns 3-character ISO currency code of the account base currency. Default value is USD. The setting is to be used
   * for copy trading accounts which use national currencies only, such as some Brazilian brokers. You should not alter
   * this setting unless you understand what you are doing.
   * @return 3-character ISO currency code of the account base currency
   */
  public String getBaseCurrency() {
    return data.baseCurrency;
  }
  
  /**
   * Returns reliability value. Possible values are regular and high
   * @return account reliability value
   */
  public String getReliability() {
    return data.reliability;
  }
  
  /**
   * Returns version value. Possible values are 4 and 5
   * @return account version value
   */
  public int getVersion() {
    return data.version;
  }
  
  /**
   * Reloads MetaTrader account from API
   * @return completable future resolving when MetaTrader account is updated
   */
  public CompletableFuture<Void> reload() {
    return metatraderAccountClient.getAccount(getId()).thenAccept(account -> data = account);
  }
  
  /**
   * Removes MetaTrader account. Cloud account transitions to DELETING state. 
   * It takes some time for an account to be eventually deleted. Self-hosted 
   * account is deleted immediately.
   * @return completable future resolving when account is scheduled for deletion
   */
  public CompletableFuture<Void> remove() {
    return Async.supply(() -> {
      connectionRegistry.remove(getId());
      try {
        metatraderAccountClient.deleteAccount(getId()).get();
        HistoryFileManager fileManager = ServiceProvider.createHistoryFileManager(getId(), "MetaApi", null);
        fileManager.deleteStorageFromDisk().get();
        if (!getType().equals("self-hosted")) {
          try {
            reload().get();
          } catch (ExecutionException e) {
            if (!(e.getCause() instanceof NotFoundException)) throw e.getCause();
          }
        }
        return null;
      } catch (Throwable e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Schedules account for deployment. It takes some time for API server to be started and account 
   * to reach the DEPLOYED state
   * @return completable future resolving when account is scheduled for deployment
   */
  public CompletableFuture<Void> deploy() {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Async.run(() -> {
      try {
        metatraderAccountClient.deployAccount(getId()).get();
        reload().get();
        result.complete(null);
      } catch (Exception e) {
        result.completeExceptionally(e);
      }
    });
    return result;
  }
  
  /**
   * Schedules account for undeployment. It takes some time for API server to be stopped and account 
   * to reach the UNDEPLOYED state
   * @return completable future resolving when account is scheduled for undeployment
   */
  public CompletableFuture<Void> undeploy() {
    return Async.run(() -> {
      connectionRegistry.remove(getId());
      metatraderAccountClient.undeployAccount(getId()).join();
      reload().join();
    });
  }
  
  /**
   * Schedules account for redeployment. It takes some time for API server to be restarted and account
   * to reach the DEPLOYED state
   * @return completable future resolving when account is scheduled for redeployment
   */
  public CompletableFuture<Void> redeploy() {
    return Async.run(() -> {
      metatraderAccountClient.redeployAccount(getId()).join();
      reload().join();
    });
  }
  
  /**
   * Increases MetaTrader account reliability. The account will be temporary stopped to perform this action
   * @return completable future resolving when account reliability is increased
   */
  public CompletableFuture<Void> increaseReliability() {
    return Async.run(() -> {
      metatraderAccountClient.increaseReliability(getId()).join();
      reload().join();
    });
  }
  
  /**
   * Waits until API server has finished deployment and account reached the DEPLOYED state.
   * Completes exceptionally with {@link TimeoutException} if account have not reached the DEPLOYED state
   * withing timeout allowed.
   * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
   * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
   * @return completable future which resolves when account is deployed
   */
  public CompletableFuture<Void> waitDeployed(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Async.run(() -> {
      long startTime = Instant.now().getEpochSecond();
      long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
      try {
        reload().get();
        while (getState() != DeploymentState.DEPLOYED && timeoutTime > Instant.now().getEpochSecond()) {
          Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
          reload().get();
        };
        if (getState() != DeploymentState.DEPLOYED) 
          throw new TimeoutException("Timed out waiting for account " + getId() + " to be deployed");
        result.complete(null);
      } catch (Exception e) {
        result.completeExceptionally(e);
      }
    });
    return result;
  }
  
  /**
   * Waits until API server has finished undeployment and account reached the UNDEPLOYED state.
   * Completes exceptionally with {@link TimeoutException} if account have not reached the UNDEPLOYED state
   * withing timeout allowed.
   * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
   * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
   * @return completable future which resolves when account is undeployed
   */
  public CompletableFuture<Void> waitUndeployed(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Async.run(() -> {
      long startTime = Instant.now().getEpochSecond();
      long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
      try {
        reload().get();
        while (getState() != DeploymentState.UNDEPLOYED && timeoutTime > Instant.now().getEpochSecond()) {
          Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
          reload().get();
        };
        if (getState() != DeploymentState.UNDEPLOYED) 
          throw new TimeoutException("Timed out waiting for account " + getId() + " to be undeployed");
        result.complete(null);
      } catch (Exception e) {
        result.completeExceptionally(e);
      }
    });
    return result;
  }
  
  /**
   * Waits until account has been deleted. Completes exceptionally with {@link TimeoutException} 
   * if account was not deleted withing timeout allowed.
   * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
   * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
   * @return completable future which resolves when account is deleted
   */
  public CompletableFuture<Void> waitRemoved(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Async.run(() -> {
      long startTime = Instant.now().getEpochSecond();
      long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
      try {
        reload().get();
        while (timeoutTime > Instant.now().getEpochSecond()) {
          Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
          reload().get();
        };
        throw new TimeoutException("Timed out waiting for account " + getId() + " to be deleted");
      } catch (Exception e) {
        if (e.getCause() instanceof NotFoundException) result.complete(null);
        else result.completeExceptionally(e);
      }
    });
    return result;
  }
  
  /**
   * Waits until API server has connected to the terminal and terminal has connected to the broker.
   * Completes exceptionally with {@link TimeoutException} if account have not connected to the broker
   * withing timeout allowed. Wait timeout in seconds is 5m and interval between account reloads while 
   * waiting for a change is 1s.
   * @return completable future which resolves when API server is connected to the broker
   */
  public CompletableFuture<Void> waitConnected() {
    return waitConnected(null, null);
  }
  
  /**
   * Waits until API server has connected to the terminal and terminal has connected to the broker.
   * Completes exceptionally with {@link TimeoutException} if account have not connected to the broker
   * withing timeout allowed.
   * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
   * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
   * @return completable future which resolves when API server is connected to the broker
   */
  public CompletableFuture<Void> waitConnected(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    Async.run(() -> {
      long startTime = Instant.now().getEpochSecond();
      long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
      try {
        reload().get();
        while (  getConnectionStatus() != ConnectionStatus.CONNECTED 
            && timeoutTime > Instant.now().getEpochSecond()
        ) {
          Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
          reload().get();
        };
        if (getConnectionStatus() != ConnectionStatus.CONNECTED) 
          throw new TimeoutException("Timed out waiting for account " + getId() + " to connect to the broker");
        result.complete(null);
      } catch (Exception e) {
        result.completeExceptionally(e);
      }
    });
    return result;
  }
  
  /**
   * Connects to MetaApi with default history storage. There is only one connection per account.
   * Subsequent calls to this method will return the same connection.
   * @return MetaApi connection
   */
  public CompletableFuture<MetaApiConnection> connect() {
    return connect(null, null);
  }
  
  /**
   * Connects to MetaApi. There is only one connection per account. 
   * Subsequent calls to this method will return the same connection.
   * @param historyStorage optional history storage, or {@code null}
   * @return MetaApi connection
   */
  public CompletableFuture<MetaApiConnection> connect(HistoryStorage historyStorage) {
    return connect(historyStorage, null);
  }
  
  /**
   * Connects to MetaApi. There is only one connection per account. 
   * Subsequent calls to this method will return the same connection.
   * @param historyStorage optional history storage, or {@code null}
   * @param historyStartTime history start time, or {@code null}. Used for tests
   * @return MetaApi connection
   */
  public CompletableFuture<MetaApiConnection> connect(HistoryStorage historyStorage, IsoTime historyStartTime) {
    return connectionRegistry.connect(this, historyStorage, historyStartTime);
  }
  
  /**
   * Updates MetaTrader account data
   * @param account MetaTrader account update
   * @return completable future resolving when account is updated
   */
  public CompletableFuture<Void> update(MetatraderAccountUpdateDto account) {
    return Async.run(() -> {
      metatraderAccountClient.updateAccount(getId(), account).join();
      reload().join();
    });
  }
  
  /**
   * Retrieves expert advisor of current account
   * @return completable future resolving with an array of expert advisor entities
   */
  public CompletableFuture<List<ExpertAdvisor>> getExpertAdvisors() {
    return Async.supply(() -> {
      checkExpertAdvisorAllowed();
      List<ExpertAdvisorDto> advisors = expertAdvisorClient.getExpertAdvisors(getId()).join();
      return advisors.stream().map(e -> new ExpertAdvisor(e, getId(), expertAdvisorClient))
        .collect(Collectors.toList());
    });
  }
  
  /**
   * Retrieves a expert advisor of current account by id
   * @param expertId expert advisor id
   * @return completable future resolving with expert advisor entity
   */
  public CompletableFuture<ExpertAdvisor> getExpertAdvisor(String expertId) {
    return Async.supply(() -> {
      checkExpertAdvisorAllowed();
      ExpertAdvisorDto advisor = expertAdvisorClient.getExpertAdvisor(getId(), expertId).join();
      return new ExpertAdvisor(advisor, getId(), expertAdvisorClient);
    });
  }
  
  /**
   * Creates an expert advisor
   * @param expertId expert advisor id
   * @param expert expert advisor data
   * @return completable future resolving with expert advisor entity
   */
  public CompletableFuture<ExpertAdvisor> createExpertAdvisor(String expertId, NewExpertAdvisorDto expert) {
    return Async.supply(() -> {
      checkExpertAdvisorAllowed();
      expertAdvisorClient.updateExpertAdvisor(getId(), expertId, expert).join();
      return getExpertAdvisor(expertId).join();
    });
  }

  /**
   * Returns historical candles for a specific symbol and timeframe from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalCandles/
   * @param symbol symbol to retrieve candles for (e.g. a currency pair or an index)
   * @param timeframe defines the timeframe according to which the candles must be generated.
   * Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h,
   * 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @return completable future promise resolving with historical candles downloaded
   */
  public CompletableFuture<List<MetatraderCandle>> getHistoricalCandles(String symbol, String timeframe) {
    return historicalMarketDataClient.getHistoricalCandles(getId(), symbol, timeframe, null, null);
  }
  
  /**
   * Returns historical candles for a specific symbol and timeframe from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalCandles/
   * @param symbol symbol to retrieve candles for (e.g. a currency pair or an index)
   * @param timeframe defines the timeframe according to which the candles must be generated.
   * Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h,
   * 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @param startTime time to start loading candles from. Note that candles are loaded in backwards
   * direction, so this should be the latest time. Leave {@code null} to request latest candles.
   * @return completable future promise resolving with historical candles downloaded
   */
  public CompletableFuture<List<MetatraderCandle>> getHistoricalCandles(String symbol,
    String timeframe, IsoTime startTime) {
    return historicalMarketDataClient.getHistoricalCandles(getId(), symbol, timeframe, startTime, null);
  }
  
  /**
   * Returns historical candles for a specific symbol and timeframe from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalCandles/
   * @param symbol symbol to retrieve candles for (e.g. a currency pair or an index)
   * @param timeframe defines the timeframe according to which the candles must be generated.
   * Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h,
   * 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @param startTime time to start loading candles from. Note that candles are loaded in backwards
   * direction, so this should be the latest time. Leave {@code null} to request latest candles.
   * @param limit maximum number of candles to retrieve, or {@code null}. Must be less or equal to 1000
   * @return completable future resolving with historical candles downloaded
   */
  public CompletableFuture<List<MetatraderCandle>> getHistoricalCandles(String symbol,
    String timeframe, IsoTime startTime, Integer limit) {
    return historicalMarketDataClient.getHistoricalCandles(getId(), symbol, timeframe, startTime, limit);
  }

  /**
   * Returns historical ticks for a specific symbol from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalTicks/
   * @param symbol symbol to retrieve ticks for (e.g. a currency pair or an index)
   * @return completable future resolving with historical ticks downloaded
   */
  public CompletableFuture<List<MetatraderTick>> getHistoricalTicks(String symbol) {
    return historicalMarketDataClient.getHistoricalTicks(getId(), symbol, null, null, null);
  }
  
  /**
   * Returns historical ticks for a specific symbol from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalTicks/
   * @param symbol symbol to retrieve ticks for (e.g. a currency pair or an index)
   * @param startTime time to start loading ticks from. Note that ticks are loaded in forward
   * direction, so this should be the earliest time. Leave {@code null} to request latest candles.
   * @return completable future resolving with historical ticks downloaded
   */
  public CompletableFuture<List<MetatraderTick>> getHistoricalTicks(String symbol, IsoTime startTime) {
    return historicalMarketDataClient.getHistoricalTicks(getId(), symbol, startTime, null, null);
  }
  
  /**
   * Returns historical ticks for a specific symbol from the MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalTicks/
   * @param symbol symbol to retrieve ticks for (e.g. a currency pair or an index)
   * @param startTime time to start loading ticks from. Note that ticks are loaded in forward
   * direction, so this should be the earliest time. Leave {@code null} to request latest candles.
   * @param offset number of ticks to skip, or {@code null} (you can use it to avoid requesting
   * ticks from previous request twice)
   * @param limit maximum number of ticks to retrieve, or {@code null}. Must be less or equal to 1000
   * @return completable future resolving with historical ticks downloaded
   */
  public CompletableFuture<List<MetatraderTick>> getHistoricalTicks(String symbol,
    IsoTime startTime, Integer offset, Integer limit) {
    return historicalMarketDataClient.getHistoricalTicks(getId(), symbol, startTime, offset, limit);
  }
  
  private void checkExpertAdvisorAllowed() throws CompletionException {
    if (getVersion() != 4 || !getType().equals("cloud-g1")) {
      throw new CompletionException(new ValidationException(
        "Custom expert advisor is available only for MT4 G1 accounts", null));
    }
  }
}