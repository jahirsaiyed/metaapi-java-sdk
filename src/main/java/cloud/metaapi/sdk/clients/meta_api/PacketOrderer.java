package cloud.metaapi.sdk.clients.meta_api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Class which orders the synchronization packets
 */
public class PacketOrderer {
   
    private class Packet {
        /**
         * Account id from packet
         */
        public String accountId;
        /**
         * Sequence number
         */
        public int sequenceNumber;
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
    private Map<String, AtomicInteger> sequenceNumberByAccount;
    private Map<String, List<Packet>> packetsByAccountId;
    private Timer outOfOrderJob;
    
    /**
     * Constructs the class
     * @param outOfOrderListener listener which will receive out of order packet events
     * @param orderingTimeoutInSeconds packet ordering timeout, or {@code null}. Default is 10 seconds
     */
    public PacketOrderer(OutOfOrderListener outOfOrderListener, Integer orderingTimeoutInSeconds) {
        this.outOfOrderListener = outOfOrderListener;
        this.orderingTimeoutInSeconds = (orderingTimeoutInSeconds != null ? orderingTimeoutInSeconds : 10);
        this.isOutOfOrderEmitted = new HashMap<>();
    }
    
    /**
     * Initializes the packet orderer
     */
    public void start() {
        final PacketOrderer self = this;
        sequenceNumberByAccount = new HashMap<>();
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
        int sequenceNumber = (packet.has("sequenceNumber") ? packet.get("sequenceNumber").asInt() : 0);
        if (sequenceNumber == 0) {
            result.add(packet);
            return result;
        }
        String accountId = packet.get("accountId").asText();
        if (packet.get("type").asText().equals("specifications") && packet.has("synchronizationId")) {
            // synchronization packet sequence just started
            isOutOfOrderEmitted.put(accountId, false);
            sequenceNumberByAccount.put(accountId, new AtomicInteger(sequenceNumber));
            packetsByAccountId.remove(accountId);
            result.add(packet);
            return result;
        } else if (sequenceNumber < sequenceNumberByAccount.get(accountId).get()) {
            // filter out previous packets
            return result;
        } else if (sequenceNumber == sequenceNumberByAccount.get(accountId).get()) {
            // let the duplicate s/n packet to pass through
            result.add(packet);
            return result;
        } else if (sequenceNumber == sequenceNumberByAccount.get(accountId).get() + 1) {
            // in-order packet was received
            sequenceNumberByAccount.get(accountId).incrementAndGet();
            result.add(packet);
            List<Packet> waitList = packetsByAccountId.getOrDefault(accountId, new ArrayList<>());
            while (!waitList.isEmpty() 
                && (waitList.get(0).sequenceNumber == getSequenceNumberByPacket(packet).get()
                    || waitList.get(0).sequenceNumber == getSequenceNumberByPacket(packet).get() + 1
                )
            ) {
                result.add(waitList.get(0).packet);
                if (waitList.get(0).sequenceNumber == getSequenceNumberByPacket(packet).get() + 1) {
                    getSequenceNumberByPacket(packet).getAndIncrement();
                }
                waitList.remove(0);
            }
            if (waitList.isEmpty()) {
                packetsByAccountId.remove(accountId);
            }
            return result;
        } else if (sequenceNumberByAccount.containsKey(accountId)) {
            // out-of-order packet was received, add it to the wait list
            if (packetsByAccountId.get(accountId) == null) packetsByAccountId.put(accountId, new ArrayList<>());
            List<Packet> waitList = packetsByAccountId.get(accountId);
            Packet p = new Packet();
            p.accountId = accountId;
            p.sequenceNumber = sequenceNumber;
            p.packet = packet;
            p.receivedAt = new IsoTime(Date.from(Instant.now()));
            waitList.add(p);
            return result;
        } else {
            result.add(packet);
            return result;
        }
    }
    
    private void emitOutOfOrderEvents() {
        packetsByAccountId.values().forEach(waitList -> { 
            if (!waitList.isEmpty()) {
                Packet packet = waitList.get(0);
                Instant receivedAtPlusTimeout = packet.receivedAt.getDate().toInstant().plusSeconds(orderingTimeoutInSeconds);
                if (receivedAtPlusTimeout.compareTo(Instant.now()) < 0) {
                    String accountId = packet.accountId;
                    if (!isOutOfOrderEmitted.getOrDefault(accountId, false)) {
                        isOutOfOrderEmitted.put(accountId, true);
                        outOfOrderListener.onOutOfOrderPacket(
                            packet.accountId, sequenceNumberByAccount.get(accountId).get() + 1,
                            packet.sequenceNumber, packet.packet, packet.receivedAt);
                    }
                }
            }
        });
    }
    
    private AtomicInteger getSequenceNumberByPacket(JsonNode packet) {
        return sequenceNumberByAccount.get(packet.get("accountId").asText());
    }
}