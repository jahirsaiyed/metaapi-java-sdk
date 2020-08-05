package metaapi.cloudsdk.lib.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import metaapi.cloudsdk.lib.clients.HttpRequestOptions.Method;
import metaapi.cloudsdk.lib.clients.errorHandler.*;
import metaapi.cloudsdk.lib.clients.mocks.HttpClientMock;

/**
 * Class for testing json requests
 */
class JsonModelExample {
    /**
     * Some first number
     */
    public int a;
    /**
     * Some second number
     */
    public int b;
}

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
        assertThrows(NotFoundException.class, () -> {
            HttpRequestOptions opts = new HttpRequestOptions("http://example.com/not-found", Method.GET);
            try {
                httpClient.request(opts).get(10000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }
    
    /**
     * Tests {@link HttpClient#requestJson(HttpRequestOptions, Class)}
     */
    @Test
    public void testCanReturnJsonInResponse() throws Exception {
        JsonModelExample expected = new JsonModelExample();
        expected.a = 42;
        expected.b = 28;
        HttpClient clientMock = new HttpClientMock((opts) -> {
            return CompletableFuture.completedFuture("{\"a\": 42, \"b\": \"28\"}");
        });
        JsonModelExample actual = clientMock.requestJson(null, JsonModelExample.class).get();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link HttpClient#requestJson(HttpRequestOptions, Class)}
     */
    @Test
    public void testCompletesExceptionallyDuringParsingInvalidJsonString() {
        assertThrows(JsonProcessingException.class, () -> {
            String invalidJsonString = "{a: 42, b: 28}";
            HttpClient clientMock = new HttpClientMock((opts) -> CompletableFuture.completedFuture(invalidJsonString));
            try {
                clientMock.requestJson(null, JsonModelExample.class).get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        });
    }
}