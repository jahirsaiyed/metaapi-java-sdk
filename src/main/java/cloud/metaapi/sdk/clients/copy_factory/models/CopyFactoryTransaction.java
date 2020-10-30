package cloud.metaapi.sdk.clients.copy_factory.models;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDeal.DealType;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * CopyFactory transaction
 */
public class CopyFactoryTransaction {
    
    /**
     * Transaction id
     */
    public String id;
    /**
     * Transaction type
     */
    public DealType type;
    /**
     * Transaction time
     */
    public IsoTime time;
    /**
     * CopyFactory account id
     */
    public String accountId;
    /**
     * Optional symbol traded, or {@code null}
     */
    public String symbol;
    /**
     * Strategy subscriber
     */
    public CopyFactorySubscriberOrProvider subscriber;
    /**
     * Demo account flag
     */
    public boolean demo;
    /**
     * Strategy provider
     */
    public CopyFactorySubscriberOrProvider provider;
    /**
     * Strategy
     */
    public CopyFactoryStrategyIdAndName strategy;
    /**
     * Source position id, or {@code null}
     */
    public String positionId;
    /**
     * High-water mark strategy balance improvement
     */
    public double improvement;
    /**
     * Provider commission
     */
    public double providerCommission;
    /**
     * Platform commission
     */
    public double platformCommission;
    /**
     * Trade volume, or {@code null}
     */
    public Double quantity;
    /**
     * Trade lot price, or {@code null}
     */
    public Double lotPrice;
    /**
     * Trade tick price, or {@code null}
     */
    public Double tickPrice;
    /**
     * Trade amount, or {@code null}
     */
    public Double amount;
    /**
     * Trade commission or {@code null}
     */
    public Double commission;
    /**
     * Trade swap
     */
    public Double swap;
    /**
     * Trade profit
     */
    public Double profit;
    /**
     * Trade copying metrics such as slippage and latencies, or {@code null}. Measured selectively for copied trades
     */
    public CopyFactoryTransactionMetrics metrics;
}