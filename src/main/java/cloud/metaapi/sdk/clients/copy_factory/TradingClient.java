package cloud.metaapi.sdk.clients.copy_factory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.copy_factory.models.*;

/**
 * metaapi.cloud CopyFactory trading API (trade copying trading API) client (see
 * https://trading-api-v1.project-stock.agiliumlabs.cloud/swagger/#/)
 */
public class TradingClient extends MetaApiClient {

    /**
     * Constructs CopyFactory trading API client instance. Domain is set to {@code agiliumtrade.agiliumtrade.ai}
     * @param httpClient HTTP client
     * @param token authorization token
     */
    public TradingClient(HttpClient httpClient, String token) {
        this(httpClient, token, "agiliumtrade.agiliumtrade.ai");
    }

    /**
     * Constructs CopyFactory trading API client instance
     * @param httpClient HTTP client
     * @param token authorization token
     * @param domain domain to connect to
     */
    public TradingClient(HttpClient httpClient, String token, String domain) {
        super(httpClient, token, domain);
        this.host = "https://trading-api-v1." + domain;
    }
    
    /**
     * Resynchronizes the account. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/post_users_current_accounts_accountId_resynchronize
     * @param accountId account id
     * @param strategyIds optional array of strategy ids to recynchronize, or {@code null}.
     * Default is to synchronize all strategies
     * @return completable future which resolves when resynchronization is scheduled
     */
    public CompletableFuture<Void> resynchronize(String accountId, List<String> strategyIds) {
        if (isNotJwtToken()) return handleNoAccessError("resynchronize");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/accounts/" + accountId + "/resynchronize", Method.POST);
        opts.getHeaders().put("auth-token", token);
        if (strategyIds != null && !strategyIds.isEmpty()) opts.getQueryParameters().put("strategyId", strategyIds);
        return httpClient.request(opts).thenApply(response -> null);
    }
    
    /**
     * Returns subscriber account stopouts. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/get_users_current_accounts_accountId_stopouts
     * @param accountId account id
     * @return completable future which resolves with stopouts found
     */
    public CompletableFuture<List<CopyFactoryStrategyStopout>> getStopouts(String accountId) {
        if (isNotJwtToken()) return handleNoAccessError("getStopouts");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/accounts/" + accountId + "/stopouts", Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, CopyFactoryStrategyStopout[].class)
            .thenApply((array) -> Arrays.asList(array));
    }
    
    /**
     * Resets account stopout. See
     * https://trading-api-v1.agiliumtrade.agiliumtrade.ai/swagger/#!/default/post_users_current_accounts_accountId_stopouts_reason_reset
     * @param accountId account id
     * @param reason stopout reason to reset. One of yearly-balance, monthly-balance, daily-balance,
     * yearly-equity, monthly-equity, daily-equity, max-drawdown
     * @return completable future which resolves when the stopout is reset
     */
    public CompletableFuture<Void> resetStopout(String accountId, String reason) {
        if (isNotJwtToken()) return handleNoAccessError("resetStopout");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/accounts/" + accountId + "/stopouts/" + reason + "/reset", Method.POST);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply(response -> null);
    }
}