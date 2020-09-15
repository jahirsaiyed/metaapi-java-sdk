package cloud.metaapi.sdk.clients;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import kong.unirest.Cookies;

/**
 * Tests {@link HttpClientWithCookies}
 */
class HttpClientWithCookiesTest {

    private HttpClientWithCookies httpClient;
    
    @BeforeEach
    void setUp() throws Exception {
        httpClient = new HttpClientWithCookies();
    }

    /**
     * Tests {@link HttpClientWithCookies#request(HttpRequestOptions)}
     */
    @Test
    void testLoadsHtmlPageFromExampleCom() throws InterruptedException, ExecutionException, TimeoutException {
        HttpRequestOptions opts = new HttpRequestOptions("http://example.com", Method.GET);
        Pair<String, Cookies> response = httpClient.request(opts).get(10000, TimeUnit.MILLISECONDS);
        assertNotEquals(-1, response.getLeft().indexOf("doctype html"));
        assertTrue(response.getRight().isEmpty());
    }
}