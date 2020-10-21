package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
    void testRestoresPacketOrderStartingFromPacketOfSpecificationsType() {
        ObjectNode firstPacket = JsonMapper.getInstance().createObjectNode();
        firstPacket.put("type", "specifications");
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
    void testFiltersOutPacketsFromPreviousSynchronizationAttemptThatIncludesSpecifications() {
        ObjectNode previousSpecifications = JsonMapper.getInstance().createObjectNode();
        previousSpecifications.put("type", "specifications");
        previousSpecifications.put("accountId", "accountId");
        previousSpecifications.put("sequenceTimestamp", 1603124267178L);
        previousSpecifications.put("sequenceNumber", 13);
        previousSpecifications.put("synchronizationId", "synchronizationId");
        ObjectNode oneOfPreviousPackets = JsonMapper.getInstance().createObjectNode();
        oneOfPreviousPackets.put("type", "positions");
        oneOfPreviousPackets.put("accountId", "accountId");
        oneOfPreviousPackets.put("sequenceTimestamp", 1603124267188L);
        oneOfPreviousPackets.put("sequenceNumber", 15);
        ObjectNode thisSpecifications = JsonMapper.getInstance().createObjectNode();
        thisSpecifications.put("type", "specifications");
        thisSpecifications.put("accountId", "accountId");
        thisSpecifications.put("sequenceTimestamp", 1603124267198L);
        thisSpecifications.put("sequenceNumber", 1);
        thisSpecifications.put("synchronizationId", "synchronizationId");
        ObjectNode thisSecondPacket = JsonMapper.getInstance().createObjectNode();
        thisSecondPacket.put("type", "prices");
        thisSecondPacket.put("accountId", "accountId");
        thisSecondPacket.put("sequenceTimestamp", 1603124268198L);
        thisSecondPacket.put("sequenceNumber", 2);
        assertThat(packetOrderer.restoreOrder(previousSpecifications))
            .usingRecursiveComparison().isEqualTo(Lists.list(previousSpecifications));
        assertThat(packetOrderer.restoreOrder(oneOfPreviousPackets))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSecondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSpecifications))
            .usingRecursiveComparison().isEqualTo(Lists.list(thisSpecifications, thisSecondPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testFiltersOutPacketsFromPreviousSynchronizationAttemptThatDoesNotIncludeSpecifications() {
        ObjectNode oneOfPreviousPackets = JsonMapper.getInstance().createObjectNode();
        oneOfPreviousPackets.put("type", "positions");
        oneOfPreviousPackets.put("accountId", "accountId");
        oneOfPreviousPackets.put("sequenceTimestamp", 1603124267188L);
        oneOfPreviousPackets.put("sequenceNumber", 15);
        ObjectNode thisSpecifications = JsonMapper.getInstance().createObjectNode();
        thisSpecifications.put("type", "specifications");
        thisSpecifications.put("accountId", "accountId");
        thisSpecifications.put("sequenceTimestamp", 1603124267198L);
        thisSpecifications.put("sequenceNumber", 16);
        thisSpecifications.put("synchronizationId", "synchronizationId");
        ObjectNode thisSecondPacket = JsonMapper.getInstance().createObjectNode();
        thisSecondPacket.put("type", "prices");
        thisSecondPacket.put("accountId", "accountId");
        thisSecondPacket.put("sequenceTimestamp", 1603124268198L);
        thisSecondPacket.put("sequenceNumber", 17);
        assertThat(packetOrderer.restoreOrder(oneOfPreviousPackets))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSecondPacket))
            .usingRecursiveComparison().isEqualTo(Lists.emptyList());
        assertThat(packetOrderer.restoreOrder(thisSpecifications))
            .usingRecursiveComparison().isEqualTo(Lists.list(thisSpecifications, thisSecondPacket));
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testPassesThroughDuplicatePackets() {
        ObjectNode previousSpecifications = JsonMapper.getInstance().createObjectNode();
        previousSpecifications.put("type", "specifications");
        previousSpecifications.put("accountId", "accountId");
        previousSpecifications.put("sequenceTimestamp", 1603124267178L);
        previousSpecifications.put("sequenceNumber", 16);
        previousSpecifications.put("synchronizationId", "synchronizationId");
        ObjectNode secondPacket = JsonMapper.getInstance().createObjectNode();
        secondPacket.put("type", "prices");
        secondPacket.put("accountId", "accountId");
        secondPacket.put("sequenceTimestamp", 1603124268198L);
        secondPacket.put("sequenceNumber", 17);
        assertThat(packetOrderer.restoreOrder(previousSpecifications))
            .usingRecursiveComparison().isEqualTo(Lists.list(previousSpecifications));
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
        firstPacket.put("type", "specifications");
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
        firstPacket.put("type", "specifications");
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
            Mockito.eq(14), Mockito.eq(15), Mockito.eq(thirdPacket), Mockito.any());
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
    void testCallsOnOutOfOutOrderListenerIfTheFirstPacketInWaitListIsTimedOut() throws Exception {
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
        @SuppressWarnings("unchecked")
        Map<String, List<Packet>> waitLists = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByAccountId", true);
        waitLists.put("accountId", Lists.list(timedOutPacket, notTimedOutPacket));
        Thread.sleep(3000);
        Mockito.verify(outOfOrderListener, Mockito.times(1)).onOutOfOrderPacket("accountId", 1, 11,
            timedOutPacket.packet, timedOutPacket.receivedAt);
    }
    
    /**
     * Tests {@link PacketOrderer#restoreOrder(JsonNode)}
     */
    @Test
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
        @SuppressWarnings("unchecked")
        Map<String, List<Packet>> waitLists = (Map<String, List<Packet>>) FieldUtils
            .readField(packetOrderer, "packetsByAccountId", true);
        waitLists.put("accountId", Lists.list(notTimedOutPacket, timedOutPacket));
        Thread.sleep(3000);
        Mockito.verify(outOfOrderListener, Mockito.times(0)).onOutOfOrderPacket(Mockito.anyString(), 
            Mockito.anyInt(), Mockito.anyInt(), Mockito.any(JsonNode.class), Mockito.any(IsoTime.class));
    }
}