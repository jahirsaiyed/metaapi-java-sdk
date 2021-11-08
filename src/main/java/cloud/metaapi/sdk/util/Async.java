package cloud.metaapi.sdk.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Helper class for managing asynchronous operations
 */
public class Async {
  
  private static ExecutorService executor = null;
  
  /**
   * Returns executor for completable futures to create with
   * @return executor service
   */
  public static ExecutorService getExecutor() {
    if (executor == null) {
      executor = Executors.newCachedThreadPool();
    }
    return executor;
  }
  
  /**
   * Shutdown used executor service
   */
  public static void shutdownExecutor() {
    if (executor != null) {
      executor.shutdown();
      executor = null;
    }
  }
  
  /**
   * Runs asynchronous task with own executor
   * @param runnable asynchronous task
   * @return completable future
   */
  public static CompletableFuture<Void> run(Runnable runnable) {
    return CompletableFuture.runAsync(runnable, getExecutor());
  }
  
  /**
   * Runs asynchonous supplier with own executor
   * @param <U> supplier return type
   * @param supplier asynchronous supplier
   * @return completable future
   */
  public static <U> CompletableFuture<U> supply(Supplier<U> supplier) {
    return CompletableFuture.supplyAsync(supplier, getExecutor());
  }
}
