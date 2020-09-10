package cloud.metaapi.sdk.clients.models;

/**
 * Object to supply additional information for validation exceptions
 */
public class ValidationDetails {
    /**
     * Name of the parameter to which an error relates
     */
    public String parameter;
    /**
     * Message of the error
     */
    public String message;
    /**
     * Value of the parameter
     */
    public String value;
}