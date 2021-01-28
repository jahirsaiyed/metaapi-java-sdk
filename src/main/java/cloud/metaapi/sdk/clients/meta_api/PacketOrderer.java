package cloud.metaapi.sdk.clients.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Class which orders the synchronization packets
 */
public class PacketOrderer {
   
  /**
   * Class for storing packet in wait list
   */
  protected class Packet {
    /**
     * Instance id
     */
    public String instanceId;
    /**
     * Account id from packet
     */
    public String accountId;
    /**
     * Instance index
     */
    public int instanceIndex;
    /**
     * Sequence number
     */
    public long sequenceNumber;
    /**
     * Packet data
     */
    public JsonNode packet;
    /**
     * Time of receiving the packet
     */
    public IsoTime receivedAt;
  }
  
  private OutOfOrderListener outOfOrderListener;
  private int orderingTimeoutInSeconds;
  private Map<String, Boolean> isOutOfOrderEmitted;
  private Map<String, AtomicLong> sequenceNumberByInstance;
  private Map<String, Integer> lastSessionStartTimestamp;
  private Map<String, List<Packet>> packetsByInstance;
  private int waitListSizeLimit = 100;
  private Timer outOfOrderJob;
  
  /**
   * Constructs the class
   * @param outOfOrderListener listener which will receive out of order packet events
   * @param orderingTimeoutInSeconds packet ordering timeout
   */
  public PacketOrderer(OutOfOrderListener outOfOrderListener, int orderingTimeoutInSeconds) {
    this.outOfOrderListener = outOfOrderListener;
    this.orderingTimeoutInSeconds = orderingTimeoutInSeconds;
    this.isOutOfOrderEmitted = new HashMap<>();
  }
  
  /**
   * Initializes the packet orderer
   */
  public void start() {
    final PacketOrderer self = this;
    sequenceNumberByInstance = new HashMap<>();
    lastSessionStartTimestamp = new HashMap<>();
    packetsByInstance = new HashMap<>();
    if (outOfOrderJob == null) {
      outOfOrderJob = new Timer();
      outOfOrderJob.schedule(new TimerTask() {
        @Override
        public void run() {
          self.emitOutOfOrderEvents();
        }
      }, 1000, 1000);
    }
  }
  
  /**
   * Deinitialized the packet orderer
   */
  public void stop() {
    if (outOfOrderJob != null) {
      outOfOrderJob.cancel();
      outOfOrderJob = null;
    }
  }
  
  /**
   * Processes the packet and resolves in the order of packet sequence number
   * @param packet packet to process
   * @return ordered packets when the packets are ready to be processed in order
   */
  public List<JsonNode> restoreOrder(JsonNode packet) {
    List<JsonNode> result = new ArrayList<>();
    long sequenceNumber = (packet.has("sequenceNumber") ? packet.get("sequenceNumber").asLong() : -1);
    if (sequenceNumber == -1) {
      result.add(packet);
      return result;
    }
    String accountId = packet.get("accountId").asText();
    int instanceIndex = packet.has("instanceIndex") ? packet.get("instanceIndex").asInt() : 0;
    String instanceId = accountId + ":" + instanceIndex;
    if (packet.get("type").asText().equals("synchronizationStarted") && packet.has("synchronizationId")) {
      // synchronization packet sequence just started
      isOutOfOrderEmitted.put(instanceId, false);
      sequenceNumberByInstance.put(instanceId, new AtomicLong(sequenceNumber));
      lastSessionStartTimestamp.put(instanceId, packet.get("sequenceTimestamp").asInt());
      if (packetsByInstance.containsKey(instanceId)) {
        packetsByInstance.get(instanceId).removeIf(waitPacket -> 
          waitPacket.packet.get("sequenceTimestamp").asInt() < packet.get("sequenceTimestamp").asInt());
      }
      result.add(packet);
      result.addAll(findNextPacketsFromWaitList(instanceId));
      return result;
    } else if (lastSessionStartTimestamp.containsKey(instanceId)
      && packet.get("sequenceTimestamp").asInt() < lastSessionStartTimestamp.get(instanceId)) {
      // filter out previous packets
      return result;
    } else if (sequenceNumberByInstance.containsKey(instanceId)
      && sequenceNumber == sequenceNumberByInstance.get(instanceId).get()) {
      // let the duplicate s/n packet to pass through
      result.add(packet);
      return result;
    } else if (sequenceNumberByInstance.containsKey(instanceId)
      && sequenceNumber == sequenceNumberByInstance.get(instanceId).get() + 1) {
      // in-order packet was received
      sequenceNumberByInstance.get(instanceId).incrementAndGet();
      result.add(packet);
      result.addAll(findNextPacketsFromWaitList(instanceId));
      return result;
    } else {
      // out-of-order packet was received, add it to the wait list
      if (packetsByInstance.get(instanceId) == null) packetsByInstance.put(instanceId,
        new ArrayList<>());
      List<Packet> waitList = packetsByInstance.get(instanceId);
      Packet p = new Packet();
      p.instanceId = instanceId;
      p.accountId = accountId;
      p.instanceIndex = instanceIndex;
      p.sequenceNumber = sequenceNumber;
      p.packet = packet;
      p.receivedAt = new IsoTime(Date.from(Instant.now()));
      waitList.add(p);
      waitList.sort((e1, e2) -> (e1.sequenceNumber - e2.sequenceNumber) > 0 ? 1 : -1);
      while (waitList.size() > waitListSizeLimit) waitList.remove(0);
      return result;
    }
  }
  
  private List<JsonNode> findNextPacketsFromWaitList(String instanceId) {
    List<JsonNode> result = new ArrayList<>();
    List<Packet> waitList = packetsByInstance.getOrDefault(instanceId, new ArrayList<>());
    while (!waitList.isEmpty() 
      && (waitList.get(0).sequenceNumber == sequenceNumberByInstance.get(instanceId).get()
        || waitList.get(0).sequenceNumber == sequenceNumberByInstance.get(instanceId).get() + 1)
    ) {
      result.add(waitList.get(0).packet);
      if (waitList.get(0).sequenceNumber == sequenceNumberByInstance.get(instanceId).get() + 1) {
        sequenceNumberByInstance.get(instanceId).getAndIncrement();
      }
      waitList.remove(0);
    }
    if (waitList.isEmpty()) {
      packetsByInstance.remove(instanceId);
    }
    return result;
  }
  
  private void emitOutOfOrderEvents() {
    packetsByInstance.values().forEach(waitList -> { 
      if (!waitList.isEmpty()) {
        Packet packet = waitList.get(0);
        if (packet == null) return;
        Instant receivedAtPlusTimeout = packet.receivedAt.getDate()
          .toInstant().plusSeconds(orderingTimeoutInSeconds);
        if (receivedAtPlusTimeout.compareTo(Instant.now()) < 0) {
          String instanceId = packet.instanceId;
          if (!isOutOfOrderEmitted.getOrDefault(instanceId, false)) {
            isOutOfOrderEmitted.put(instanceId, true);
            // Do not emit onOutOfOrderPacket for packets that come before synchronizationStarted
            if (sequenceNumberByInstance.containsKey(instanceId)) {
              outOfOrderListener.onOutOfOrderPacket(packet.accountId, packet.instanceIndex,
                sequenceNumberByInstance.get(instanceId).get() + 1, packet.sequenceNumber,
                packet.packet, packet.receivedAt);
            }
          }
        }
      }
    });
  }
}