package cloud.metaapi.sdk.meta_api.reservoir;

import java.util.Comparator;

public class NumberAvlTreeReservoir<T extends Number> extends AvlTreeReservoir<T> {

  public NumberAvlTreeReservoir(Comparator<T> comparer, int reservoirSize) {
    this(comparer, reservoirSize, null, null);
  }
  
  public NumberAvlTreeReservoir(Comparator<T> comparer, int reservoirSize, Long storagePeriodInMilliseconds) {
    super(comparer, reservoirSize, storagePeriodInMilliseconds, null);
  }
  
  public NumberAvlTreeReservoir(Comparator<T> comparer, int reservoirSize, Long storagePeriodInMilliseconds,
      RandomGenerator randomNumberGen) {
    super(comparer, reservoirSize, storagePeriodInMilliseconds, randomNumberGen);
  }

  public Double getPercentile(double percent) {
    removeOldRecords();
    double index = (size() - 1) * percent / 100;
    int lower = (int) Math.floor(index);
    double fractionPart = index - lower;
    T item = valueTree.at(lower);
    if (item == null) {
      return null;
    }
    double percentile = item.doubleValue();
    if (fractionPart > 0) {
      percentile += fractionPart * (valueTree.at(lower + 1).doubleValue() - valueTree.at(lower).doubleValue());
    }
    return percentile;
  }
}