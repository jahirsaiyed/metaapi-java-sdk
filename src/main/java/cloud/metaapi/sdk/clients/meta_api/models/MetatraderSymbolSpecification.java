package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader symbol specification. Contains symbol specification (see
 * https://metaapi.cloud/docs/client/models/metatraderSymbolSpecification/)
 */
public class MetatraderSymbolSpecification {
    
    /**
     * Order filling mode. See 
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#symbol_filling_mode
     * for more details
     */
    public enum FillingMode { SYMBOL_FILLING_FOK, SYMBOL_FILLING_IOC };
    
    /**
     * Deal execution mode. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_trade_execution
     * for more details
     */
    public enum ExecutionMode { SYMBOL_TRADE_EXECUTION_REQUEST, SYMBOL_TRADE_EXECUTION_INSTANT, 
        SYMBOL_TRADE_EXECUTION_MARKET, SYMBOL_TRADE_EXECUTION_EXCHANGE };
    
    /**
     * Order execution type. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_trade_mode
     * for more details
     */
    public enum TradeMode { SYMBOL_TRADE_MODE_DISABLED, SYMBOL_TRADE_MODE_LONGONLY,
        SYMBOL_TRADE_MODE_SHORTONLY, SYMBOL_TRADE_MODE_CLOSEONLY, SYMBOL_TRADE_MODE_FULL };
        
    /**
     * Contract price calculation mode. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_calc_mode
     * for more details
     */
    public enum CalcMode { SYMBOL_CALC_MODE_UNKNOWN, SYMBOL_CALC_MODE_FOREX, SYMBOL_CALC_MODE_FOREX_NO_LEVERAGE,
        SYMBOL_CALC_MODE_FUTURES, SYMBOL_CALC_MODE_CFD, SYMBOL_CALC_MODE_CFDINDEX, SYMBOL_CALC_MODE_CFDLEVERAGE,
        SYMBOL_CALC_MODE_EXCH_STOCKS, SYMBOL_CALC_MODE_EXCH_FUTURES, SYMBOL_CALC_MODE_EXCH_FUTURES_FORTS,
        SYMBOL_CALC_MODE_EXCH_BONDS, SYMBOL_CALC_MODE_EXCH_STOCKS_MOEX, SYMBOL_CALC_MODE_EXCH_BONDS_MOEX,
        SYMBOL_CALC_MODE_SERV_COLLATERAL };
        
    /**
     * Swap calculation model. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_swap_mode
     * for more details
     */
    public enum SwapMode { SYMBOL_SWAP_MODE_DISABLED, SYMBOL_SWAP_MODE_POINTS, SYMBOL_SWAP_MODE_CURRENCY_SYMBOL,
        SYMBOL_SWAP_MODE_CURRENCY_MARGIN, SYMBOL_SWAP_MODE_CURRENCY_DEPOSIT, SYMBOL_SWAP_MODE_INTEREST_CURRENT,
        SYMBOL_SWAP_MODE_INTEREST_OPEN, SYMBOL_SWAP_MODE_REOPEN_CURRENT, SYMBOL_SWAP_MODE_REOPEN_BID }
        
    /**
     * Order expiration mode. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#symbol_expiration_mode
     * for more details
     */
    public enum ExpirationMode { SYMBOL_EXPIRATION_GTC, SYMBOL_EXPIRATION_DAY, SYMBOL_EXPIRATION_SPECIFIED,
        SYMBOL_EXPIRATION_SPECIFIED_DAY };
    
    /**
     * Order type. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#symbol_order_mode
     * for more details
     */
    public enum OrderType { SYMBOL_ORDER_MARKET, SYMBOL_ORDER_LIMIT, SYMBOL_ORDER_STOP, SYMBOL_ORDER_STOP_LIMIT,
        SYMBOL_ORDER_SL, SYMBOL_ORDER_TP, SYMBOL_ORDER_CLOSEBY };
        
    /**
     * Order GTC mode. See
     * https://www.mql5.com/en/docs/constants/environment_state/marketinfoconstants#enum_symbol_order_gtc_mode
     * for more details
     */
    public enum OrderGtcMode { SYMBOL_ORDERS_GTC, SYMBOL_ORDERS_DAILY, SYMBOL_ORDERS_DAILY_EXCLUDING_STOPS };
        
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
    /**
     * Trade contract size
     */
    public int contractSize;
    /**
     * Quote sessions, indexed by day of week
     */
    public MetatraderSessions quoteSessions;
    /**
     * Trade sessions, indexed by day of week
     */
    public MetatraderSessions tradeSessions;
    /**
     * Order execution type, or {@code null}
     */
    public TradeMode tradeMode;
    /**
     * Accrued interest, or {@code null} – accumulated coupon interest, i.e. part of the coupon interest calculated
     * in proportion to the number of days since the coupon bond issuance or the last coupon interest payment
     */
    public Double bondAccruedInterest;
    /**
     * Face value, or {@code null} – initial bond value set by the issuer
     */
    public Double bondFaceValue;
    /**
     * The strike price of an option, or {@code null}. The price at which an option buyer can buy (in a Call
     * option) or sell (in a Put option) the underlying asset, and the option seller is obliged to sell or buy
     * the appropriate amount of the underlying asset
     */
    public Double optionStrike;
    /**
     * Option/warrant sensitivity shows by how many points the price of the option's underlying asset should
     * change so that the price of the option changes by one point, or {@code null}
     */
    public Double optionPriceSensivity;
    /**
     * Liquidity Rate is the share of the asset that can be used for the margin, or {@code null}
     */
    public Double liquidityRate;
    /**
     * Initial margin means the amount in the margin currency required for opening a position with the volume of
     * one lot. It is used for checking a client's assets when he or she enters the market
     */
    public double initialMargin;
    /**
     * The maintenance margin. If it is set, it sets the margin amount in the margin currency of the symbol,
     * charged from one lot. It is used for checking a client's assets when his/her account state changes.
     * If the maintenance margin is equal to 0, the initial margin is used
     */
    public double maintenanceMargin;
    /**
     * Contract size or margin value per one lot of hedged positions (oppositely directed positions of one symbol).
     * Two margin calculation methods are possible for hedged positions. The calculation method is defined by the
     * broker
     */
    public double hedgedMargin;
    /**
     * Calculating hedging margin using the larger leg (Buy or Sell), or {@code null}
     */
    public Boolean hedgedMarginUsesLargerLeg;
    /**
     * Margin currency
     */
    public String marginCurrency;
    /**
     * Contract price calculation mode
     */
    public CalcMode priceCalculationMode;
    /**
     * Base currency
     */
    public String baseCurrency;
    /**
     * Profit currency, or {@code null}
     */
    public String profitCurrency;
    /**
     * Swap calculation model
     */
    public SwapMode swapMode;
    /**
     * Long swap value, or {@code null}
     */
    public Double swapLong;
    /**
     * Short swap value, or {@code null}
     */
    public Double swapShort;
    /**
     * Day of week to charge 3 days swap rollover, or {@code null}
     */
    public DayOfWeek swapRollover3Days;
    /**
     * Allowed order expiration modes
     */
    public List<ExpirationMode> allowedExpirationModes;
    /**
     * Allowed order types
     */
    public List<OrderType> allowedOrderTypes;
    /**
     * If the expirationMode property is set to SYMBOL_EXPIRATION_GTC (good till canceled), the expiration of
     * pending orders, as well as of Stop Loss/Take Profit orders should be additionally set using this enumeration
     */
    public OrderGtcMode orderGTCMode;
    /**
     * Digits after a decimal point
     */
    public int digits;
    /**
     * Path in the symbol tree, or {@code null}
     */
    public String path;
    /**
     * Symbol description
     */
    public String description;
    /**
     * Date of the symbol trade beginning (usually used for futures)
     */
    public IsoTime startTime;
    /**
     * Date of the symbol trade end (usually used for futures)
     */
    public IsoTime expirationTime;
}