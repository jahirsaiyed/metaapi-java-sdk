package cloud.metaapi.sdk;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.ProvisioningProfileClient;

/**
 * MetaApi MetaTrader API SDK
 */
public class MetaApi {
    
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private ProvisioningProfileApi provisioningProfileApi;
    private MetatraderAccountApi metatraderAccountApi;
    
    /**
     * Constructs MetaApi class instance with default domain agiliumtrade.agiliumtrade.ai
     * @param token authorization token
     */
    public MetaApi(String token) {
        this(token, "agiliumtrade.agiliumtrade.ai");
    }
    
    /**
     * Constructs MetaApi class instance
     * @param token authorization token
     * @param domain domain to connect to
     */
    public MetaApi(String token, String domain) {
        HttpClient httpClient = new HttpClient();
        metaApiWebsocketClient = new MetaApiWebsocketClient(token, domain);
        provisioningProfileApi = new ProvisioningProfileApi(new ProvisioningProfileClient(httpClient, token, domain));
        metatraderAccountApi = new MetatraderAccountApi(new MetatraderAccountClient(httpClient, token, domain),
            metaApiWebsocketClient);
    }
    
    /**
     * Returns provisioning profile API
     * @returns provisioning profile API
     */
    public ProvisioningProfileApi getProvisioningProfileApi() {
        return provisioningProfileApi;
    }
    
    /**
     * Returns MetaTrader account API
     * @return MetaTrader account API
     */
    public MetatraderAccountApi getMetatraderAccountApi() {
        return metatraderAccountApi;
    }
    
    /**
     * Closes all clients and connections
     */
    public void close() {
        metaApiWebsocketClient.close();
    }
}