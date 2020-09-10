package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Provisioning profile model
 */
public class ProvisioningProfileDto {
    /**
     * Provisioning profile unique identifier
     */
    public String _id;
    /**
     * Provisioning profile name
     */
    public String name;
    /**
     * MetaTrader version (allowed values are 4 and 5)
     */
    public int version;
    /**
     * Provisioning profile status (allowed values are new and active)
     */
    public String status;
}