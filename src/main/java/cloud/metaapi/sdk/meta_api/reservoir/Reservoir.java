package cloud.metaapi.sdk.meta_api.reservoir;

import java.util.ArrayList;
import java.util.List;

import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * FIFO-like reservoir of a fixed size capable
 * calculating running sums, min/max, average, msdev and stddev
 * msdev and stddev calculation by Naive algorithm
 * (Mean square deviation) msdev = sqrt((∑{i = from 1 to n}(Xi)^2 -(∑{i = from 1 to n}Xi)^2 / N) / N)
 * (Standard deviation) stddev = sqrt((∑{i = from 1 to n}(Xi)^2 -(∑{i = from 1 to n}Xi)^2 / N) / N - 1)
 * link: https://goo.gl/MAEGP2
 */
public class Reservoir {
    
    public List<Statistics> array;
    public int size;
    public Statistics statistics;
    private int interval;
    private long queueEndTime;
    private int firstQueueIndex;
    private Statistics intermediaryRecord; 
    
    public class Statistics {
        public int count;
        public long sum;
        public Long max;
        public Long min;
        public Double average;
        public int sumOfSquares;
        public Double msdev;
        public Double stddev;
    }
    
    /**
     * Constructs Reservoir
     * @param size Reservoir size
     * @param observationIntervalInMS Reservoir observation Interval In ms
     */
    public Reservoir(int size, int observationIntervalInMS) {
        this(size, observationIntervalInMS, null);
    }
    
    /**
     * Constructs Reservoir
     * @param size Reservoir size
     * @param observationIntervalInMS Reservoir observation Interval In ms
     */
    public Reservoir(int size, int observationIntervalInMS, Reservoir object) {
        if (object == null) {
            this.array = new ArrayList<>();
            this.size = size;
            this.interval = observationIntervalInMS / size;
            this.queueEndTime = ServiceProvider.getNow().toEpochMilli();
            this.firstQueueIndex = 0;
            this.intermediaryRecord = null;
            this.statistics = new Statistics() {{
                count = 0;
                sum = 0;
                max = null;
                min = null;
                average = 0.0;
                sumOfSquares = 0;
                msdev = 0.0;
                stddev = 0.0;
            }};
        } else {
            this.array = object.array;
            this.size = object.size;
            this.interval = object.interval;
            this.queueEndTime = object.queueEndTime;
            this.firstQueueIndex = object.firstQueueIndex;
            this.intermediaryRecord = object.intermediaryRecord;
            this.statistics = checkStatisticsOnRestore(object.statistics);
        }
    }
    
    public Statistics checkStatisticsOnRestore(Statistics statistics) {
        if (statistics.count == 0) {
            statistics = new Statistics() {{
                count = 0;
                sum = 0;
                max = null;
                min = null;
                average = null;
                sumOfSquares = 0;
                msdev = null;
                stddev = null;
            }};
        } else if (statistics.count < 2) {
            statistics.msdev = null;
            statistics.stddev = null;
        }
        return statistics;
    }
    
    /**
     * Add element to Reservoir
     * @param data to add
     */
    public void pushMeasurement(long data) {
        updateQueue();
        updateIntermediaryRecord(data);
        updateStatisticsOnAdd(data);
    }
    
    /**
     * Returns Reservoir statistics
     * @return Reservoir statistics
     */
    public Statistics getStatistics() {
        updateQueue();
        return statistics;
    }
    
    private void updateQueue() {
        long intervalsCount = takeTimeIntervalsCount();
        long emptyElementsCount = takeEmptyElementsAddCount();
        if (emptyElementsCount > 0) {
            addRecord(emptyElementsCount);
            queueEndTime += intervalsCount * interval;
        }
    }
    
    private long takeEmptyElementsAddCount() {
        long emptyElementsCount = takeTimeIntervalsCount();
        if (emptyElementsCount > size) {
            emptyElementsCount = size;
        }
        return emptyElementsCount;
    }
    
    private long takeTimeIntervalsCount() {
        long timeNow = ServiceProvider.getNow().toEpochMilli();
        long timeDiff = timeNow - queueEndTime;
        long timeIntervalCount = timeDiff / interval;
        return timeIntervalCount;
    }
    
    private int updateRunningStatisticsOnRemove(long removeCount) {
        int removeElementIndex = firstQueueIndex + 1;
        for (int i = 0; i < removeCount; i++) {
            if (removeElementIndex >= size) {
                removeElementIndex = 0;
            }
            
            Statistics statistics = new Statistics() {{
                count = 0;
                sum = 0;
                max = null;
                min = null;
                average = 0.0;
                sumOfSquares = 0;
            }};
            if (removeElementIndex < array.size()) {
                updateStatisticsOnRemove(array.get(removeElementIndex), removeElementIndex);
                array.set(removeElementIndex, statistics);
            } else {
                updateStatisticsOnRemove(null, removeElementIndex);
                array.add(statistics);
            }
            removeElementIndex++;
        }
        removeElementIndex--;
        if (removeElementIndex < 0) {
            removeElementIndex = size - 1;
        }
        return removeElementIndex;
    }
    
    private void updateStatisticsOnRemove(Statistics removeElement, int removeElementIndex) {
        if (removeElement != null) {
            statistics.count -= removeElement.count;
            statistics.sumOfSquares -= removeElement.sumOfSquares;
            statistics.sum -= removeElement.sum;
            updateStatisticsMinAndMaxOnRemove(removeElement, removeElementIndex);
            if (statistics.count > 0) {
                statistics.average = statistics.sum / (double) statistics.count;
                if (statistics.count > 1) {
                    double difOfSums = calculateDifferenceOfSums(statistics.sumOfSquares, statistics.sum, statistics.count);
                    statistics.msdev = Math.sqrt(difOfSums / statistics.count);
                    statistics.stddev = Math.sqrt(difOfSums / (statistics.count - 1));
                } else {
                    statistics.stddev = null;
                    statistics.msdev = null;
                }
            } else {
                statistics.average = null;
                statistics.stddev = null;
                statistics.msdev = null;
            }
        }
    }
    
    private void updateStatisticsMinAndMaxOnRemove(Statistics removeElement, int removeElementIndex) {
        if (removeElement.max != null && removeElement.max == statistics.max) {
            statistics.max = findMax(removeElementIndex);
        }
        if (removeElement.min != null && removeElement.min == statistics.min) {
            statistics.min = findMin(removeElementIndex);
        }
    }
    
    private void updateStatisticsOnAdd(long data) {
        statistics.count += 1;
        statistics.sum += data;
        updateStatisticsMinAndMaxOnAdd(data);
        statistics.sumOfSquares += Math.pow(data, 2);
        if (statistics.count > 0) {
            statistics.average = statistics.sum / (double) statistics.count;
            double difOfSums = calculateDifferenceOfSums(statistics.sumOfSquares, statistics.sum, statistics.count);
            if (statistics.count > 1) {
                statistics.msdev = Math.sqrt(difOfSums / statistics.count);
                statistics.stddev = Math.sqrt(difOfSums / (statistics.count - 1));
            } else {
                statistics.msdev = null;
                statistics.stddev = null;
            }
        }
    }
    
    private void updateStatisticsMinAndMaxOnAdd(long data) {
        if (statistics.max == null || statistics.max < data) {
            statistics.max = data;
        }
        if (statistics.min == null || statistics.min > data) {
            statistics.min = data;
        }
    }
    
    private void addRecord(long emptyElementsCount) {
        if (intermediaryRecord != null) {
            if (array.size() == firstQueueIndex) {
                array.add(firstQueueIndex, intermediaryRecord);
            } else {
                array.set(firstQueueIndex, intermediaryRecord);
            }
            intermediaryRecord = null;
        }
        int curIndexInArray = updateRunningStatisticsOnRemove(emptyElementsCount);
        firstQueueIndex = curIndexInArray;
    }
    
    private double calculateDifferenceOfSums(int sum1, long sum, int count) {
        double dif = sum1 - Math.pow(sum, 2) / count;
        return dif;
    }
    
    private void updateIntermediaryRecord(long el) {
        if (intermediaryRecord == null) {
            intermediaryRecord = new Statistics() {{
                count = 1;
                sum = el;
                max = el;
                min = el;
                average = (double) el;
                sumOfSquares = (int) Math.pow(el, 2);
            }};
        } else {
            if (intermediaryRecord.max < el) {
                intermediaryRecord.max = el;
            }
            if (intermediaryRecord.min > el) {
                intermediaryRecord.min = el;
            }
            intermediaryRecord.count += 1;
            intermediaryRecord.sum += el;
            intermediaryRecord.sumOfSquares += Math.pow(el, 2);
        }
    }
    
    private Long findMin(int index) {
        Long min = Long.MAX_VALUE;
        for (int i = 0; i < array.size(); ++i) {
            Statistics el = array.get(i);
            if (el != null && el.min != null && el.min < min && i != index) {
                min = el.min;
            }
        }
        if (min == Integer.MAX_VALUE) {
            return intermediaryRecord != null ? intermediaryRecord.min : null;
        }
        return min;
    }
    
    private Long findMax(int index) {
        Long max = Long.MIN_VALUE;
        for (int i = 0; i < array.size(); ++i) {
            Statistics el = array.get(i);
            if (el != null && el.max != null && el.max > max && i != index) {
                max = el.max;
            }
        }
        if (max == Integer.MIN_VALUE) {
            return intermediaryRecord != null ? intermediaryRecord.max : null;
        }
        return max;
    }
}