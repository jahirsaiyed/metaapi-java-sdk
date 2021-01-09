package cloud.metaapi.sdk.meta_api.reservoir;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Statistical reservoir of a fixed size capable calculating percentile
 * This reservoir taken from https://www.npmjs.com/package/reservoir
 * This reservoir has been modified by avl tree (https://www.npmjs.com/package/avl-sorted-list)
 * Array which contains all data was removed and instead of it add tree
 */
public class AvlTreeReservoir<T> extends AvlTree<AvlTreeReservoir<T>.Node> {

  public class Node {
    public int index;
    public long time;
    public T data;
  }
  
  public static int switchToAlgorithmZConstant = 22;
  public static String debug = "none";
  
  @FunctionalInterface
  public static interface RandomGenerator {
    double random();
  }
  
  @FunctionalInterface
  public static interface Algorithm {
    int execute();
  }
  
  private Long interval;
  private RandomGenerator rng;
  private int reservoirSize;
  private int totalItemCount = 0;
  private int lastDeletedIndex = -1;
  private int numToSkip = -1;
  private Algorithm currentAlgorithm;
  private int algorithmXCount = 0;
  private int switchThreshold;
  private Integer evictNext = null;
  private int initialIndex = 0;
  private double W;
  protected AvlTree<T> valueTree;
  
  /**
   * "Algorithm R"
   * Selects random elements from an unknown-length input.
   * Has a time-complexity of: O(N)
   * Number of random numbers required:
   * N - n
   * Where:
   * n = the size of the reservoir
   * N = the size of the input
   */
  private Algorithm algorithmR = () -> {
    int localItemCount = totalItemCount + 1;
    int randomValue = (int) Math.floor(rng.random() * localItemCount);
    int toSkip = 0;
    
    while (randomValue >= reservoirSize) {
      toSkip++;
      localItemCount++;
      randomValue = (int) Math.floor(rng.random() * localItemCount);
    }
    evictNext = randomValue;
    return toSkip;
  };

  /**
   * "Algorithm Z"
   * Selects random elements from an unknown-length input.
   * Has a time-complexity of:
   *  O(n(1 + log (N / n)))
   * Number of random numbers required:
   *  2 * n * ln( N / n )
   * Where:
   *  n = the size of the reservoir
   *  N = the size of the input
   */
  private Algorithm algorithmZ = () -> {
    int term = totalItemCount - reservoirSize + 1;
    int denom, numer, numer_lim, toSkip = 0;
    
    while (true) {
      double randomValue = rng.random();
      double x = totalItemCount * (W - 1);
      toSkip = (int) Math.floor(x);
      
      double subterm = (totalItemCount + 1) / (double) term;
      subterm *= subterm;
      int termSkip = term + toSkip;
      double lhs = Math.exp(Math.log(((randomValue * subterm) * termSkip) / (totalItemCount + x)) / reservoirSize);
      double rhs = (((totalItemCount + x) / (double) termSkip) * term) / totalItemCount;
      
      if (lhs <= rhs) {
        W = rhs / lhs;
        break;
      }
      
      double y = (((randomValue * (totalItemCount + 1)) / term) * (totalItemCount + toSkip + 1)) / (totalItemCount + x);
      
      if (reservoirSize < toSkip) {
        denom = totalItemCount;
        numer_lim = term + toSkip;
      } else {
        denom = totalItemCount - reservoirSize + toSkip;
        numer_lim = totalItemCount + 1;
      }
      
      for (numer = totalItemCount + toSkip; numer >= numer_lim; numer--) {
        y = (y * numer) / denom;
        denom--;
      }

      W = Math.exp(-Math.log(rng.random()) / reservoirSize);
      
      if (Math.exp(Math.log(y) / reservoirSize) <= (totalItemCount + x) / (double) totalItemCount) {
        break;
      }
    }
    return toSkip;
  };
  
  /**
   * "Algorithm X"
   * Selects random elements from an unknown-length input.
   * Has a time-complexity of: O(N)
   * Number of random numbers required:
   *  2 * n * ln( N / n )
   * Where:
   *  n = the size of the reservoir
   *  N = the size of the input
   */
  private Algorithm algorithmX = () -> {
    int localItemCount = totalItemCount;
    double randomValue = rng.random();
    int toSkip = 0;
    double quotient;
    
    if (totalItemCount <= switchThreshold) {
      localItemCount++;
      algorithmXCount++;
      quotient = (double) algorithmXCount / localItemCount;
      
      while (quotient > randomValue) {
        toSkip++;
        localItemCount++;
        algorithmXCount++;
        quotient = (quotient * algorithmXCount) / localItemCount;
      }
      return toSkip;
    } else {
      currentAlgorithm = algorithmZ;
      return currentAlgorithm.execute();
    }
  };
  
  public AvlTreeReservoir(Comparator<T> comparer, int reservoirSize) {
    this(comparer, reservoirSize, null, null);
  }
  
  public AvlTreeReservoir(Comparator<T> comparer, int reservoirSize, Long storagePeriodInMilliseconds) {
    this(comparer, reservoirSize, storagePeriodInMilliseconds, null);
  }
  
  public AvlTreeReservoir(Comparator<T> comparer, int reservoirSize, Long storagePeriodInMilliseconds,
      RandomGenerator randomNumberGen) {
    super((a, b) -> a.index - b.index);
    this.interval = storagePeriodInMilliseconds;
    this.rng = randomNumberGen != null ? randomNumberGen : () -> Math.random();
    this.reservoirSize = reservoirSize != 0 ? reservoirSize : 1;
    this.currentAlgorithm = algorithmX;
    this.switchThreshold = switchToAlgorithmZConstant * this.reservoirSize;
    
    if (debug.equals("R")) {
      currentAlgorithm = algorithmR;
    } else if (debug.equals("X")) {
      switchThreshold = Integer.MAX_VALUE;
    } else if (debug.equals("Z")) {
      currentAlgorithm = algorithmZ;
    }
    
    W = Math.exp(-Math.log(rng.random()) / reservoirSize);
    valueTree = new AvlTree<>(comparer);
  }
  
  public void removeOldRecords() {
    while (interval != null) {
      Node element = at(0);
      if (element != null && Date.from(ServiceProvider.getNow()).getTime() > element.time + interval) {
        removeAt(0);
        int deletedIndexDiff = element.index - lastDeletedIndex;
        lastDeletedIndex = element.index;
        valueTree.remove(element.data);
        totalItemCount -= deletedIndexDiff;
        algorithmXCount = Math.max(0, algorithmXCount - deletedIndexDiff);
      } else {
        break;
      }
    }
  }
  
  public int pushSome(T value) {
    return pushSome(Arrays.asList(value));
  }
  
  public int pushSome(List<T> values) {
    int len = Math.min(size(), reservoirSize);
    for (T arg : values) {
      removeOldRecords();
      Node value = new Node() {{ index = initialIndex; time = Date.from(ServiceProvider.getNow()).getTime(); data = arg; }};
      addSample(value);
      initialIndex++;
    }
    return len;
  }
  
  public int fromObject(Node value) {
    return fromObject(Arrays.asList(value));
  }
  
  public int fromObject(List<Node> values) {
    int len = Math.min(size(), reservoirSize);
    for (Node arg : values) {
      removeOldRecords();
      Node value = new Node() {{ index = arg.index; time = arg.time; data = arg.data; }};
      addSample(value);
      initialIndex++;
    }
    return len;
  }
  
  public List<T> toValueList() {
    return toList().stream().map(value -> value.data).collect(Collectors.toList());
  }
  
  private void addSample(Node sample) {
    if (size() < reservoirSize) {
      insert(sample);
      valueTree.insert(sample.data);
    } else {
      if (numToSkip < 0) {
        numToSkip = currentAlgorithm.execute();
      }
      if (numToSkip == 0) {
        replaceRandomSample(sample, this);
      }
      numToSkip--;
    }
    totalItemCount++;
  }
  
  private void replaceRandomSample(Node sample, AvlTreeReservoir<T> reservoir) {
    int randomIndex;
    if (evictNext != null) {
      randomIndex = evictNext;
      evictNext = null;
    } else {
      randomIndex = (int) Math.floor(rng.random() * reservoirSize);
    }
    Node value = reservoir.at(randomIndex);
    reservoir.removeAt(randomIndex);
    valueTree.remove(value.data);
    valueTree.insert(sample.data);
    reservoir.insert(sample);
  }
}