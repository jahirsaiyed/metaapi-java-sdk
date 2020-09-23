package cloud.metaapi.sdk.copy_factory;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.copy_factory.ConfigurationClient;
import cloud.metaapi.sdk.clients.copy_factory.HistoryClient;
import cloud.metaapi.sdk.clients.copy_factory.TradingClient;

/**
 * MetaApi CopyFactory copy trading API SDK
 */
public class CopyFactory {
    
    private ConfigurationClient configurationClient;
    private HistoryClient historyClient;
    private TradingClient tradingClient;
    
    /**
     * Constructs CopyFactory class instance. Domain is {@code agiliumtrade.agiliumtrade.ai}, timeout for http requests
     * is {@code 60 seconds}, timeout for connecting to server is {@code 60 seconds}.
     * @param token authorization token
     */
    public CopyFactory(String token) {
        this(token, "agiliumtrade.agiliumtrade.ai", 60, 60);
    }
    
    /**
     * Constructs CopyFactory class instance
     * @param token authorization token
     * @param domain domain to connect to
     * @param requestTimeout timeout for http requests in seconds
     * @param connectTimeout timeout for connecting to server in seconds
     */
    public CopyFactory(String token, String domain, int requestTimeout, int connectTimeout) {
        HttpClient httpClient = new HttpClient(requestTimeout * 1000, connectTimeout * 1000);
        configurationClient = new ConfigurationClient(httpClient, token, domain);
        historyClient = new HistoryClient(httpClient, token, domain);
        tradingClient = new TradingClient(httpClient, token, domain);
    }
    
    /**
     * Returns CopyFactory configuration API
     * @return configuration API
     */
    public ConfigurationClient getConfigurationApi() {
        return configurationClient;
    }
    
    /**
     * Returns CopyFactory history API
     * @return history API
     */
    public HistoryClient getHistoryApi() {
        return historyClient;
    }
    
    /**
     * Returns CopyFactory trading API
     * @return trading API
     */
    public TradingClient getTradingApi() {
        return tradingClient;
    }
}