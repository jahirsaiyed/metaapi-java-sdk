package cloud.metaapi.sdk.clients.models;

import java.util.Optional;

/**
 * Сontains trade command result. For a trade request see {@link MetatraderTrade}
 */
public class MetatraderTradeResponse {
    /**
     * Numeric result code, see 
     * https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes and
     * https://book.mql4.com/appendix/errors
     */
    public int numericCode;
    /**
     * Machine-readable string result code, see 
     * https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes and 
     * https://book.mql4.com/appendix/errors
     */
    public String stringCode;
    /**
     * Human-readable error or result message
     */
    public String message;
    /**
     * Id of the order created or modified by the trade
     */
    public Optional<String> orderId = Optional.empty();
    /**
     * Id of the position created or modified by the trade
     */
    public Optional<String> positionId = Optional.empty();
}