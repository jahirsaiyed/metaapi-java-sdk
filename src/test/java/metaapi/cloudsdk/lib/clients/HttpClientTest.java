package metaapi.cloudsdk.lib.clients;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import metaapi.cloudsdk.lib.clients.HttpRequestOptions.Method;
import metaapi.cloudsdk.lib.clients.errorHandler.*;
import metaapi.cloudsdk.lib.clients.mocks.HttpClientMock;

/**
 * Tests {@link HttpClient}
 */
public class HttpClientTest {

    private final HttpClient httpClient = new HttpClient();
    
    /**
     * Tests {@link HttpClient#request(HttpRequestOptions)}
     */
    @Test
    public void testLoadsHtmlPageFromExampleCom() throws Exception {
        HttpRequestOptions opts = new HttpRequestOptions("http://example.com", Method.GET);
        String response = httpClient.request(opts).get(10000, TimeUnit.MILLISECONDS);
        assertNotEquals(-1, response.indexOf("doctype html"));
    }
    
    /**
     * Tests {@link HttpClient#request(HttpRequestOptions)}
     */
    @Test
    public void testReturnsNotFoundIfServerReturns404() {
        assertThrows(NotFoundError.class, () -> {
            HttpRequestOptions opts = new HttpRequestOptions("http://example.com/not-found", Method.GET);
            try {
                httpClient.request(opts).get(10000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }
    
    /**
     * Tests {@link HttpClient#requestJson(HttpRequestOptions)}
     */
    @Test
    public void testCanReturnJsonInResponse() throws Exception {
        String jsonString = "{\"a\": 42, \"b\": \"28\"}";
        JsonNode expected = JsonMapper.getInstance().readTree(jsonString);
        HttpClient clientMock = new HttpClientMock((opts) -> CompletableFuture.completedFuture(jsonString));
        JsonNode actual = clientMock.requestJson(null).get();
        assertTrue(EqualsBuilder.reflectionEquals(expected, actual));
    }
    
    /**
     * Tests {@link HttpClient#requestJson(HttpRequestOptions)}
     */
    @Test
    public void testCompletesExceptionallyDuringParsingInvalidJsonString() {
        assertThrows(JsonProcessingException.class, () -> {
            String invalidJsonString = "{a: 42, b: 28}";
            HttpClient clientMock = new HttpClientMock((opts) -> CompletableFuture.completedFuture(invalidJsonString));
            try {
                clientMock.requestJson(null).get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }
}