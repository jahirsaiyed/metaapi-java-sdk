package cloud.metaapi.sdk.clients.meta_api;

import com.fasterxml.jackson.databind.JsonNode;

import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * Listener to receive out of order packet events
 */
public interface OutOfOrderListener {
    
    /**
     * Method to receive out of order packet events
     * @param accountId account id
     * @param instanceIndex instance index
     * @param expectedSequenceNumber expected s/n
     * @param actualSequenceNumber actual s/n
     * @param packet packet data
     * @param receivedAt time the packet was received at
     */
    void onOutOfOrderPacket(String accountId, int instanceIndex, long expectedSequenceNumber, 
        long actualSequenceNumber, JsonNode packet, IsoTime receivedAt);
}