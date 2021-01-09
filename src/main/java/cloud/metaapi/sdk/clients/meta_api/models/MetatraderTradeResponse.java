package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Сontains trade command result. For a trade request see {@link MetatraderTrade}
 */
public class MetatraderTradeResponse {
    /**
     * Numeric response code, see
     * https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes and
     * https://book.mql4.com/appendix/errors. Response codes which indicate success are 0, 10008-10010, 10025.
     * The rest codes are errors
     */
    public int numericCode;
    /**
     * String response code, see
     * https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes and
     * https://book.mql4.com/appendix/errors. Response codes which indicate success are ERR_NO_ERROR,
     * TRADE_RETCODE_PLACED, TRADE_RETCODE_DONE, TRADE_RETCODE_DONE_PARTIAL, TRADE_RETCODE_NO_CHANGES.
     * The rest codes are errors.
     */
    public String stringCode;
    /**
     * Human-readable error or result message
     */
    public String message;
    /**
     * Id of the order created or modified by the trade, or {@code null}
     */
    public String orderId;
    /**
     * Id of the position created or modified by the trade, or {@code null}
     */
    public String positionId;
    /**
     * Identifier of an opposite position used for closing by order ORDER_TYPE_CLOSE_BY, or {@code null}
     */
    public String closeByPositionId;
}