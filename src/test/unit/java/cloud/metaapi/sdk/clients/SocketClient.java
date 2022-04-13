package cloud.metaapi.sdk.clients;

import java.util.function.Consumer;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Socket client wrapper
 */
public class SocketClient {
  
  private static ObjectMapper jsonMapper = JsonMapper.getInstance();
  private SocketIOServer io;
  private SocketIOClient socket;
  
  /**
   * Constructs instance
   * @param io Socket server
   * @param socket Native socket
   */
  public SocketClient(SocketIOServer io, SocketIOClient socket) {
    this.io = io;
    this.socket = socket;
  }
  
  /**
   * Subscribes a client to 
   * @param event Event type
   * @param handler Event handler
   */
  public void on(String event, Consumer<JsonNode> handler) {
    io.addEventListener(event, Object.class, new DataListener<Object>() {
      @Override
      public void onData(SocketIOClient client, Object data, AckRequest ackSender) throws Exception {
        if (client == socket) {
          handler.accept(jsonMapper.valueToTree(data));
        }
      }
    });
  }
  
  /**
   * Sends event to the server
   * @param event Event type
   * @param data Data to send
   */
  public void sendEvent(String event, String data) {
    socket.sendEvent(event, data);
  }
  
  /**
   * Sends event to the server
   * @param event Event type
   * @param data Data to send
   */
  public void emit(String event, JsonNode data) {
    try {
      socket.sendEvent(event, jsonMapper.writeValueAsString(data));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Returns socket handshake data
   * @return Handshake data
   */
  public HandshakeData getHandshakeData() {
    return socket.getHandshakeData();
  }
  
  /**
   * Disconnects socket
   */
  public void disconnect() {
    socket.disconnect();
  }
}
