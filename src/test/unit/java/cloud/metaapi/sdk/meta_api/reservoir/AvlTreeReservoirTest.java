package cloud.metaapi.sdk.meta_api.reservoir;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link AvlTreeReservoir}
 */
class AvlTreeReservoirTest {

  /**
   * Tests {@link AvlTreeReservoir#pushSome(Object)}
   */
  @Test
  void testAccumulatesMeasurements() {
    AvlTreeReservoir<String> res = new AvlTreeReservoir<>((a, b) -> a.compareTo(b), 3);
    res.pushSome("test1");
    res.pushSome("test2");
    assertEquals(2, res.size());
    assertEquals("test1", res.at(0).data);
    assertEquals("test2", res.at(1).data);
  }

  /**
   * Tests {@link AvlTreeReservoir#pushSome(Object)}
   */
  @Test
  void testRandomlyRemovesOldElementsFromReservoir() {
    AvlTreeReservoir<Integer> res = new AvlTreeReservoir<>((a, b) -> a - b, 3);
    res.pushSome(5);
    res.pushSome(4);
    res.pushSome(3);
    res.pushSome(2);
    res.pushSome(1);
    assertEquals(3, res.size());
  }
  
  /**
   * Tests {@link AvlTreeReservoir#pushSome(List)}
   */
  @Test
  void testCalculatesPercentilesWhenReservoirHas5Elements() {
    NumberAvlTreeReservoir<Integer> res = new NumberAvlTreeReservoir<>((a, b) -> a - b, 5);
    res.pushSome(Arrays.asList(5, 1, 3, 2, 4));
    
    assertEquals(4.0052, res.getPercentile(75.13));
    assertEquals(4.004, res.getPercentile(75.1));
    assertEquals(1.002, res.getPercentile(0.05));
    assertEquals(3, res.getPercentile(50));
    assertEquals(4, res.getPercentile(75));
  }
  
  /**
   * Tests {@link AvlTreeReservoir#pushSome(List)}
   */
  @Test
  void testReturnsPercentilesForActualRecordsOnly() throws InterruptedException {
    ServiceProvider.setNowInstantMock(Instant.ofEpochMilli(0));
    NumberAvlTreeReservoir<Integer> res = new NumberAvlTreeReservoir<>((a, b) -> a - b, 15, 60000L);
    for (int item : Arrays.asList(5, 15, 20, 35, 40, 50)) {
      res.pushSome(item);
      ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(10001));
    };
    assertEquals(35, res.getPercentile(50));
  }

  @Test
  void testRunsXAlgorithm() throws InterruptedException {
    ServiceProvider.setNowInstantMock(Instant.ofEpochMilli(0));
    NumberAvlTreeReservoir<Double> res = new NumberAvlTreeReservoir<>((a, b) -> a > b ? 1 
      : a < b ? -1 : 0, 15, 60000L);
    for (int i = 0; i < 1000; i++) {
      double item = Math.random();
      res.pushSome(item);
      ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(1001));
    }
    assertEquals(15, res.size());
    AvlTreeReservoir<Double>.Node max = res.max();
    Assertions.assertThat(max.index).isBetween(990, 999);
    Assertions.assertThat(max.time).isBetween(990000L, 999999L);
    ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(60000));
    res.getPercentile(0);
    assertEquals(0, res.size());
  }
  
  @Test
  void testRunsZAlgorithm() throws InterruptedException {
    ServiceProvider.setNowInstantMock(Instant.ofEpochMilli(0));
    NumberAvlTreeReservoir<Double> res = new NumberAvlTreeReservoir<>((a, b) -> a > b ? 1 
      : a < b ? -1 : 0, 10, 60000L);
    for (int i = 0; i < 3000; i++) {
      double item = Math.random();
      res.pushSome(item);
      ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(100));
    }
    assertEquals(10, res.size());
  }
}