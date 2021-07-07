package cloud.metaapi.sdk.meta_api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import cloud.metaapi.sdk.clients.meta_api.HistoricalMarketDataClient;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.AccountsFilter;
import cloud.metaapi.sdk.clients.meta_api.models.NewMetatraderAccountDto;

/**
 * Exposes MetaTrader account API logic to the consumers
 */
public class MetatraderAccountApi {
  
  private MetatraderAccountClient metatraderAccountClient;
  private MetaApiWebsocketClient metaApiWebsocketClient;
  private ConnectionRegistry connectionRegistry;
  private HistoricalMarketDataClient historicalMarketDataClient;
  private ExpertAdvisorClient expertAdvisorClient;
  
  /**
   * Constructs a MetaTrader account API instance
   * @param metatraderAccountClient MetaTrader account REST API client
   * @param metaApiWebsocketClient MetaApi websocket client
   * @param connectionRegistry metatrader account connection registry
   * @param expertAdvisorClient expert advisor REST API client
   */
  public MetatraderAccountApi(MetatraderAccountClient metatraderAccountClient,
    MetaApiWebsocketClient metaApiWebsocketClient, ConnectionRegistry connectionRegistry,
    ExpertAdvisorClient expertAdvisorClient, HistoricalMarketDataClient historicalMarketDataClient) {
    this.metatraderAccountClient = metatraderAccountClient;
    this.metaApiWebsocketClient = metaApiWebsocketClient;
    this.connectionRegistry = connectionRegistry;
    this.expertAdvisorClient = expertAdvisorClient;
    this.historicalMarketDataClient = historicalMarketDataClient;
  }
  
  /**
   * Retrieves MetaTrader accounts without filtering
   * @return completable future resolving with a list of MetaTrader account entities
   */
  public CompletableFuture<List<MetatraderAccount>> getAccounts() {
    return getAccounts(null);
  }
  
  /**
   * Retrieves MetaTrader accounts
   * @param accountsFilter optional filter or {@code null}
   * @return completable future resolving with a list of MetaTrader account entities
   */
  public CompletableFuture<List<MetatraderAccount>> getAccounts(AccountsFilter accountsFilter) {
    return metatraderAccountClient.getAccounts(accountsFilter).thenApply(accounts -> {
      return accounts.stream().map(accountDto -> new MetatraderAccount(accountDto, metatraderAccountClient,
        metaApiWebsocketClient, connectionRegistry, expertAdvisorClient, historicalMarketDataClient))
        .collect(Collectors.toList());
    });
  }
  
  /**
   * Retrieves a MetaTrader account by id
   * @param accountId MetaTrader account id
   * @return completable future resolving with MetaTrader account entity
   */
  public CompletableFuture<MetatraderAccount> getAccount(String accountId) {
    return metatraderAccountClient.getAccount(accountId).thenApply(accountDto -> {
      return new MetatraderAccount(accountDto, metatraderAccountClient, metaApiWebsocketClient,
        connectionRegistry, expertAdvisorClient, historicalMarketDataClient);
    });
  }
  
  /**
   * Retrieves a MetaTrader account by token
   * @return completable future resolving with MetaTrader account entity
   */
  public CompletableFuture<MetatraderAccount> getAccountByToken() {
    return metatraderAccountClient.getAccountByToken().thenApply(accountDto -> {
      return new MetatraderAccount(accountDto, metatraderAccountClient, metaApiWebsocketClient,
        connectionRegistry, expertAdvisorClient, historicalMarketDataClient);
    });
  }
  
  /**
   * Creates a MetaTrader account
   * @param account MetaTrader account data
   * @return completable future resolving with MetaTrader account entity
   */
  public CompletableFuture<MetatraderAccount> createAccount(NewMetatraderAccountDto account) {
    return metatraderAccountClient.createAccount(account).thenApply(id -> {
      try {
        return getAccount(id.id).get();
      } catch (Exception e) {
        throw new CompletionException(e);
      }
    });
  }
}