package cloud.metaapi.sdk.clients.copy_factory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.RandomStringUtils;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.copy_factory.models.*;
import cloud.metaapi.sdk.clients.MetaApiClient;

/**
 * metaapi.cloud CopyFactory configuration API (trade copying configuration API) client (see
 * https://trading-api-v1.project-stock.agiliumlabs.cloud/swagger/#/)
 */
public class ConfigurationClient extends MetaApiClient {

    /**
     * Constructs CopyFactory configuration API client instance. Domain is set to {@code agiliumtrade.agiliumtrade.ai}
     * @param httpClient HTTP client
     * @param token authorization token
     */
    public ConfigurationClient(HttpClient httpClient, String token) {
        this(httpClient, token, "agiliumtrade.agiliumtrade.ai");
    }

    /**
     * Constructs CopyFactory configuration API client instance
     * @param httpClient HTTP client
     * @param token authorization token
     * @param domain domain to connect to
     */
    public ConfigurationClient(HttpClient httpClient, String token, String domain) {
        super(httpClient, token, domain);
        this.host = "https://trading-api-v1." + domain;
    }
    
    /**
     * Retrieves new unused strategy id. Method is accessible only with API access token. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/get_users_current_configuration_unused_strategy_id
     * @return completable future resolving with strategy id generated
     */
    public CompletableFuture<StrategyId> generateStrategyId() {
        if (isNotJwtToken()) return handleNoAccessError("generateStrategyId");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/unused-strategy-id", Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, StrategyId.class);
    }
    
    /**
     * Generates random account id
     * @return account id
     */
    public String generateAccountId() {
        return RandomStringUtils.randomAlphanumeric(64);
    }
    
    /**
     * Retrieves CopyFactory copy trading accounts. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/get_users_current_configuration_accounts
     * @return completable future resolving with CopyFactory accounts found
     */
    public CompletableFuture<List<CopyFactoryAccount>> getAccounts() {
        if (isNotJwtToken()) return handleNoAccessError("getAccounts");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/accounts", Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, CopyFactoryAccount[].class).thenApply((array) -> Arrays.asList(array));
    }
    
    /**
     * Updates a CopyFactory trade copying account. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/put_users_current_configuration_accounts_accountId
     * @param id copy trading account id
     * @param account trading account update
     * @return completable future resolving when account is updated
     */
    public CompletableFuture<Void> updateAccount(String id, CopyFactoryAccountUpdate account) {
        if (isNotJwtToken()) return handleNoAccessError("updateAccount");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/accounts/" + id, Method.PUT);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(account);
        return httpClient.request(opts).thenApply((response) -> null);
    }
    
    /**
     * Deletes a CopyFactory trade copying account. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/delete_users_current_configuration_accounts_accountId
     * @param id copy trading account id
     * @return completable future resolving when account is removed
     */
    public CompletableFuture<Void> removeAccount(String id) {
        if (isNotJwtToken()) return handleNoAccessError("removeAccount");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/accounts/" + id, Method.DELETE);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((response) -> null);
    }
    
    /**
     * Retrieves CopyFactory copy trading strategies. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/get_users_current_configuration_strategies
     * @return completable future resolving with CopyFactory strategies found
     */
    public CompletableFuture<List<CopyFactoryStrategy>> getStrategies() {
        if (isNotJwtToken()) return handleNoAccessError("getStrategies");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/strategies", Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, CopyFactoryStrategy[].class).thenApply(array -> Arrays.asList(array));
    }
    
    /**
     * Updates a CopyFactory strategy. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/put_users_current_configuration_strategies_strategyId
     * @param id copy trading strategy id
     * @param account trading strategy update
     * @return completable future resolving when strategy is updated
     */
    public CompletableFuture<Void> updateStrategy(String id, CopyFactoryStrategyUpdate strategy) {
        if (isNotJwtToken()) return handleNoAccessError("updateStrategy");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/strategies/" + id, Method.PUT);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(strategy);
        return httpClient.request(opts).thenApply((response) -> null);
    }
    
    /**
     * Deletes a CopyFactory strategy. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/delete_users_current_configuration_strategies_strategyId
     * @param id strategy id
     * @return completable future resolving when strategy is removed
     */
    public CompletableFuture<Void> removeStrategy(String id) {
        if (isNotJwtToken()) return handleNoAccessError("removeStrategy");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/configuration/strategies/" + id, Method.DELETE);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((response) -> null);
    }
}