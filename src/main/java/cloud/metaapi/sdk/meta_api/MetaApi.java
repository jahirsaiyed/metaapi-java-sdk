package cloud.metaapi.sdk.meta_api;

import java.io.IOException;

import org.apache.log4j.Logger;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.RetryOptions;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderDemoAccountClient;
import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;

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
  private LatencyMonitor latencyMonitor;
  
  /**
   * MetaApi options
   */
  public static class Options {
    /**
     * Application id. By default is {@code MetaApi}
     */
    public String application = "MetaApi";
    /**
     * Domain to connect to. By default is {@code agiliumtrade.agiliumtrade.ai}
     */
    public String domain = "agiliumtrade.agiliumtrade.ai";
    /**
     * Timeout for socket requests in seconds. By default is {@code 1 minute}
     */
    public int requestTimeout = 60;
    /**
     * Timeout for connecting to server in seconds. By default is {@code 1 minute}
     */
    public int connectTimeout = 60;
    /**
     * Packet ordering timeout in seconds. Default is {@code 1 minute}
     */
    public int packetOrderingTimeout = 60;
    /**
     * Packet logger options
     */
    public MetaApiWebsocketClient.PacketLoggerOptions packetLogger = new MetaApiWebsocketClient.PacketLoggerOptions();
    /**
     * Flag to enable latency tracking
     */
    public boolean enableLatencyMonitor = false;
    /**
     * Max amount of concurrent synchronizations
     */
    public int maxConcurrentSynchronizations = 5;
    /**
     * Retry options
     */
    public RetryOptions retryOpts = new RetryOptions();
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
   * Returns MetaApi application latency monitor
   * @return latency monitor
   */
  public LatencyMonitor getLatencyMonitor() {
    return latencyMonitor;
  }
  
  /**
   * Closes all clients and connections
   */
  public void close() {
    metaApiWebsocketClient.removeLatencyListener(latencyMonitor);
    metaApiWebsocketClient.close();
  }
  
  private void initialize(String token, Options opts) throws ValidationException, IOException {
    if (opts == null) opts = new Options();
    if (!opts.application.matches("[a-zA-Z0-9_]+")) {
      throw new ValidationException("Application name must be non-empty string consisting from letters, digits and _ only", null);
    }
    HttpClient httpClient = new HttpClient(opts.requestTimeout * 1000, opts.connectTimeout * 1000, opts.retryOpts);
    MetaApiWebsocketClient.ClientOptions websocketOptions = new MetaApiWebsocketClient.ClientOptions();
    websocketOptions.application = opts.application;
    websocketOptions.domain = opts.domain;
    websocketOptions.requestTimeout = opts.requestTimeout * 1000L;
    websocketOptions.connectTimeout = opts.connectTimeout * 1000L;
    websocketOptions.packetOrderingTimeout = opts.packetOrderingTimeout;
    websocketOptions.packetLogger = opts.packetLogger;
    websocketOptions.maxConcurrentSynchronizations = opts.maxConcurrentSynchronizations;
    websocketOptions.retryOpts = opts.retryOpts;
    metaApiWebsocketClient = new MetaApiWebsocketClient(token, websocketOptions);
    provisioningProfileApi = new ProvisioningProfileApi(new ProvisioningProfileClient(httpClient, token, opts.domain));
    connectionRegistry = new ConnectionRegistry(metaApiWebsocketClient, opts.application);
    metatraderAccountApi = new MetatraderAccountApi(
      new MetatraderAccountClient(httpClient, token, opts.domain),
      metaApiWebsocketClient, connectionRegistry);
    metatraderDemoAccountApi = new MetatraderDemoAccountApi(
      new MetatraderDemoAccountClient(httpClient, token, opts.domain));
    if (opts.enableLatencyMonitor) {
      latencyMonitor = new LatencyMonitor();
      metaApiWebsocketClient.addLatencyListener(latencyMonitor);
    }
  }
}