package cloud.metaapi.sdk.clients;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.util.JsonMapper;
import kong.unirest.Cookies;

/**
 * HTTP client that differs from the HttpClient in that upon request it returns
 * not only the request body, but also cookies
 */
public class HttpClientWithCookies {

    private HttpClient httpClient;
    
    /**
     * Constructs class instance. Connect and request timeout are {@code 1 minute} each.
     */
    public HttpClientWithCookies() {
        httpClient = new HttpClient();
    }
    
    /**
     * Constructs class instance
     * @param requestTimeout request timeout in milliseconds
     * @param connectTimeout connect timeout in milliseconds
     */
    public HttpClientWithCookies(int requestTimeout, int connectTimeout) {
        httpClient = new HttpClient();
    }
    
    /**
     * Performs a request. Completable future response errors are returned as ApiError or subclasses.
     * Also see {@link HttpClient#makeRequest(HttpRequestOptions)} for more information about the result completion cases.
     * @param options request options
     * @return completable future with request results
     */
    public CompletableFuture<Pair<String, Cookies>> request(HttpRequestOptions options) {
        return httpClient.makeCheckedRequest(options)
            .thenApply(response -> Pair.of(response.getBody(), response.getCookies()));
    }
    
    /**
     * Does the same as {@link #request(HttpRequestOptions)} but automatically converts response into json.
     * If there is a json parsing error, completes exceptionally with {@link JsonProcessingException}.
     * @param options request options
     * @param valueType class into which the response will be transformed
     * @return completable future with request results as json
     */
    public <T> CompletableFuture<Pair<T, Cookies>> requestJson(HttpRequestOptions options, Class<T> valueType) {
        return httpClient.makeCheckedRequest(options).thenApply((response) -> {
            try {
                return Pair.of(JsonMapper.getInstance().readValue(response.getBody(), valueType), response.getCookies());
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        });
    }
}