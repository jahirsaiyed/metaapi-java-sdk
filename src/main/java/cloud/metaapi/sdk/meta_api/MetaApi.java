package cloud.metaapi.sdk.meta_api;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpClientWithCookies;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;

/**
 * MetaApi MetaTrader API SDK
 */
public class MetaApi {
    
    private static Logger logger = Logger.getLogger(MetaApi.class);
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private ProvisioningProfileApi provisioningProfileApi;
    private MetatraderAccountApi metatraderAccountApi;
    
    /**
     * Constructs MetaApi class instance. Domain is {@code agiliumtrade.agiliumtrade.ai}, timeout for http requests is
     * {@code 60 seconds}, timeout for connecting to server is {@code 60 seconds}.
     * @param token authorization token
     */
    public MetaApi(String token) {
        this(token, "agiliumtrade.agiliumtrade.ai", 60, 60);
    }
    
    /**
     * Constructs MetaApi class instance
     * @param token authorization token
     * @param domain domain to connect to
     * @param requestTimeout timeout for http requests in seconds
     * @param connectTimeout timeout for connecting to server in seconds
     */
    public MetaApi(String token, String domain, int requestTimeout, int connectTimeout) {
        HttpClient httpClient = new HttpClient(requestTimeout * 1000, connectTimeout * 1000);
        metaApiWebsocketClient = new MetaApiWebsocketClient(
            new HttpClientWithCookies(), token, domain, 
            requestTimeout * 1000, connectTimeout * 1000);
        provisioningProfileApi = new ProvisioningProfileApi(new ProvisioningProfileClient(httpClient, token, domain));
        metatraderAccountApi = new MetatraderAccountApi(
            new MetatraderAccountClient(httpClient, token, domain),
            metaApiWebsocketClient);
        metaApiWebsocketClient.connect().exceptionally(err -> {
            logger.error("Failed to connect to MetaApi websocket API", err);
            return null;
        });
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