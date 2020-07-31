package metaapi.cloudsdk.lib.clients;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import metaapi.cloudsdk.lib.clients.HttpRequestOptions.Method;
import metaapi.cloudsdk.lib.clients.models.*;

/**
 * metaapi.cloud MetaTrader account API client (see https://metaapi.cloud/docs/provisioning/)
 */
public class MetatraderAccountClient {

    private HttpClient httpClient;
    private String host;
    private String token;
    
    /**
     * Constructs MetaTrader account API client instance with default domain agiliumtrade.agiliumtrade.ai
     * @param httpClient HTTP client
     * @param token authorization token
     */
    public MetatraderAccountClient(HttpClient httpClient, String token) {
        this(httpClient, token, "agiliumtrade.agiliumtrade.ai");
    }
    
    /**
     * @see MetatraderAccountClient#MetatraderAccountClient(HttpClient, String, String)
     * @param domain domain to connect to
     */
    public MetatraderAccountClient(HttpClient httpClient, String token, String domain) {
        this.httpClient = httpClient;
        this.host = "https://mt-provisioning-api-v1." + domain;
        this.token = token;
    }
    
    /**
     * Retrieves MetaTrader accounts owned by user (see 
     * https://metaapi.cloud/docs/provisioning/api/account/readAccounts/)
     * @param provisioningProfileId optional provisioning profile id filter
     * @return completable future resolving with MetaTrader accounts found
     */
    public CompletableFuture<MetatraderAccountDto[]> getAccounts(
        Optional<String> provisioningProfileId
    ) throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts", Method.GET);
        if (provisioningProfileId.isPresent()) {
            opts.getQueryParameters().put("provisioningProfileId", provisioningProfileId.get());
        }
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, MetatraderAccountDto[].class);
    }
    
    /**
     * Retrieves a MetaTrader account by id (see https://metaapi.cloud/docs/provisioning/api/account/readAccount/).
     * Completable future is completed with an error if account is not found.
     * @param id MetaTrader account id
     * @return completable future resolving with MetaTrader account found
     */
    public CompletableFuture<MetatraderAccountDto> getAccount(String id) throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, MetatraderAccountDto.class);
    }
    
    /**
     * Starts cloud API server for a MetaTrader account using specified provisioning profile (see
     * https://metaapi.cloud/docs/provisioning/api/account/createAccount/). It takes some time to launch the terminal and
     * connect the terminal to the broker, you can use the connectionStatus field to monitor the current status of the
     * terminal.
     * @param account MetaTrader account to create
     * @return completable future resolving with an id of the MetaTrader account created
     */
    public CompletableFuture<MetatraderAccountIdDto> createAccount(NewMetatraderAccountDto account) throws Exception {
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
    public CompletableFuture<Void> deployAccount(String id) throws Exception {
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
    public CompletableFuture<Void> undeployAccount(String id) throws Exception {
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
    public CompletableFuture<Void> redeployAccount(String id) throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/accounts/" + id + "/redeploy", Method.POST);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((body) -> null);
    }
    
    /**
     * Stops and deletes an API server for a specified MetaTrader account. The terminal state such as downloaded market
     * data history will be deleted as well when you delete the account. (see
     * https://metaapi.cloud/docs/provisioning/api/account/deleteAccount/)
     * @param id MetaTrader account id
     * @return completable future resolving when MetaTrader account is scheduled for deletion
     */
    public CompletableFuture<Void> deleteAccount(String id) throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.DELETE);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((body) -> null);
    }
    
    /**
     * Updates existing metatrader account data (see
     * https://metaapi.cloud/docs/provisioning/api/account/updateAccount/)
     * @param id MetaTrader account id
     * @param account updated MetaTrader account
     * @return completable future resolving when MetaTrader account is updated
     */
    public CompletableFuture<Void> updateAccount(String id, MetatraderAccountUpdateDto account) throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + id, Method.PUT);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(account);
        return httpClient.request(opts).thenApply((body) -> null);
    }
}