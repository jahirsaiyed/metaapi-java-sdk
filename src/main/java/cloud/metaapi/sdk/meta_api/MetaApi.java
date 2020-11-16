package cloud.metaapi.sdk.meta_api;

import java.util.ArrayList;
import java.util.List;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderDemoAccountClient;
import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;
import cloud.metaapi.sdk.clients.models.ValidationDetails;

/**
 * MetaApi MetaTrader API SDK
 */
public class MetaApi {
    
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private ProvisioningProfileApi provisioningProfileApi;
    private MetatraderAccountApi metatraderAccountApi;
    private ConnectionRegistry connectionRegistry;
    private MetatraderDemoAccountApi metatraderDemoAccountApi;
    
    /**
     * Constructs MetaApi class instance. Domain is {@code agiliumtrade.agiliumtrade.ai}, application is {@code MetaApi},
     * timeout for http requests is 60 seconds, timeout for connecting to server is 60 seconds.
     * @param token authorization token
     */
    public MetaApi(String token) throws ValidationException {
        this(token, null, null, null, null, null);
    }
    
    /**
     * Constructs MetaApi class instance. Domain is {@code agiliumtrade.agiliumtrade.ai}, timeout for http requests is
     * 60 seconds, timeout for connecting to server is 60 seconds.
     * @param token authorization token
     * @param application id, or {@code null}. By default is {@code MetaApi}
     * 
     */
    public MetaApi(String token, String application) throws ValidationException {
        this(token, application, null, null, null, null);
    }
    
    /**
     * Constructs MetaApi class instance. Timeout for http requests is 60 seconds,
     * timeout for connecting to server is 60 seconds.
     * @param token authorization token
     * @param application id, or {@code null}. By default is {@code MetaApi}
     * @param domain domain to connect to, or {@code null}. By default is {@code agiliumtrade.agiliumtrade.ai} used
     */
    public MetaApi(String token, String application, String domain) throws ValidationException {
        this(token, application, domain, null, null, null);
    }
    
    /**
     * Constructs MetaApi class instance
     * @param token authorization token
     * @param application id, or {@code null}. By default is {@code MetaApi}
     * @param domain domain to connect to, or {@code null}. By default is {@code agiliumtrade.agiliumtrade.ai} used
     * @param requestTimeout timeout for http requests in seconds, or {@code null}. By default is 60 seconds
     * @param connectTimeout timeout for connecting to server in seconds, or {@code null}. By default is 60 seconds
     */
    public MetaApi(String token, String application, String domain, Integer requestTimeout,
        Integer connectTimeout) throws ValidationException {
        this(token, application, domain, requestTimeout, connectTimeout, null);
    }
    
    /**
     * Constructs MetaApi class instance
     * @param token authorization token
     * @param application id, or {@code null}. By default is {@code MetaApi}
     * @param domain domain to connect to, or {@code null}. By default is {@code agiliumtrade.agiliumtrade.ai} used
     * @param requestTimeout timeout for http requests in seconds, or {@code null}. By default is 60 seconds
     * @param connectTimeout timeout for connecting to server in seconds, or {@code null}. By default is 60 seconds
     * @param packetOrderingTimeout packet ordering timeout in seconds, or {@code null}. By default is 60 seconds
     */
    public MetaApi(String token, String application, String domain, Integer requestTimeout, Integer connectTimeout,
        Integer packetOrderingTimeout) throws ValidationException {
        if (application == null) application = "MetaApi";
        else if (!application.matches("[a-zA-Z0-9_]+")) {
            List<ValidationDetails> details = new ArrayList<>();
            throw new ValidationException("Application name must be non-empty string consisting from letters, digits and _ only", details);
        }
        if (domain == null) domain = "agiliumtrade.agiliumtrade.ai";
        if (requestTimeout == null) requestTimeout = 60;
        if (connectTimeout == null) connectTimeout = 60;
        HttpClient httpClient = new HttpClient(requestTimeout * 1000, connectTimeout * 1000);
        metaApiWebsocketClient = new MetaApiWebsocketClient(token, application, domain, requestTimeout * 1000L, 
            connectTimeout * 1000L, packetOrderingTimeout);
        provisioningProfileApi = new ProvisioningProfileApi(new ProvisioningProfileClient(httpClient, token, domain));
        connectionRegistry = new ConnectionRegistry(metaApiWebsocketClient, application);
        metatraderAccountApi = new MetatraderAccountApi(
            new MetatraderAccountClient(httpClient, token, domain),
            metaApiWebsocketClient, connectionRegistry);
        metatraderDemoAccountApi = new MetatraderDemoAccountApi(
            new MetatraderDemoAccountClient(httpClient, token, domain));
    }
    
    /**
     * Returns provisioning profile API
     * @return provisioning profile API
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
     * Returns MetaTrader demo account API
     * @return MetaTrader demo account API
     */
    public MetatraderDemoAccountApi getMetatraderDemoAccountApi() {
        return metatraderDemoAccountApi;
    }
    
    /**
     * Closes all clients and connections
     */
    public void close() {
        metaApiWebsocketClient.close();
    }
}