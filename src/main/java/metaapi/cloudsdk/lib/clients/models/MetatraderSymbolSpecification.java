package metaapi.cloudsdk.lib.clients.models;

/**
 * MetaTrader symbol specification. Contains symbol specification (see
 * https://metaapi.cloud/docs/client/models/metatraderSymbolSpecification/)
 */
public class MetatraderSymbolSpecification {
    /**
     * Symbol (e.g. a currency pair or an index)
     */
    public String symbol;
    /**
     * Tick size
     */
    public double tickSize;
    /**
     * Minimum order volume for the symbol
     */
    public double minVolume;
    /**
     * Maximum order volume for the symbol
     */
    public double maxVolume;
    /**
     * Order volume step for the symbol
     */
    public double volumeStep;
}