package cloud.metaapi.sdk.meta_api.reservoir;

import java.util.List;

import cloud.metaapi.sdk.meta_api.reservoir.AvlTreeReservoir.RandomGenerator;

/**
 * Statistical reservoir of a fixed size capable calculating percentiles
 * This reservoir is derived from https://www.npmjs.com/package/reservoir
 * and was integrated with an avl tree (https://www.npmjs.com/package/avl-sorted-list)
 */
public class StatisticalReservoir {
  
  public NumberAvlTreeReservoir<Double> reservoir;
  public int length;
  
  /**
   * Constructs reservoir
   * @param size Reservoir size
   * @param interval reservoir interval in milliseconds, or {@code null}
   */
  public StatisticalReservoir(int size, Long interval) {
    this(size, interval, null);
  }
  
  /**
   * Constructs reservoir
   * @param size Reservoir size
   * @param interval reservoir interval in milliseconds, or {@code null}
   * @param randomNumberGen custom random generator, or {@code null}
   */
  public StatisticalReservoir(int size, Long interval, RandomGenerator randomNumberGen) {
    this.reservoir = new NumberAvlTreeReservoir<>((a, b) -> a < b ? -1 : (a > b ? 1 : 0), size, 
        interval, randomNumberGen);
    this.length = this.reservoir.size();
  }
  
  /**
   * Constructs reservoir
   * @param size Reservoir size
   * @param customRandomGenerator custom random generator, or {@code null}
   * @param interval reservoir interval in milliseconds, or {@code null}
   */
  public StatisticalReservoir(int size, RandomGenerator customRandomGenerator, Long interval) {
    this.reservoir = new NumberAvlTreeReservoir<>((a, b) -> a < b ? -1 : (a > b ? 1 : 0), size, 
        interval, customRandomGenerator);
    this.length = this.reservoir.size();
  }
  
  /**
   * Add element to reservoir
   * @param data data to add
   */
  public void pushMeasurement(double data) {
    reservoir.pushSome(data);
    length = reservoir.size();
  }
  
  /**
   * Calculate percentile statistics for values stored in reservoir.
   * @param p value in percents from 0 to 100
   * @return percentile value
   */
  public double getPercentile(double p) {
    length = reservoir.size();
    return reservoir.getPercentile(p);
  }
  
  /**
   * @return reservoir list
   */
  public List<AvlTreeReservoir<Double>.Node> toList() {
    return reservoir.toList();
  }
  
  /**
   * @return reservoir value list
   */
  public List<Double> toValueList() {
    return reservoir.toValueList();
  }
}