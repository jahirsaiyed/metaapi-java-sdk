package cloud.metaapi.sdk.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;

/**
 * JavaScript-like helper functions
 */
public class Js {
  
  /**
   * JavaScript-like reduce
   * @param <U> Type of the accumulated value
   * @param <T> Type of list elements to reduce from
   * @return <U> Accumulated value
   */
  public static <U, T> U reduce(List<T> list, BiFunction<U, T, U> reducer, U initialValue) {
    U result = initialValue;
    for (T item : list) {
      result = reducer.apply(result, item);
    }
    return result;
  }
  
  /**
   * Alternative to JavaScript a || b || c ... construction, where the first non-null and
   * non-zero value is returned
   * @param <T> Type of values
   * @param values Values to select from
   * @return <T> Selected value or {@code null} 
   */
  @SafeVarargs
  public static <T> T or(T... values) {
    for (T value : values) {
      if (value != null && !value.equals(0)) {
        return value;
      }
    }
    return null;
  }
  
  /**
   * Creates a hash map from variadic value pairs
   * @param <T> Type of the map keys
   * @param <U> Type of the map values
   * @param pairs Key-Value pairs
   * @return Map created from the key-value pairs
   */
  @SafeVarargs
  public static <T, U> Map<T, U> asMap(Pair<T, U>... pairs) {
    Map<T, U> result = new HashMap<>();
    for (Pair<T, U> pair : pairs) {
      result.put(pair.getLeft(), pair.getRight());
    }
    return result;
  }
  
  /**
   * Setups timeout with JavaScript-like syntax
   * @param callback Callback to run
   * @param msInterval Timeout in milliseconds
   * @return Timeout handle
   */
  public static Timer setTimeout(Runnable callback, int msInterval) {
    Timer result = new Timer();
    result.schedule(new TimerTask() {
      @Override
      public void run() {
        callback.run();
      }
    }, msInterval);
    return result;
  }
  
  /**
   * Setups interval with JavaScript-like syntax
   * @param callback Callback to run
   * @param msInterval Interval in milliseconds
   * @return Interval handle
   */
  public static Timer setInterval(Runnable callback, int msInterval) {
    Timer result = new Timer();
    result.schedule(new TimerTask() {
      @Override
      public void run() {
        callback.run();
      }
    }, msInterval, msInterval);
    return result;
  }
}
