package cloud.metaapi.sdk.util;

import java.util.List;
import java.util.function.BiFunction;

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
}
