package cloud.metaapi.sdk.clients.meta_api.models;

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
    /**
     * Broker timezone name from Time Zone Database
     */
    public String brokerTimezone;
    /**
     * Broker DST timezone name from Time Zone Database
     */
    public String brokerDSTTimezone;
}