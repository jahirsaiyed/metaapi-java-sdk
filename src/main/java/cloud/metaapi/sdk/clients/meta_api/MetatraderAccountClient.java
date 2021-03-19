package cloud.metaapi.sdk.clients.meta_api;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * metaapi.cloud MetaTrader account API client (see https://metaapi.cloud/docs/provisioning/)
 */
public class MetatraderAccountClient extends MetaApiClient {

  private static Logger logger = Logger.getLogger(MetatraderAccountClient.class);
  
  /**
   * Constructs MetaTrader account API client instance with default domain agiliumtrade.agiliumtrade.ai
   * @param httpClient HTTP client
   * @param token authorization token
   */
  public MetatraderAccountClient(HttpClient httpClient, String token) {
    super(httpClient, token);
  }
  
  /**
   * Constructs MetaTrader account API client instance
   * @param httpClient HTTP client
   * @param token authorization token
   * @param domain domain to connect to
   */
  public MetatraderAccountClient(HttpClient httpClient, String token, String domain) {
    super(httpClient, token, domain);
  }
  
  /**
   * Retrieves MetaTrader accounts owned by user (see 
   * https://metaapi.cloud/docs/provisioning/api/account/readAccounts/).
   * Method is accessible only with API access token.
   * @param accountsFilter optional filter or {@code null}
   * @return completable future resolving with MetaTrader accounts found
   */
  public CompletableFuture<List<MetatraderAccountDto>> getAccounts(AccountsFilter accountsFilter) {
    if (this.isNotJwtToken()) return handleNoAccessError("getAccounts");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts", Method.GET);
    if (accountsFilter != null) opts.getQueryParameters().putAll(transformModelToMap(accountsFilter));
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, MetatraderAccountDto[].class).thenApply(array -> Arrays.asList(array));
  }
  
  /**
   * Retrieves a MetaTrader account by token (see https://metaapi.cloud/docs/provisioning/api/account/readAccount/).
   * Completes exceptionally if account is not found.
   * Method is accessible only with account access token
   * @return completable future resolving with MetaTrader account found
   */
  public CompletableFuture<MetatraderAccountDto> getAccountByToken() {
    if (isNotAccountToken()) return handleNoAccessError("getAccountByToken");
    HttpRequestOptions opts = new HttpRequestOptions(
      host + "/users/current/accounts/accessToken/" + token, Method.GET);
    return httpClient.requestJson(opts, MetatraderAccountDto.class);
  }
  
  /**
   * Retrieves a MetaTrader account by id (see https://metaapi.cloud/docs/provisioning/api/account/readAccount/).
   * Completable future is completed with an error if account is not found.
   * @param id MetaTrader account id
   * @return completable future resolving with MetaTrader account found
   */
  public CompletableFuture<MetatraderAccountDto> getAccount(String id) {
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.GET);
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, MetatraderAccountDto.class);
  }
  
  /**
   * Starts cloud API server for a MetaTrader account using specified provisioning profile (see
   * https://metaapi.cloud/docs/provisioning/api/account/createAccount/). It takes some time to launch the terminal and
   * connect the terminal to the broker, you can use the connectionStatus field to monitor the current status of the
   * terminal. Method is accessible only with API access token.
   * @param account MetaTrader account to create
   * @return completable future resolving with an id of the MetaTrader account created
   */
  public CompletableFuture<MetatraderAccountIdDto> createAccount(NewMetatraderAccountDto account) {
    if (isNotJwtToken()) return handleNoAccessError("createAccount");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts", Method.POST);
    opts.getHeaders().put("auth-token", token);
    opts.setBody(account);
    return httpClient.requestJson(opts, MetatraderAccountIdDto.class);
  }
  
  /**
   * Starts API server for MetaTrader account. This request will be ignored if the account has already been deployed.
   * (see https://metaapi.cloud/docs/provisioning/api/account/deployAccount/)
   * @param id MetaTrader account id to deploy
   * @return completable future resolving when MetaTrader account is scheduled for deployment
   */
  public CompletableFuture<Void> deployAccount(String id) {
    if (isNotJwtToken()) return handleNoAccessError("deployAccount");
    HttpRequestOptions opts = new HttpRequestOptions(
      host + "/users/current/accounts/" + id + "/deploy", Method.POST);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  /**
   * Stops API server for a MetaTrader account. Terminal data such as downloaded market history data will be preserved.
   * (see https://metaapi.cloud/docs/provisioning/api/account/undeployAccount/)
   * @param id MetaTrader account id to undeploy
   * @return completable future resolving when MetaTrader account is scheduled for undeployment
   */
  public CompletableFuture<Void> undeployAccount(String id) {
    if (isNotJwtToken()) return handleNoAccessError("undeployAccount");
    HttpRequestOptions opts = new HttpRequestOptions(
      host + "/users/current/accounts/" + id + "/undeploy", Method.POST);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  /**
   * Redeploys MetaTrader account. This is equivalent to undeploy immediately followed by deploy.
   * (see https://metaapi.cloud/docs/provisioning/api/account/deployAccount/)
   * @param id MetaTrader account id to redeploy
   * @return completable future resolving when MetaTrader account is scheduled for redeployment
   */
  public CompletableFuture<Void> redeployAccount(String id) {
    if (isNotJwtToken()) return handleNoAccessError("redeployAccount");
    HttpRequestOptions opts = new HttpRequestOptions(
      host + "/users/current/accounts/" + id + "/redeploy", Method.POST);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  /**
   * Stops and deletes an API server for a specified MetaTrader account. The terminal state such as downloaded market
   * data history will be deleted as well when you delete the account (see
   * https://metaapi.cloud/docs/provisioning/api/account/deleteAccount/).
   * Method is accessible only with API access token
   * @param id MetaTrader account id
   * @return completable future resolving when MetaTrader account is scheduled for deletion
   */
  public CompletableFuture<Void> deleteAccount(String id) {
    if (isNotJwtToken()) return handleNoAccessError("deleteAccount");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.DELETE);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  /**
   * Updates existing metatrader account data (see
   * https://metaapi.cloud/docs/provisioning/api/account/updateAccount/).
   * Method is accessible only with API access token
   * @param id MetaTrader account id
   * @param account updated MetaTrader account
   * @return completable future resolving when MetaTrader account is updated
   */
  public CompletableFuture<Void> updateAccount(String id, MetatraderAccountUpdateDto account) {
    if (isNotJwtToken()) return handleNoAccessError("updateAccount");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.PUT);
    opts.getHeaders().put("auth-token", token);
    opts.setBody(account);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  /**
   * Increases MetaTrader account reliability. The account will be temporary stopped to perform this action. (see
   * https://metaapi.cloud/docs/provisioning/api/account/increaseReliability/).
   * Method is accessible only with API access token
   * @param id MetaTrader account id
   * @return completable future resolving when MetaTrader account reliability is increased
   */
  public CompletableFuture<Void> increaseReliability(String id) {
    if (isNotJwtToken()) return handleNoAccessError("increaseReliability");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + id + "/increase-reliability", Method.POST);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply((body) -> null);
  }
  
  private Map<String, Object> transformModelToMap(Object model) {
    Map<String, Object> result = new HashMap<>();
    Field[] publicFields = model.getClass().getFields();
    for (int i = 0; i < publicFields.length; ++i) {
      Field field = publicFields[i];
      try {
        if (field.get(model) != null) result.put(field.getName(), field.get(model));
      } catch (IllegalArgumentException | IllegalAccessException e) {
        logger.error("Cannot transform model to map", e);
      }
    }
    return result;
  }
}