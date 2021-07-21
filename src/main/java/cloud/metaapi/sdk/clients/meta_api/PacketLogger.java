package cloud.metaapi.sdk.clients.meta_api;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletionException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.OptionsValidator;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.util.JsonMapper;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * A class which records packets into log files
 */
public class PacketLogger {

  private static SimpleDateFormat longDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static SimpleDateFormat shortDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private static Logger logger = LogManager.getLogger(PacketLogger.class);
  private int fileNumberLimit;
  private int logFileSizeInHours;
  private boolean compressSpecifications;
  private boolean compressPrices;
  private Map<String, Map<Integer, PreviousPrice>> previousPrices = new HashMap<>();
  private Map<String, Map<Integer, JsonNode>> lastSNPacket = new HashMap<>();
  private Map<String, WriteQueueItem> writeQueue = new HashMap<>();
  private Timer recordInterval;
  private Timer deleteOldLogsInterval;
  private String root;
  
  /**
   * Packet logger options
   */
  public static class LoggerOptions {
    /**
     * Whether enable packet logger
     */
    public boolean enabled = false;
    /**
     * Maximum amount of files per account, default value is 12
     */
    int fileNumberLimit = 12;
    /**
     * Amount of logged hours per account file, default value is 4
     */
    int logFileSizeInHours = 4;
    /**
     * Whether to compress specifications packets, default value is true
     */
    boolean compressSpecifications = true;
    /**
     * Whether to compress specifications packets, default value is true
     */
    boolean compressPrices = true;
  }
  
  /**
   * Log message
   */
  public static class LogMessage {
    public Date date;
    public String message;
  }
  
  private static class PreviousPrice {
    JsonNode first;
    JsonNode last;
  }
  
  private static class WriteQueueItem {
    public boolean isWriting;
    public List<String> queue;
  }
  
  /**
   * Constructs the class
   * @param opts packet logger options
   * @throws IOException if log directory cannot be created
   * @throws ValidationException if specified opts are invalid
   */
  public PacketLogger(LoggerOptions opts) throws IOException, ValidationException {
    OptionsValidator validator = new OptionsValidator();
    validator.validateNonZeroInt(opts.fileNumberLimit, "packetLogger.fileNumberLimit");
    validator.validateNonZeroInt(opts.logFileSizeInHours, "packetLogger.logFileSizeInHours");
    
    this.fileNumberLimit = opts.fileNumberLimit;
    this.logFileSizeInHours = opts.logFileSizeInHours;
    this.compressSpecifications = opts.compressSpecifications;
    this.compressPrices = opts.compressPrices;
    this.root = "./.metaapi/logs";
    Files.createDirectories(FileSystems.getDefault().getPath(this.root));
  }
  
  /**
   * Processes packets and pushes them into save queue
   * @param packet packet to log
   */
  public void logPacket(JsonNode packet) {
    int instanceIndex = packet.has("instanceIndex") ? packet.get("instanceIndex").asInt() : 0;
    String packetAccountId = packet.get("accountId").asText();
    String packetType = packet.get("type").asText();
    Integer packetSequenceNumber = packet.has("sequenceNumber") ? packet.get("sequenceNumber").asInt() : null;
    if (!writeQueue.containsKey(packetAccountId)) {
      writeQueue.put(packetAccountId, new WriteQueueItem() {{
        isWriting = false;
        queue = new ArrayList<>();
      }});
    }
    if (packetType.equals("status")) {
      return;
    }
    if (!lastSNPacket.containsKey(packetAccountId)) {
      lastSNPacket.put(packetAccountId, new HashMap<>());
    }
    if (packetType.equals("keepalive") || packetType.equals("noop")) {
      lastSNPacket.get(packetAccountId).put(instanceIndex, packet);
      return;
    }
    List<String> queue = writeQueue.get(packetAccountId).queue;
    if (!previousPrices.containsKey(packetAccountId)) {
      previousPrices.put(packetAccountId, new HashMap<>());
    }
    PreviousPrice prevPrice = previousPrices.get(packetAccountId).get(instanceIndex);
    if (!packetType.equals("prices")) {
      if (prevPrice != null) {
        recordPrices(packetAccountId, instanceIndex);
      }
      if (packetType.equals("specifications") && compressSpecifications) {
        ObjectNode queueItem = JsonMapper.getInstance().createObjectNode();
        queueItem.put("type", packetType);
        queueItem.put("sequenceNumber", packetSequenceNumber);
        queueItem.set("sequenceTimestamp", packet.get("sequenceTimestamp"));
        queueItem.put("instanceIndex", instanceIndex);
        queue.add(queueItem.toString());
      } else {
        queue.add(packet.toString());
      }
    } else {
      if (!compressPrices) {
        queue.add(packet.toString());
      } else {
        if (prevPrice != null) {
          List<Integer> validSequenceNumbers = new ArrayList<>();
          validSequenceNumbers.add(prevPrice.last.has("sequenceNumber")
            ? prevPrice.last.get("sequenceNumber").asInt() : null);
          if (validSequenceNumbers.get(0) != null) {
            validSequenceNumbers.add(validSequenceNumbers.get(0) + 1);
          }
          if (lastSNPacket.get(packetAccountId).containsKey(instanceIndex)) {
            JsonNode lastKeepAlivePacket = lastSNPacket.get(packetAccountId).get(instanceIndex); 
            if (lastKeepAlivePacket.has("sequenceNumber")) {
              validSequenceNumbers.add(lastKeepAlivePacket.get("sequenceNumber").asInt() + 1);
            }
          }
          if (validSequenceNumbers.indexOf(packetSequenceNumber) == -1) {
            recordPrices(packetAccountId, instanceIndex);
            ensurePreviousPriceObject(packetAccountId);
            previousPrices.get(packetAccountId).put(instanceIndex, new PreviousPrice() {{
              first = packet; last = packet; }});
            queue.add(packet.toString());
          } else {
            prevPrice.last = packet;
          }
        } else {
          ensurePreviousPriceObject(packetAccountId);
          previousPrices.get(packetAccountId).put(instanceIndex, new PreviousPrice() {{
            first = packet; last = packet; }});
          queue.add(packet.toString());
        }
      }
    }
  }
  
  /**
   * Returns log messages
   * @param accountId account id 
   * @return log messages
   */
  public List<LogMessage> readLogs(String accountId) {
    return readLogs(accountId, null, null);
  }
  
  /**
   * Returns log messages within date bounds as an array of objects
   * @param accountId account id 
   * @param dateAfter date to get logs after, or {@code null}
   * @param dateBefore date to get logs before, or {@code null}
   * @return log messages
   */
  public List<LogMessage> readLogs(String accountId, Date dateAfter, Date dateBefore) {
    File rootFolder = new File(root);
    File[] folders = rootFolder.listFiles();
    List<LogMessage> packets = new ArrayList<>();
    for (File folder : folders) {
      Path filePath = FileSystems.getDefault().getPath(root, folder.getName(), accountId + ".log");
      if (Files.exists(filePath)) {
        try {
          List<String> contents = Files.readAllLines(filePath);
          List<LogMessage> messages = new ArrayList<>();
          contents.removeIf(line -> line.length() == 0);
          for (String line : contents) {
            messages.add(new LogMessage() {{
              date = longDateFormat.parse(line.substring(1, 24));
              message = line.substring(26);
            }});
          }
          if (dateAfter != null) {
            messages.removeIf(message -> message.date.compareTo(dateAfter) != 1);
          }
          if (dateBefore != null) {
            messages.removeIf(message -> message.date.compareTo(dateBefore) != -1);
          }
          packets.addAll(messages);
        } catch (Throwable e) {
          throw new CompletionException(e);
        }
      }
    }
    return packets;
  }
  
  /**
   * Returns path for account log file
   * @param accountId account id
   * @return file path
   * @throws IOException if failed to create file directory
   */
  public String getFilePath(String accountId) throws IOException {
    Date now = Date.from(ServiceProvider.getNow());
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(now);
    int fileIndex = calendar.get(Calendar.HOUR_OF_DAY) / logFileSizeInHours;
    String folderName = shortDateFormat.format(now) + "-" + (fileIndex > 9 ? fileIndex : "0" + fileIndex);
    Files.createDirectories(FileSystems.getDefault().getPath(root, folderName));
    return root + "/" + folderName + "/" + accountId + ".log";
  }
  
  /**
   * Initializes the packet logger
   */
  public void start() {
    previousPrices.clear();
    if (recordInterval == null) {
      PacketLogger self = this;
      recordInterval = new Timer();
      recordInterval.schedule(new TimerTask() {
        @Override
        public void run() {
          self.appendLogs();
        }
      }, 1000, 1000);
      deleteOldLogsInterval = new Timer();
      deleteOldLogsInterval.schedule(new TimerTask() {
        @Override
        public void run() {
          try {
            self.deleteOldData();
          } catch (IOException e) {
            logger.error("Failed to delete old data", e);
          }
        }
      }, 10000, 10000);
    }
  }
  
  /**
   * Deinitializes the packet logger
   */
  public void stop() {
    if (recordInterval != null) {
      recordInterval.cancel();
      recordInterval = null;
      deleteOldLogsInterval.cancel();
      deleteOldLogsInterval = null;
    }
  }
  
  /**
   * Records price packet messages to log files
   * @param accountId account id
   */
  private void recordPrices(String accountId, int instanceNumber) {
    PreviousPrice prevPrice = previousPrices.get(accountId).get(instanceNumber);
    if (prevPrice == null) {
      prevPrice = new PreviousPrice() {{
        first = JsonMapper.getInstance().createObjectNode();
        last = JsonMapper.getInstance().createObjectNode();
      }};
    }
    List<String> queue = writeQueue.get(accountId).queue;
    previousPrices.get(accountId).remove(instanceNumber);
    if (previousPrices.get(accountId).size() == 0) {
      previousPrices.remove(accountId);
    }
    int firstSequenceNumber = prevPrice.first.get("sequenceNumber").asInt();
    int lastSequenceNumber = prevPrice.last.get("sequenceNumber").asInt();
    if (firstSequenceNumber != lastSequenceNumber) {
      queue.add(prevPrice.last.toString());
      queue.add("Recorded price packets " + firstSequenceNumber + "-" + lastSequenceNumber
        + ", instanceIndex: " + instanceNumber);
    }
  }
  
  /**
   * Writes logs to files
   */
  private void appendLogs() {
    writeQueue.keySet().forEach((key) -> {
      WriteQueueItem queue = writeQueue.get(key);
      if (!queue.isWriting && queue.queue.size() != 0) {
        queue.isWriting = true;
        try {
          String filePath = getFilePath(key);
          String writeString = "";
          for (String line : queue.queue) {
            writeString += "[" + longDateFormat.format(Date.from(ServiceProvider.getNow())) + "] "
              + line + "\r\n";
          }
          queue.queue.clear();
          FileUtils.writeByteArrayToFile(new File(filePath),
            writeString.getBytes(StandardCharsets.UTF_8), true);
        } catch (Throwable e) {
          logger.info("Error writing log", e);
        }
        queue.isWriting = false;
      }
    });
  }
  
  /**
   * Deletes folders when the folder limit is exceeded
   * @throws IOException if failed to delete an old data directory
   */
  private void deleteOldData() throws IOException {
    File rootFolder = new File(root);
    List<String> contents = Arrays.asList(rootFolder.list());
    Collections.sort(contents, Collator.getInstance());
    if (contents.size() > fileNumberLimit) {
      for (String folder : contents.subList(0, contents.size() - fileNumberLimit)) {
        FileUtils.deleteDirectory(new File(rootFolder + "/" + folder));
      }
    }
  }
  
  private void ensurePreviousPriceObject(String accountId) {
    if (!previousPrices.containsKey(accountId)) {
      previousPrices.put(accountId, new HashMap<>());
    }
  }
}