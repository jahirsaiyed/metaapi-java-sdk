package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.meta_api.PacketOrderer.Packet;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link PacketOrderer}
 */
class PacketOrdererTest {

    private PacketOrderer packetOrderer;
    private OutOfOrderListener outOfOrderListener;
    
    @BeforeEach
    void setUp() throws Exception {
        outOfOrderListener = Mockito.mock(OutOfOrderListener.class);
        packetOrderer = new PacketOrderer(outOfOrderListener, 1);
        packetOrderer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        packetOrderer.stop();
    }

    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testReturnsPacketWithoutASequenceNumberImmediately() {
        ObjectNode packetWithoutSN = JsonMapper.getInstance().createObjectNode();
        packetWithoutSN.put("type", "authenticated");
        assertThat(packetOrderer.restoreOrder(packetWithoutSN))
            .usingRecursiveComparison().isEqualTo(Lists.list(packetWithoutSN));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testRestoresPacketOrder() {
        ObjectNode firstPacket = JsonMapper.getInstance().createObjectNode();
        firstPacket.put("type", "synchronizationStarted");
        firstPacket.put("accountId", "accountId");
        firstPacket.put("sequenceTimestamp", 1603124267178L);
        firstPacket.put("sequenceNumber", 13);
        firstPacket.put("synchronizationId", "synchronizationId");
        ObjectNode secondPacket = JsonMapper.getInstance().createObjectNode();
        secondPacket.put("type", "prices");
        secondPacket.put("accountId", "accountId");
        secondPacket.put("sequenceTimestamp", 1603124267180L);
        secondPacket.put("sequenceNumber", 14);
        ObjectNode thirdPacket = JsonMapper.getInstance().createObjectNode();
        thirdPacket.put("type", "accountInformation");
        thirdPacket.put("accountId", "accountId");
        thirdPacket.put("sequenceTimestamp", 1603124267187L);
        thirdPacket.put("sequenceNumber", 15);
        ObjectNode fourthPacket = JsonMapper.getInstance().createObjectNode();
        fourthPacket.put("type", "positions");
        fourthPacket.put("accountId", "accountId");
        fourthPacket.put("sequenceTimestamp", 1603124267188L);
        fourthPacket.put("sequenceNumber", 16);
        assertThat(packetOrderer.restoreOrder(secondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(firstPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(firstPacket, secondPacket));
        assertThat(packetOrderer.restoreOrder(fourthPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thirdPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(thirdPacket, fourthPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testFiltersOutPacketsFromPreviousSynchronizationAttemptThatIncludesSynchronizationStartedPacket() {
        ObjectNode previousStart = JsonMapper.getInstance().createObjectNode();
        previousStart.put("type", "synchronizationStarted");
        previousStart.put("accountId", "accountId");
        previousStart.put("sequenceTimestamp", 1603124267178L);
        previousStart.put("sequenceNumber", 13);
        previousStart.put("synchronizationId", "synchronizationId");
        ObjectNode oneOfPreviousPackets = JsonMapper.getInstance().createObjectNode();
        oneOfPreviousPackets.put("type", "positions");
        oneOfPreviousPackets.put("accountId", "accountId");
        oneOfPreviousPackets.put("sequenceTimestamp", 1603124267188L);
        oneOfPreviousPackets.put("sequenceNumber", 15);
        ObjectNode thisStart = JsonMapper.getInstance().createObjectNode();
        thisStart.put("type", "synchronizationStarted");
        thisStart.put("accountId", "accountId");
        thisStart.put("sequenceTimestamp", 1603124267198L);
        thisStart.put("sequenceNumber", 1);
        thisStart.put("synchronizationId", "synchronizationId");
        ObjectNode thisSecondPacket = JsonMapper.getInstance().createObjectNode();
        thisSecondPacket.put("type", "prices");
        thisSecondPacket.put("accountId", "accountId");
        thisSecondPacket.put("sequenceTimestamp", 1603124268198L);
        thisSecondPacket.put("sequenceNumber", 2);
        assertThat(packetOrderer.restoreOrder(previousStart))
            .usingRecursiveComparison().isEqualTo(Lists.list(previousStart));
        assertThat(packetOrderer.restoreOrder(oneOfPreviousPackets))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSecondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisStart))
            .usingRecursiveComparison().isEqualTo(Lists.list(thisStart, thisSecondPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testFiltersOutPacketsFromPreviousSynchronizationAttemptThatDoesNotIncludeSynchronizationStartedPacket() {
        ObjectNode oneOfPreviousPackets = JsonMapper.getInstance().createObjectNode();
        oneOfPreviousPackets.put("type", "positions");
        oneOfPreviousPackets.put("accountId", "accountId");
        oneOfPreviousPackets.put("sequenceTimestamp", 1603124267188L);
        oneOfPreviousPackets.put("sequenceNumber", 15);
        ObjectNode thisStart = JsonMapper.getInstance().createObjectNode();
        thisStart.put("type", "synchronizationStarted");
        thisStart.put("accountId", "accountId");
        thisStart.put("sequenceTimestamp", 1603124267198L);
        thisStart.put("sequenceNumber", 16);
        thisStart.put("synchronizationId", "synchronizationId");
        ObjectNode thisSecondPacket = JsonMapper.getInstance().createObjectNode();
        thisSecondPacket.put("type", "prices");
        thisSecondPacket.put("accountId", "accountId");
        thisSecondPacket.put("sequenceTimestamp", 1603124268198L);
        thisSecondPacket.put("sequenceNumber", 17);
        assertThat(packetOrderer.restoreOrder(oneOfPreviousPackets))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSecondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisStart))
            .usingRecursiveComparison().isEqualTo(Lists.list(thisStart, thisSecondPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testPassesThroughDuplicatePackets() {
        ObjectNode previousStart = JsonMapper.getInstance().createObjectNode();
        previousStart.put("type", "synchronizationStarted");
        previousStart.put("accountId", "accountId");
        previousStart.put("sequenceTimestamp", 1603124267178L);
        previousStart.put("sequenceNumber", 16);
        previousStart.put("synchronizationId", "synchronizationId");
        ObjectNode secondPacket = JsonMapper.getInstance().createObjectNode();
        secondPacket.put("type", "prices");
        secondPacket.put("accountId", "accountId");
        secondPacket.put("sequenceTimestamp", 1603124268198L);
        secondPacket.put("sequenceNumber", 17);
        assertThat(packetOrderer.restoreOrder(previousStart))
            .usingRecursiveComparison().isEqualTo(Lists.list(previousStart));
        assertThat(packetOrderer.restoreOrder(secondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(secondPacket));
        assertThat(packetOrderer.restoreOrder(secondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(secondPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testReturnsInOrderPacketsImmediately() {
        ObjectNode firstPacket = JsonMapper.getInstance().createObjectNode();
        firstPacket.put("type", "synchronizationStarted");
        firstPacket.put("accountId", "accountId");
        firstPacket.put("sequenceTimestamp", 1603124267178L);
        firstPacket.put("sequenceNumber", 13);
        firstPacket.put("synchronizationId", "synchronizationId");
        ObjectNode secondPacket = JsonMapper.getInstance().createObjectNode();
        secondPacket.put("type", "prices");
        secondPacket.put("accountId", "accountId");
        secondPacket.put("sequenceTimestamp", 1603124268180L);
        secondPacket.put("sequenceNumber", 14);
        ObjectNode thirdPacket = JsonMapper.getInstance().createObjectNode();
        thirdPacket.put("type", "accountInformation");
        thirdPacket.put("accountId", "accountId");
        thirdPacket.put("sequenceTimestamp", 1603124267187L);
        thirdPacket.put("sequenceNumber", 15);
        assertThat(packetOrderer.restoreOrder(firstPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(firstPacket));
        assertThat(packetOrderer.restoreOrder(secondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(secondPacket));
        assertThat(packetOrderer.restoreOrder(thirdPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(thirdPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testCallsOnOutOfOutOrderListenerOnlyOncePerSynchronizationAttempt() throws InterruptedException {
        ObjectNode firstPacket = JsonMapper.getInstance().createObjectNode();
        firstPacket.put("type", "synchronizationStarted");
        firstPacket.put("accountId", "accountId");
        firstPacket.put("sequenceTimestamp", 1603124267178L);
        firstPacket.put("sequenceNumber", 13);
        firstPacket.put("synchronizationId", "synchronizationId");
        ObjectNode thirdPacket = JsonMapper.getInstance().createObjectNode();
        thirdPacket.put("type", "orders");
        thirdPacket.put("accountId", "accountId");
        thirdPacket.put("sequenceTimestamp", 1603124267193L);
        thirdPacket.put("sequenceNumber", 15);
        assertThat(packetOrderer.restoreOrder(firstPacket))
            .usingRecursiveComparison().isEqualTo(Lists.list(firstPacket));
        assertThat(packetOrderer.restoreOrder(thirdPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        Thread.sleep(3000);
        Mockito.verify(outOfOrderListener, Mockito.times(1)).onOutOfOrderPacket(Mockito.eq("accountId"), 
            Mockito.eq(0), Mockito.eq(14L), Mockito.eq(15L), Mockito.eq(thirdPacket), Mockito.any());
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCallsOnOutOfOutOrderListenerIfTheFirstPacketInWaitListIsTimedOut() throws Exception {
        Packet timedOutPacket = packetOrderer.new Packet() {{
            accountId = "accountId";
            instanceId = "accountId:0";
            instanceIndex = 0;
            sequenceNumber = 11;
            packet = JsonMapper.getInstance().createObjectNode();
            receivedAt = new IsoTime("2010-10-19T09:58:56.000Z");
        }};
        Packet notTimedOutPacket = packetOrderer.new Packet() {{
            accountId = "accountId";
            instanceId = "accountId:0";
            instanceIndex = 0;
            sequenceNumber = 15;
            packet = JsonMapper.getInstance().createObjectNode();
            receivedAt = new IsoTime("3015-10-19T09:58:56.000Z");
        }};
        Map<String, AtomicLong> sequenceNumberByAccount = (Map<String, AtomicLong>) FieldUtils
            .readField(packetOrderer, "sequenceNumberByInstance", true);
        sequenceNumberByAccount.put("accountId:0", new AtomicLong(1));
        Map<String, List<Packet>> waitLists = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByInstance", true);
        waitLists.put("accountId:0", Lists.list(timedOutPacket, notTimedOutPacket));
        Thread.sleep(3000);
        Mockito.verify(outOfOrderListener, Mockito.times(1)).onOutOfOrderPacket("accountId",
            0, 2, 11, timedOutPacket.packet, timedOutPacket.receivedAt);
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    @SuppressWarnings("unchecked")
    void testDoesNotCallOnOutOfOutOrderListenerIfTheFirstPacketInWaitListIsNotTimedOut() throws Exception {
        Packet timedOutPacket = packetOrderer.new Packet() {{
            accountId = "accountId";
            sequenceNumber = 11;
            packet = JsonMapper.getInstance().createObjectNode();
            receivedAt = new IsoTime("2010-10-19T09:58:56.000Z");
        }};
        Packet notTimedOutPacket = packetOrderer.new Packet() {{
            accountId = "accountId";
            sequenceNumber = 15;
            packet = JsonMapper.getInstance().createObjectNode();
            receivedAt = new IsoTime("3015-10-19T09:58:56.000Z");
        }};
        Map<String, AtomicLong> sequenceNumberByAccount = (Map<String, AtomicLong>) FieldUtils
            .readField(packetOrderer, "sequenceNumberByInstance", true);
            sequenceNumberByAccount.put("accountId:0", new AtomicLong(1));
        Map<String, List<Packet>> waitLists = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByInstance", true);
        waitLists.put("accountId:0", Lists.list(notTimedOutPacket, timedOutPacket));
        Thread.sleep(3000);
        Mockito.verify(outOfOrderListener, Mockito.times(0)).onOutOfOrderPacket(Mockito.anyString(), 
            Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(JsonNode.class),
            Mockito.any(IsoTime.class));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    @SuppressWarnings("unchecked")
    void testDoesNotCallOnOutOfOutOrderListenerForPacketsThatComeBeforeSynchronizationStart() throws Exception {
        Packet outOfOrderPacket = packetOrderer.new Packet() {{
            accountId = "accountId";
            sequenceNumber = 11;
            packet = JsonMapper.getInstance().createObjectNode();
            receivedAt = new IsoTime("2010-10-19T09:58:56.000Z");
        }};
        
        // There were no synchronization start packets        
        Map<String, AtomicLong> sequenceNumberByAccount = (Map<String, AtomicLong>) FieldUtils
            .readField(packetOrderer, "sequenceNumberByInstance", true);
        sequenceNumberByAccount.remove("accountId:0");
        
        Map<String, List<Packet>> waitLists = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByInstance", true);
        waitLists.put("accountId:0", Lists.list(outOfOrderPacket));
        Thread.sleep(1000);
        Mockito.verify(outOfOrderListener, Mockito.times(0)).onOutOfOrderPacket(Mockito.anyString(), 
            Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(JsonNode.class),
            Mockito.any(IsoTime.class));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    @SuppressWarnings("unchecked")
    void testMaintainsAFixedQueueOfWaitList() throws Exception {
        FieldUtils.writeField(packetOrderer, "waitListSizeLimit", 1, true);
        ObjectNode secondPacket = JsonMapper.getInstance().createObjectNode();
        secondPacket.put("type", "prices");
        secondPacket.put("sequenceTimestamp", 1603124267180L);
        secondPacket.put("sequenceNumber", 14);
        secondPacket.put("accountId", "accountId");
        ObjectNode thirdPacket = JsonMapper.getInstance().createObjectNode();
        thirdPacket.put("type", "accountInformation");
        thirdPacket.put("sequenceTimestamp", 1603124267187L);
        thirdPacket.put("sequenceNumber", 15);
        thirdPacket.put("accountId", "accountId");
        Map<String, List<Packet>> packetsByAccountId = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByInstance", true); 
        packetOrderer.restoreOrder(secondPacket);
        assertEquals(1, packetsByAccountId.get("accountId:0").size());
        assertEquals(secondPacket, packetsByAccountId.get("accountId:0").get(0).packet);
        packetOrderer.restoreOrder(thirdPacket);
        assertEquals(1, packetsByAccountId.get("accountId:0").size());
        assertEquals(thirdPacket, packetsByAccountId.get("accountId:0").get(0).packet);
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCountsStartPacketsWithUndefinedSynchronizationIdAsOutOfOrder() throws Exception {
        ObjectNode startPacket = JsonMapper.getInstance().createObjectNode();
        startPacket.put("type", "synchronizationStarted");
        startPacket.put("sequenceTimestamp", 1603124267180L);
        startPacket.put("sequenceNumber", 16);
        startPacket.put("accountId", "accountId");
        Map<String, List<Packet>> packetsByAccountId = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByInstance", true); 
        assertEquals(0, packetOrderer.restoreOrder(startPacket).size());
        assertEquals(1, packetsByAccountId.get("accountId:0").size());
        assertEquals(startPacket, packetsByAccountId.get("accountId:0").get(0).packet);
    }
}