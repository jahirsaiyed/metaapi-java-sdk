package metaapi.cloudsdk.lib.clients.models;

import java.util.Optional;

/**
 * Сontains trade command result. For a trade request see {@link MetatraderTrade}
 */
public class MetatraderTradeResponse {
    /**
     * Error code, see https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes
     */
    public int error;
    /**
     * Machine-readable error description code
     */
    public String description;
    /**
     * Id of the order created or modified by the trade
     */
    public Optional<String> orderId;
    /**
     * Id of the position created or modified by the trade
     */
    public Optional<String> positionId;
}