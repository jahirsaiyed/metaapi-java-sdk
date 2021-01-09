package cloud.metaapi.sdk.meta_api;

import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.MetatraderDemoAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT4DemoAccount;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT5DemoAccount;

/**
 * Exposes MetaTrader demo account API logic to the consumers
 */
public class MetatraderDemoAccountApi {
    
    private MetatraderDemoAccountClient metatraderDemoAccountClient;
    
    /**
     * Constructs a MetaTrader demo account API instance
     * @param metatraderDemoAccountClient MetaTrader demo account REST API client
     */
    public MetatraderDemoAccountApi(MetatraderDemoAccountClient metatraderDemoAccountClient) {
        this.metatraderDemoAccountClient = metatraderDemoAccountClient;
    }
    
    /**
     * Creates new MetaTrader 4 demo account
     * @param profileId id of the provisioning profile that will be used as the basis for creating this account
     * @param account demo account to create
     * @return promise resolving with MetaTrader demo account entity
     */
    public CompletableFuture<MetatraderDemoAccount> createMT4DemoAccount(String profileId, NewMT4DemoAccount account) {
        return metatraderDemoAccountClient.createMT4DemoAccount(profileId, account)
            .thenApply(dto -> new MetatraderDemoAccount(dto));
    }
    
    /**
     * Creates new MetaTrader 5 demo account
     * @param profileId id of the provisioning profile that will be used as the basis for creating this account
     * @param account demo account to create
     * @return promise resolving with MetaTrader demo account entity
     */
    public CompletableFuture<MetatraderDemoAccount> createMT5DemoAccount(String profileId, NewMT5DemoAccount account) {
        return metatraderDemoAccountClient.createMT5DemoAccount(profileId, account)
            .thenApply(dto -> new MetatraderDemoAccount(dto));
    }
}