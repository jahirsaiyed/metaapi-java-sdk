package metaapi.cloudsdk.lib.clients.models;

/**
 * New provisioning profile model
 */
public class NewProvisioningProfileDto {
    /**
     * Provisioning profile name
     */
    public String name;
    /**
     * MetaTrader version (allowed values are 4 and 5)
     */
    public int version;
}