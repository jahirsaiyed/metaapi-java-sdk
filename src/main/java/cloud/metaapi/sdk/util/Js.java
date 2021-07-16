package cloud.metaapi.sdk.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.JsonNode;

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
   * non-zero value is returned, otherwide the last value is returned
   * @param <T> Type of values
   * @param values Values to select from
   * @return <T> Selected value or {@code null} 
   */
  @SafeVarargs
  public static <T> T or(T... values) {
    T last = null;
    for (T value : values) {
      last = value;
      if (value != null && !value.equals(0) && !value.equals("")) {
        return value;
      }
    }
    return last;
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
   * Creates a hash map from variadic value pairs
   * @param <T> Type of the map keys and values
   * @param pairs Key-Value pairs
   * @return Map created from the key-value pairs
   */
  @SafeVarargs
  public static <T> Map<T, T> asMap(T... pairs) {
    Map<T, T> result = new HashMap<>();
    for (int i = 0; i < pairs.length; i = i + 2) {
      result.put(pairs[i], pairs[i + 1]);
    }
    return result;
  }
  
  /**
   * Creates a json from variadic value pairs
   * @param <T> Type of the json keys and values
   * @param pairs Key-Value pairs
   * @return Json object created from the key-value pairs
   */
  @SafeVarargs
  public static <T> JsonNode asJson(T... pairs) {
    return JsonMapper.getInstance().valueToTree(Js.asMap(pairs));
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
  
  /**
   * Executes thread sleep with error handling
   * @param milliseconds Milliseonds to sleep
   */
  public static void sleep(int milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  
  public static void log(Object... data) {
    String out = "";
    for (Object item : data) {
      out += item.toString() + " ";
    }
    System.out.println(out);
  }
  
  /**
   * Logs specified data and its stack trace
   * @param data Data to print
   */
  public static void trace(Object data) {
    System.out.println(data.toString());
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace(); 
    for (int i = 2; i < stackTrace.length; ++i) {
      System.out.println("  " + stackTrace[i].toString());
    }
  }
}
