package metaapi.cloudsdk.lib.clients.models;

import java.util.Optional;

/**
 * MetaTrader account information (see https://metaapi.cloud/docs/client/models/metatraderAccountInformation/)
 */
public class MetatraderAccountInformation {
    /**
     * Broker name
     */
    public String broker;
    /**
     * Account base currency ISO code
     */
    public String currency;
    /**
     * Broker server name
     */
    public String server;
    /**
     * Account balance
     */
    public double balance;
    /**
     * Account liquidation value
     */
    public double equity;
    /**
     * Used margin
     */
    public double margin;
    /**
     * Free margin
     */
    public double freeMargin;
    /**
     * Account leverage coefficient
     */
    public double leverage;
    /**
     * Margin level calculated as % of freeMargin/margin
     */
    public Optional<Double> marginLevel;
}