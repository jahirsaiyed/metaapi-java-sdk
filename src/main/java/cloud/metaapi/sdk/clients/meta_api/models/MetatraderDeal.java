package cloud.metaapi.sdk.clients.meta_api.models;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * MetaTrader deal
 */
public class MetatraderDeal {
    
    /**
     * Deal type. See https://www.mql5.com/en/docs/constants/tradingconstants/dealproperties#enum_deal_type
     */
    public enum DealType {
        DEAL_TYPE_BUY, DEAL_TYPE_SELL, DEAL_TYPE_BALANCE, DEAL_TYPE_CREDIT, DEAL_TYPE_CHARGE,
        DEAL_TYPE_CORRECTION, DEAL_TYPE_BONUS, DEAL_TYPE_COMMISSION, DEAL_TYPE_COMMISSION_DAILY, 
        DEAL_TYPE_COMMISSION_MONTHLY, DEAL_TYPE_COMMISSION_AGENT_DAILY, DEAL_TYPE_COMMISSION_AGENT_MONTHLY, 
        DEAL_TYPE_INTEREST, DEAL_TYPE_BUY_CANCELED, DEAL_TYPE_SELL_CANCELED, DEAL_DIVIDEND, DEAL_DIVIDEND_FRANKED,
        DEAL_TAX
    }
    
    /**
     * Deal entry type. See https://www.mql5.com/en/docs/constants/tradingconstants/dealproperties#enum_deal_entry
     */
    public enum DealEntryType { DEAL_ENTRY_IN, DEAL_ENTRY_OUT, DEAL_ENTRY_INOUT, DEAL_ENTRY_OUT_BY }
    
    /**
     * Deal execution reason. See https://www.mql5.com/en/docs/constants/tradingconstants/dealproperties#enum_deal_reason
     *
     */
    public enum DealReason {
        DEAL_REASON_CLIENT, DEAL_REASON_MOBILE, DEAL_REASON_WEB, DEAL_REASON_EXPERT, DEAL_REASON_SL, DEAL_REASON_TP,
        DEAL_REASON_SO, DEAL_REASON_ROLLOVER, DEAL_REASON_VMARGIN, DEAL_REASON_SPLIT, DEAL_REASON_UNKNOWN
    }
    
    /**
     * Deal id (ticket number)
     */
    public String id;
    /**
     * Deal type
     */
    public DealType type;
    /**
     * Deal entry type or {@code null}
     */
    public DealEntryType entryType;
    /**
     * Symbol deal relates to or {@code null}
     */
    public String symbol;
    /**
     * Deal magic number or {@code null}, identifies the EA which initiated the deal
     */
    public Integer magic;
    /**
     * Time the deal was conducted at
     */
    public IsoTime time;
    /**
     * Deal volume or {@code null}
     */
    public Double volume;
    /**
     * The price the deal was conducted at or {@code null}
     */
    public Double price;
    /**
     * Deal commission or {@code null}
     */
    public Double commission;
    /**
     * Deal swap or {@code null}
     */
    public Double swap;
    /**
     * Deal profit
     */
    public double profit;
    /**
     * Id of position the deal relates to or {@code null}
     */
    public String positionId;
    /**
     * Id of order the deal relates to or {@code null}
     */
    public String orderId;
    /**
     * Deal comment or {@code null}. The sum of the line lengths of the comment and the clientId must be less than
     * or equal to 26. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String comment;
    /**
     * Deal original comment or {@code null} (present if possible to restore original comment from history)
     */
    public String originalComment;
    /**
     * Client-assigned id or {@code null}. The id value can be assigned when submitting a trade and will be present
     * on position, history orders and history deals related to the trade. You can use this field to bind your
     * trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 26. For more information see 
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public String clientId;
    /**
     * Platform id (mt4 or mt5)
     */
    public String platform;
    /**
     * Flag indicating that deal client id and original comment was not identified yet and will be
     * updated in a future synchronization packet, or {@code null}
     */
    public Boolean updatePending;
    /**
     * Optional deal execution reason or {@code null}
     */
    public DealReason reason;
}