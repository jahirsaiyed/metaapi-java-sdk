package cloud.metaapi.sdk.clients.models;

import java.util.Optional;

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
     * Deal id (ticket number)
     */
    public String id;
    /**
     * Deal type
     */
    public DealType type;
    /**
     * Deal entry type
     */
    public Optional<DealEntryType> entryType = Optional.empty();
    /**
     * Optional symbol deal relates to
     */
    public Optional<String> symbol = Optional.empty();
    /**
     * Optional deal magic number, identifies the EA which initiated the deal
     */
    public Optional<Integer> magic = Optional.empty();
    /**
     * Time the deal was conducted at
     */
    public IsoTime time;
    /**
     * Optional deal volume
     */
    public Optional<Double> volume = Optional.empty();
    /**
     * Optional, the price the deal was conducted at
     */
    public Optional<Double> price = Optional.empty();
    /**
     * Optional deal commission
     */
    public Optional<Double> commission = Optional.empty();
    /**
     * Optional deal swap
     */
    public Optional<Double> swap = Optional.empty();
    /**
     * Deal profit
     */
    public double profit;
    /**
     * Optional id of position the deal relates to
     */
    public Optional<String> positionId = Optional.empty();
    /**
     * Optional id of order the deal relates to
     */
    public Optional<String> orderId = Optional.empty();
    /**
     * Optional deal comment. The sum of the line lengths of the comment and the clientId must be less than
     * or equal to 27. For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> comment = Optional.empty();
    /**
     * Optional deal original comment (present if possible to restore original comment from history)
     */
    public Optional<String> originalComment = Optional.empty();
    /**
     * Optional client-assigned id. The id value can be assigned when submitting a trade and will be present
     * on position, history orders and history deals related to the trade. You can use this field to bind your
     * trades to objects in your application and then track trade progress. The sum of the line lengths of the
     * comment and the clientId must be less than or equal to 27. For more information see 
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> clientId = Optional.empty();
    /**
     * Platform id (mt4 or mt5)
     */
    public String platform;
    /**
     * Optional flag indicating that deal client id and original comment was not identified yet and will be
     * updated in a future synchronization packet
     */
    public Optional<Boolean> updatePending = Optional.empty();
}