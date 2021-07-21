package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * Stop options
 */
public class StopOptions {
  
  /**
   * Stop units. ABSOLUTE_PRICE means the that the value of value field is a final stop value.
   * RELATIVE_* means that the value field value contains relative stop expressed either in price,
   * points, account currency or balance percentage
   */
  public enum StopUnits { ABSOLUTE_PRICE, RELATIVE_PRICE, RELATIVE_POINTS, RELATIVE_CURRENCY,
    RELATIVE_BALANCE_PERCENTAGE }
  
  /**
   * Stop (SL or TP) value
   */
  public double value;
  /**
   * Stop unitsm or {@code null}. Default is ABSOLUTE_PRICE
   */
  public StopUnits units;
}