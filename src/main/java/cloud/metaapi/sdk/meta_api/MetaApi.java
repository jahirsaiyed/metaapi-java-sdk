package cloud.metaapi.sdk.meta_api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderDemoAccountClient;
import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient.PacketLoggerOptions;
import cloud.metaapi.sdk.clients.models.ValidationDetails;

/**
 * MetaApi MetaTrader API SDK
 */
public class MetaApi {
    
    private static Logger logger = Logger.getLogger(MetaApi.class);
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private ProvisioningProfileApi provisioningProfileApi;
    private MetatraderAccountApi metatraderAccountApi;
    private ConnectionRegistry connectionRegistry;
    private MetatraderDemoAccountApi metatraderDemoAccountApi;
    
    /**
     * MetaApi options
     */
    public static class Options {
        /**
         * Application id, or {@code null}. By default is {@code MetaApi}
         */
        public String application;
        /**
         * Domain to connect to, or {@code null}. By default is {@code agiliumtrade.agiliumtrade.ai}
         */
        public String domain;
        /**
         * Timeout for socket requests in seconds or {@code null}. By default is {@code 1 minute}
         */
        public Integer requestTimeout;
        /**
         * Timeout for connecting to server in seconds or {@code null}. By default is {@code 1 minute}
         */
        public Integer connectTimeout;
        /**
         * Packet ordering timeout in seconds, or {@code null}. Default is {@code 1 minute}
         */
        public Integer packetOrderingTimeout;
        /**
         * Packet logger options
         */
        public MetaApiWebsocketClient.PacketLoggerOptions packetLogger;
    }
    
    /**
     * Constructs MetaApi class instance with default options
     * @param token authorization token
     * @throws IOException if packet logger is enabled and failed to create the log directory
     */
    public MetaApi(String token) throws IOException {
        try {
            initialize(token, null);
        } catch (ValidationException e) {
            logger.error("Application name is incorrect", e);
        }
    }
    
    /**
     * Constructs MetaApi class instance
     * @param token authorization token
     * @param opts application options, or {@code null}
     * @throws ValidationException if application name is incorrect
     * @throws IOException if packet logger is enabled and failed to create the log directory
     */
    public MetaApi(String token, Options opts) throws ValidationException, IOException {
        initialize(token, opts);
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
    
    private void initialize(String token, Options opts) throws ValidationException, IOException {
        String application = opts != null && opts.application != null ? opts.application : "MetaApi";
        String domain = opts != null && opts.domain != null ? opts.domain : "agiliumtrade.agiliumtrade.ai";
        int requestTimeout = opts != null && opts.requestTimeout != null ? opts.requestTimeout : 60;
        int connectTimeout = opts != null && opts.connectTimeout != null ? opts.connectTimeout : 60;
        int packetOrderingTimeout = opts != null && opts.packetOrderingTimeout != null ? opts.packetOrderingTimeout : 60;
        PacketLoggerOptions packetLogger = opts != null ? opts.packetLogger : null;
        if (!application.matches("[a-zA-Z0-9_]+")) {
            List<ValidationDetails> details = new ArrayList<>();
            throw new ValidationException("Application name must be non-empty string consisting from letters, digits and _ only", details);
        }
        HttpClient httpClient = new HttpClient(requestTimeout * 1000, connectTimeout * 1000);
        MetaApiWebsocketClient.ClientOptions websocketOptions = new MetaApiWebsocketClient.ClientOptions();
        websocketOptions.application = application;
        websocketOptions.domain = domain;
        websocketOptions.requestTimeout = (long) requestTimeout;
        websocketOptions.connectTimeout = (long) connectTimeout;
        websocketOptions.packetOrderingTimeout = packetOrderingTimeout;
        websocketOptions.packetLogger = packetLogger;
        metaApiWebsocketClient = new MetaApiWebsocketClient(token, websocketOptions);
        provisioningProfileApi = new ProvisioningProfileApi(new ProvisioningProfileClient(httpClient, token, domain));
        connectionRegistry = new ConnectionRegistry(metaApiWebsocketClient, application);
        metatraderAccountApi = new MetatraderAccountApi(
            new MetatraderAccountClient(httpClient, token, domain),
            metaApiWebsocketClient, connectionRegistry);
        metatraderDemoAccountApi = new MetatraderDemoAccountApi(
            new MetatraderDemoAccountClient(httpClient, token, domain));
    }
}