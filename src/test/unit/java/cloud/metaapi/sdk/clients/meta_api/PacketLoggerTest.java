package cloud.metaapi.sdk.clients.meta_api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cloud.metaapi.sdk.clients.meta_api.PacketLogger.LogMessage;
import cloud.metaapi.sdk.util.JsonMapper;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link PacketLogger}
 */
class PacketLoggerTest {

    private static SimpleDateFormat longTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static String folder = "./.metaapi/logs/";
    private static String filePath = folder + "2020-10-10-00/accountId.log";
    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private PacketLogger packetLogger;
    private Map<String, JsonNode> packets;
    
    @BeforeEach
    void setUp() throws Exception {
        ServiceProvider.setNowInstantMock(longTimeFormat.parse("2020-10-10 00:00:00.000").toInstant());
        packetLogger = new PacketLogger(new PacketLogger.LoggerOptions() {{
            fileNumberLimit = 3;
            logFileSizeInHours = 4;
        }});
        packetLogger.start();
        FileUtils.cleanDirectory(new File(folder));
        packets = new HashMap<>();
        ObjectNode accountInformationPacket = jsonMapper.createObjectNode();
        ObjectNode accountInformation = jsonMapper.createObjectNode();
        accountInformationPacket.put("type", "accountInformation");
        accountInformationPacket.put("instanceIndex", 7);
        accountInformationPacket.set("accountInformation", accountInformation);
        accountInformation.put("broker", "Broker");
        accountInformation.put("currency", "USD");
        accountInformation.put("server", "Broker-Demo");
        accountInformation.put("balance", 20000);
        accountInformation.put("equity", 25000);
        accountInformationPacket.put("sequenceTimestamp", 100000);
        accountInformationPacket.put("accountId", "accountId");
        packets.put("accountInformation", accountInformationPacket);
        ObjectNode pricesPacket = jsonMapper.createObjectNode();
        ObjectNode price1 = jsonMapper.createObjectNode();
        price1.put("symbol", "EURUSD");
        price1.put("bid", 1.18);
        price1.put("ask", 1.19);
        ObjectNode price2 = jsonMapper.createObjectNode();
        price2.put("symbol", "USDJPY");
        price2.put("bid", 103.222);
        price2.put("ask", 103.25);
        pricesPacket.put("type", "prices");
        pricesPacket.put("instanceIndex", 7);
        ArrayNode prices = jsonMapper.createArrayNode();
        prices.add(price1);
        prices.add(price2);
        pricesPacket.set("prices", prices);
        pricesPacket.put("accountId", "accountId");
        pricesPacket.put("sequenceTimestamp", 100000);
        pricesPacket.put("sequenceNumber", 1);
        packets.put("prices", pricesPacket);
        ObjectNode statusPacket = jsonMapper.createObjectNode();
        statusPacket.put("type", "status");
        statusPacket.put("instanceIndex", 7);
        statusPacket.put("status", "connected");
        statusPacket.put("accountId", "accountId");
        statusPacket.put("sequenceTimestamp", 100000);
        packets.put("status", statusPacket);
        ObjectNode keepAlivePacket = jsonMapper.createObjectNode();
        keepAlivePacket.put("type", "keepalive");
        keepAlivePacket.put("instanceIndex", 7);
        keepAlivePacket.put("accountId", "accountId");
        keepAlivePacket.put("sequenceTimestamp", 100000);
        packets.put("keepalive", keepAlivePacket);
        ObjectNode specificationsPacket = jsonMapper.createObjectNode();
        specificationsPacket.put("type", "specifications");
        specificationsPacket.put("instanceIndex", 7);
        specificationsPacket.set("specifications", jsonMapper.createArrayNode());
        specificationsPacket.put("accountId", "accountId");
        specificationsPacket.put("sequenceTimestamp", 100000);
        specificationsPacket.put("sequenceNumber", 1);
        packets.put("specifications", specificationsPacket);
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        packetLogger.stop();
        FileUtils.cleanDirectory(new File(folder));
        ServiceProvider.reset();
    }

    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsPacket() throws Exception {
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("accountInformation"));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testDoesNotRecordStatusAndKeepalivePackets() throws Exception {
        packetLogger.logPacket(packets.get("status"));
        packetLogger.logPacket(packets.get("keepalive"));
        sleep(1000);
        Thread.sleep(1000);
        assertFalse(Files.exists(FileSystems.getDefault().getPath(filePath)));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsShortenedSpecifications() throws Exception {
        packetLogger.logPacket(packets.get("specifications"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        ObjectNode expected = JsonMapper.getInstance().createObjectNode();
        expected.put("type", "specifications");
        expected.put("sequenceNumber", 1);
        expected.put("sequenceTimestamp", 100000);
        expected.put("instanceIndex", 7);
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(expected);
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsFullSpecificationsIfCompressDisabled() throws Exception {
        packetLogger.stop();
        packetLogger = new PacketLogger(new PacketLogger.LoggerOptions() {{
            fileNumberLimit = 3;
            logFileSizeInHours = 4;
            compressSpecifications = false;
        }});
        packetLogger.start();
        packetLogger.logPacket(packets.get("specifications"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        ObjectNode expected = JsonMapper.getInstance().createObjectNode();
        expected.put("accountId", "accountId");
        expected.put("type", "specifications");
        expected.put("sequenceNumber", 1);
        expected.put("instanceIndex", 7);
        expected.put("sequenceTimestamp", 100000);
        expected.set("specifications", JsonMapper.getInstance().createArrayNode());
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(expected);
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsSinglePricePacket() throws Exception {
        packetLogger.logPacket(packets.get("prices"));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("prices"));
        Assertions.assertThat(jsonMapper.readTree(result.get(1).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("accountInformation"));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsRangeOfPricePackets() throws Exception {
        packetLogger.logPacket(packets.get("prices"));
        packetLogger.logPacket(changeSN(packets.get("prices"), 2));
        packetLogger.logPacket(changeSN(packets.get("prices"), 3));
        packetLogger.logPacket(changeSN(packets.get("prices"), 4));
        packetLogger.logPacket(changeSN(packets.get("keepalive"), 5));
        packetLogger.logPacket(changeSN(packets.get("prices"), 6));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("prices"));
        Assertions.assertThat(jsonMapper.readTree(result.get(1).message)).usingRecursiveComparison()
            .isEqualTo(changeSN(packets.get("prices"), 6));
        assertEquals("Recorded price packets 1-6, instanceIndex: 7", result.get(2).message);
        Assertions.assertThat(jsonMapper.readTree(result.get(3).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("accountInformation"));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsRangeOfPricePacketsOfDifferentInstances() throws Exception {
        packetLogger.logPacket(packets.get("prices"));
        packetLogger.logPacket(changeSN(packets.get("prices"), 2));
        packetLogger.logPacket(changeSN(packets.get("prices"), 3));
        packetLogger.logPacket(changeSN(packets.get("prices"), 1, 8));
        packetLogger.logPacket(changeSN(packets.get("prices"), 2, 8));
        packetLogger.logPacket(changeSN(packets.get("prices"), 3, 8));
        packetLogger.logPacket(changeSN(packets.get("prices"), 4, 8));
        packetLogger.logPacket(changeSN(packets.get("prices"), 4));
        packetLogger.logPacket(changeSN(packets.get("prices"), 5, 8));
        ObjectNode accountInformationPacket = packets.get("accountInformation").deepCopy();
        accountInformationPacket.put("instanceIndex", 8);
        packetLogger.logPacket(accountInformationPacket);
        packetLogger.logPacket(changeSN(packets.get("prices"), 5));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
          .isEqualTo(packets.get("prices"));
        Assertions.assertThat(jsonMapper.readTree(result.get(1).message)).usingRecursiveComparison()
          .isEqualTo(changeSN(packets.get("prices"), 1, 8));
        Assertions.assertThat(jsonMapper.readTree(result.get(2).message)).usingRecursiveComparison()
          .isEqualTo(changeSN(packets.get("prices"), 5, 8));
        assertEquals("Recorded price packets 1-5, instanceIndex: 8", result.get(3).message);
        Assertions.assertThat(jsonMapper.readTree(result.get(4).message)).usingRecursiveComparison()
          .isEqualTo(accountInformationPacket);
        Assertions.assertThat(jsonMapper.readTree(result.get(5).message)).usingRecursiveComparison()
          .isEqualTo(changeSN(packets.get("prices"), 5));
        assertEquals("Recorded price packets 1-5, instanceIndex: 7", result.get(6).message);
        Assertions.assertThat(jsonMapper.readTree(result.get(7).message)).usingRecursiveComparison()
          .isEqualTo(packets.get("accountInformation"));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testRecordsAllPricePacketsIfCompressIsDisabled() throws Exception {
        packetLogger.stop();
        packetLogger = new PacketLogger(new PacketLogger.LoggerOptions() {{
            fileNumberLimit = 3;
            logFileSizeInHours = 4;
            compressPrices = false;
        }});
        packetLogger.start();
        packetLogger.logPacket(packets.get("prices"));
        packetLogger.logPacket(changeSN(packets.get("prices"), 2));
        packetLogger.logPacket(changeSN(packets.get("prices"), 3));
        packetLogger.logPacket(changeSN(packets.get("prices"), 4));
        packetLogger.logPacket(changeSN(packets.get("prices"), 5));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("prices"));
        Assertions.assertThat(jsonMapper.readTree(result.get(1).message)).usingRecursiveComparison()
            .isEqualTo(changeSN(packets.get("prices"), 2));
        Assertions.assertThat(jsonMapper.readTree(result.get(2).message)).usingRecursiveComparison()
        .isEqualTo(changeSN(packets.get("prices"), 3));
        Assertions.assertThat(jsonMapper.readTree(result.get(3).message)).usingRecursiveComparison()
        .isEqualTo(changeSN(packets.get("prices"), 4));
        Assertions.assertThat(jsonMapper.readTree(result.get(4).message)).usingRecursiveComparison()
        .isEqualTo(changeSN(packets.get("prices"), 5));
        Assertions.assertThat(jsonMapper.readTree(result.get(5).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("accountInformation"));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testStopsPricePacketSequenceIfPriceSequenceNumberDoesNotMatch() throws Exception {
        packetLogger.logPacket(packets.get("prices"));
        packetLogger.logPacket(changeSN(packets.get("prices"), 2));
        packetLogger.logPacket(changeSN(packets.get("prices"), 3));
        packetLogger.logPacket(changeSN(packets.get("prices"), 4));
        packetLogger.logPacket(changeSN(packets.get("prices"), 6));
        sleep(1000);
        Thread.sleep(1000);
        List<LogMessage> result = packetLogger.readLogs("accountId");
        Assertions.assertThat(jsonMapper.readTree(result.get(0).message)).usingRecursiveComparison()
            .isEqualTo(packets.get("prices"));
        Assertions.assertThat(jsonMapper.readTree(result.get(1).message)).usingRecursiveComparison()
            .isEqualTo(changeSN(packets.get("prices"), 4));
        assertEquals("Recorded price packets 1-4, instanceIndex: 7", result.get(2).message);
        Assertions.assertThat(jsonMapper.readTree(result.get(3).message)).usingRecursiveComparison()
            .isEqualTo(changeSN(packets.get("prices"), 6));
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testReadsLogsWithinBounds() throws Exception {
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(2000, 60 * 60 * 1000);
        Thread.sleep(1000);
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(2000, 60 * 60 * 1000);
        Thread.sleep(1000);
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(2000);
        List<LogMessage> result = packetLogger.readLogs("accountId",
            longTimeFormat.parse("2020-10-10 00:30:00.000"),
            longTimeFormat.parse("2020-10-10 01:30:00.000"));
        assertEquals(5, result.size());
        List<LogMessage> resultAfter = packetLogger.readLogs("accountId",
            longTimeFormat.parse("2020-10-10 00:30:00.000"), null);
        assertEquals(8, resultAfter.size());
        List<LogMessage> resultBefore = packetLogger.readLogs("accountId",
            null, longTimeFormat.parse("2020-10-10 01:30:00.000"));
        assertEquals(7, resultBefore.size());
    }
    
    /**
     * Tests {@link PacketLogger#logPacket(JsonNode)} 
     */
    @Test
    void testDeletesExpiredFolders() throws Exception {
        File folderFile = new File(folder);
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(11000);
        List<String> files = Arrays.asList(folderFile.list());
        Collections.sort(files, Collator.getInstance());
        Assertions.assertThat(files.toArray()).usingRecursiveComparison()
            .isEqualTo(new String[] { "2020-10-10-00" });
        
        sleep(1000, 4 * 60 * 60 * 1000);
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(11000);
        files = Arrays.asList(folderFile.list());
        Collections.sort(files, Collator.getInstance());
        Assertions.assertThat(files.toArray()).usingRecursiveComparison()
            .isEqualTo(new String[] { "2020-10-10-00", "2020-10-10-01" });
        
        sleep(1000, 4 * 60 * 60 * 1000);
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(11000);
        files = Arrays.asList(folderFile.list());
        Collections.sort(files, Collator.getInstance());
        Assertions.assertThat(files.toArray()).usingRecursiveComparison()
            .isEqualTo(new String[] { "2020-10-10-00", "2020-10-10-01", "2020-10-10-02" });
        
        sleep(1000, 4 * 60 * 60 * 1000);
        packetLogger.logPacket(packets.get("accountInformation"));
        sleep(11000);
        files = Arrays.asList(folderFile.list());
        Collections.sort(files, Collator.getInstance());
        Assertions.assertThat(files.toArray()).usingRecursiveComparison()
            .isEqualTo(new String[] { "2020-10-10-01", "2020-10-10-02", "2020-10-10-03" });
    }
    
    private JsonNode changeSN(JsonNode obj, int sequenceNumber) {
        return changeSN(obj, sequenceNumber, null);
    }
    
    private JsonNode changeSN(JsonNode obj, int sequenceNumber, Integer instanceIndex) {
        ObjectNode result = obj.deepCopy();
        result.put("sequenceNumber", sequenceNumber);
        result.put("instanceIndex", instanceIndex != null ? instanceIndex : 7);
        return result;
    }

    private void sleep(int milliseconds) throws InterruptedException {
        sleep(milliseconds, milliseconds);
    }
    
    private void sleep(int milliseconds, int plusMilliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
        ServiceProvider.setNowInstantMock(ServiceProvider.getNow().plusMillis(plusMilliseconds));
    }
}
