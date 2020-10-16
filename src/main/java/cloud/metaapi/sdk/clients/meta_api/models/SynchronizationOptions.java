package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Synchronization options
 */
public class SynchronizationOptions {
    /**
     * Application regular expression pattern, or {@code null}. Default is .*
     */
    public String applicationPattern;
    /**
     * Synchronization id, or {@code null}. Last synchronization request id will be used by default
     */
    public String synchronizationId;
    /**
     * Wait timeout in seconds, or {@code null}. Default is 5m
     */
    public Integer timeoutInSeconds;
    /**
     * Interval between account reloads while waiting for a change, or {@code null}. Default is 1s
     */
    public Integer intervalInMilliseconds;
}