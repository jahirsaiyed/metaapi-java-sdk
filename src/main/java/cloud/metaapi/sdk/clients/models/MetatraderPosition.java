package cloud.metaapi.sdk.clients.models;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * MetaTrader position
 */
@JsonIgnoreProperties(value = { "platform" })
public class MetatraderPosition {
    
    /**
     * Position type
     */
    public enum PositionType { POSITION_TYPE_BUY, POSITION_TYPE_SELL }
    
    /**
     * Position id (ticket number)
     */
    public String id;
    /**
     * Position type
     */
    public PositionType type;
    /**
     * Position symbol
     */
    public String symbol;
    /**
     * Position magic number, identifies the EA which opened the position
     */
    public int magic;
    /**
     * Time position was opened at
     */
    public IsoTime time;
    /**
     * Last position modification time
     */
    public IsoTime updateTime;
    /**
     * Position open price
     */
    public double openPrice;
    /**
     * Current price
     */
    public double currentPrice;
    /**
     * Current tick value
     */
    public double currentTickValue;
    /**
     * Optional position stop loss price
     */
    public Optional<Double> stopLoss = Optional.empty();
    /**
     * Optional position take profit price
     */
    public Optional<Double> takeProfit = Optional.empty();
    /**
     * Position volume
     */
    public double volume;
    /**
     * Position cumulative swap
     */
    public double swap;
    /**
     * Position cumulative profit
     */
    public double profit;
    /**
     * Optional position comment. The sum of the line lengths of the comment and 
     * the clientId must be less than or equal to 27. For more information see
     * https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> comment = Optional.empty();
    /**
     * Optional client-assigned id. The id value can be assigned when submitting a trade and
     * will be present on position, history orders and history deals related to the trade. 
     * You can use this field to bind your trades to objects in your application and then track trade progress.
     * The sum of the line lengths of the comment and the clientId must be less than or equal to 27. 
     * For more information see https://metaapi.cloud/docs/client/clientIdUsage/
     */
    public Optional<String> clientId = Optional.empty();
    /**
     * Profit of the part of the position which is not yet closed, including swap
     */
    public Optional<Double> unrealizedProfit = Optional.empty();
    /**
     * Profit of the already closed part, including commissions and swap
     */
    public Optional<Double> realizedProfit = Optional.empty();
    /**
     * Optional position commission
     */
    public Optional<Double> commission = Optional.empty();
}