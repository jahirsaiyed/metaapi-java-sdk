package cloud.metaapi.sdk.clients.meta_api.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connection health status
 */
public class ConnectionHealthStatus {
    /**
     * Flag indicating successfull connection to API server
     */
    public boolean connected;
    /**
     * Flag indicating successfull connection to broker
     */
    public boolean connectedToBroker;
    /**
     * Flag indicating that quotes are being streamed successfully from the broker
     */
    public boolean quoteStreamingHealthy;
    /**
     * Flag indicating a successful synchronization
     */
    @JsonProperty("synchronized")
    public boolean isSynchronized;
    /**
     * Flag indicating overall connection health status
     */
    public boolean healthy;
    /**
     * Health status message
     */
    public String message;
}