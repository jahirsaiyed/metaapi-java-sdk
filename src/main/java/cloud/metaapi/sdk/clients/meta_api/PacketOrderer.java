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
         * Account id from packet
         */
        public String accountId;
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
    private Map<String, AtomicLong> sequenceNumberByAccount;
    private Map<String, Integer> lastSessionStartTimestamp;
    private Map<String, List<Packet>> packetsByAccountId;
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
        sequenceNumberByAccount = new HashMap<>();
        lastSessionStartTimestamp = new HashMap<>();
        packetsByAccountId = new HashMap<>();
        outOfOrderJob = new Timer();
        outOfOrderJob.schedule(new TimerTask() {
            @Override
            public void run() {
                self.emitOutOfOrderEvents();
            }
        }, 1000, 1000);
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
        if (packet.get("type").asText().equals("synchronizationStarted") && packet.has("synchronizationId")) {
            // synchronization packet sequence just started
            isOutOfOrderEmitted.put(accountId, false);
            sequenceNumberByAccount.put(accountId, new AtomicLong(sequenceNumber));
            lastSessionStartTimestamp.put(accountId, packet.get("sequenceTimestamp").asInt());
            if (packetsByAccountId.containsKey(accountId)) {
                packetsByAccountId.get(accountId).removeIf(waitPacket -> 
                    waitPacket.packet.get("sequenceTimestamp").asInt() < packet.get("sequenceTimestamp").asInt());
            }
            result.add(packet);
            result.addAll(findNextPacketsFromWaitList(accountId));
            return result;
        } else if (lastSessionStartTimestamp.containsKey(accountId)
            && packet.get("sequenceTimestamp").asInt() < lastSessionStartTimestamp.get(accountId)) {
            // filter out previous packets
            return result;
        } else if (sequenceNumberByAccount.containsKey(accountId)
            && sequenceNumber == sequenceNumberByAccount.get(accountId).get()) {
            // let the duplicate s/n packet to pass through
            result.add(packet);
            return result;
        } else if (sequenceNumberByAccount.containsKey(accountId)
            && sequenceNumber == sequenceNumberByAccount.get(accountId).get() + 1) {
            // in-order packet was received
            sequenceNumberByAccount.get(accountId).incrementAndGet();
            result.add(packet);
            result.addAll(findNextPacketsFromWaitList(accountId));
            return result;
        } else {
            // out-of-order packet was received, add it to the wait list
            if (packetsByAccountId.get(accountId) == null) packetsByAccountId.put(accountId, new ArrayList<>());
            List<Packet> waitList = packetsByAccountId.get(accountId);
            Packet p = new Packet();
            p.accountId = accountId;
            p.sequenceNumber = sequenceNumber;
            p.packet = packet;
            p.receivedAt = new IsoTime(Date.from(Instant.now()));
            waitList.add(p);
            waitList.sort((e1, e2) -> (e1.sequenceNumber - e2.sequenceNumber) > 0 ? 1 : -1);
            while (waitList.size() > waitListSizeLimit) waitList.remove(0);
            return result;
        }
    }
    
    private List<JsonNode> findNextPacketsFromWaitList(String accountId) {
        List<JsonNode> result = new ArrayList<>();
        List<Packet> waitList = packetsByAccountId.getOrDefault(accountId, new ArrayList<>());
        while (!waitList.isEmpty() 
            && (waitList.get(0).sequenceNumber == sequenceNumberByAccount.get(accountId).get()
                || waitList.get(0).sequenceNumber == sequenceNumberByAccount.get(accountId).get() + 1)
        ) {
            result.add(waitList.get(0).packet);
            if (waitList.get(0).sequenceNumber == sequenceNumberByAccount.get(accountId).get() + 1) {
                sequenceNumberByAccount.get(accountId).getAndIncrement();
            }
            waitList.remove(0);
        }
        if (waitList.isEmpty()) {
            packetsByAccountId.remove(accountId);
        }
        return result;
    }
    
    private void emitOutOfOrderEvents() {
        packetsByAccountId.values().forEach(waitList -> { 
            if (!waitList.isEmpty()) {
                Packet packet = waitList.get(0);
                if (packet == null) return;
                Instant receivedAtPlusTimeout = packet.receivedAt.getDate()
                    .toInstant().plusSeconds(orderingTimeoutInSeconds);
                if (receivedAtPlusTimeout.compareTo(Instant.now()) < 0) {
                    String accountId = packet.accountId;
                    if (!isOutOfOrderEmitted.getOrDefault(accountId, false)) {
                        isOutOfOrderEmitted.put(accountId, true);
                        // Do not emit onOutOfOrderPacket for packets that come before synchronizationStarted
                        if (sequenceNumberByAccount.containsKey(accountId)) {
                            outOfOrderListener.onOutOfOrderPacket(packet.accountId,
                                sequenceNumberByAccount.get(accountId).get() + 1,
                                packet.sequenceNumber, packet.packet, packet.receivedAt);
                        }
                    }
                }
            }
        });
    }
}