package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

/**
 * MetaTrader symbol specification. Contains symbol specification (see
 * https://metaapi.cloud/docs/client/models/metatraderSymbolSpecification/)
 */
public class MetatraderSymbolSpecification {
    
    /**
     * Order filling modes. See 
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#symbol_filling_mode
     * for more details.
     */
    public enum FillingMode { SYMBOL_FILLING_FOK, SYMBOL_FILLING_IOC };
    
    /**
     * Deal execution mode. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_trade_execution
     * for more details.
     */
    public enum ExecutionMode { SYMBOL_TRADE_EXECUTION_REQUEST, SYMBOL_TRADE_EXECUTION_INSTANT, 
        SYMBOL_TRADE_EXECUTION_MARKET, SYMBOL_TRADE_EXECUTION_EXCHANGE };
    
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
    /**
     * List of allowed order filling modes
     */
    public List<FillingMode> fillingModes;
    /**
     * Deal execution mode
     */
    public ExecutionMode executionMode;
}