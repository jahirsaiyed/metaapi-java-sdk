package cloud.metaapi.sdk.clients.meta_api;

import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDemoAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT4DemoAccount;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT5DemoAccount;

/**
 * metaapi.cloud MetaTrader demo account API client
 */
public class MetatraderDemoAccountClient extends MetaApiClient {

    /**
     * Constructs class instance. Domain is set to {@code agiliumtrade.agiliumtrade.ai} 
     * @param httpClient HTTP client
     * @param token authorization token
     */
    public MetatraderDemoAccountClient(HttpClient httpClient, String token) {
        super(httpClient, token);
    }

    /**
     * Constructs class instance
     * @param httpClient HTTP client
     * @param token authorization token
     * @param domain domain to connect to
     */
    public MetatraderDemoAccountClient(HttpClient httpClient, String token, String domain) {
        super(httpClient, token, domain);
    }
    
    /**
     * Creates new MetaTrader 4 demo account
     * Method is accessible only with API access token
     * @param profileId id of the provisioning profile that will be used as the basis for creating this account
     * @param account demo account to create
     * @return completable future resolving with MetaTrader demo account created
     */
    public CompletableFuture<MetatraderDemoAccountDto> createMT4DemoAccount(String profileId, NewMT4DemoAccount account) {
        if (isNotJwtToken()) return handleNoAccessError("createMT4DemoAccount");
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/provisioning-profiles/"
            + profileId + "/mt4-demo-accounts", Method.POST);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(account);
        return httpClient.requestJson(opts, MetatraderDemoAccountDto.class);
    }
    
    /**
     * Creates new MetaTrader 5 demo account
     * Method is accessible only with API access token
     * @param profileId id of the provisioning profile that will be used as the basis for creating this account
     * @param account demo account to create
     * @return completable future resolving with MetaTrader demo account created
     */
    public CompletableFuture<MetatraderDemoAccountDto> createMT5DemoAccount(String profileId, NewMT5DemoAccount account) {
        if (isNotJwtToken()) return handleNoAccessError("createMT5DemoAccount");
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/provisioning-profiles/"
            + profileId + "/mt5-demo-accounts", Method.POST);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(account);
        return httpClient.requestJson(opts, MetatraderDemoAccountDto.class);
    }
}