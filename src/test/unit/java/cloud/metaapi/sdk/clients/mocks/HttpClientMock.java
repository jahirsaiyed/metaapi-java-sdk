package cloud.metaapi.sdk.clients.mocks;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;

/**
 * HTTP client service mock for tests
 */
public class HttpClientMock extends HttpClient {
    
    private Function<HttpRequestOptions, CompletableFuture<String>> requestMock;
    
    /**
     * Constructs HTTP client mock
     * @param requestMock mocked request function which must return CompletableFuture with response body string
     */
    public HttpClientMock(Function<HttpRequestOptions, CompletableFuture<String>> requestMock) {
        super();
        this.requestMock = requestMock;
    }
    
    /**
     * Overridden request method of HttpClient with replaced implementation that uses mocked request function
     */
    @Override
    public CompletableFuture<String> request(HttpRequestOptions options) {
        return requestMock.apply(options);
    }
    
    /**
     * Sets request mock function
     * @param mock mocked request function which must return CompletableFuture with response body string
     */
    public void setRequestMock(Function<HttpRequestOptions, CompletableFuture<String>> mock) {
        this.requestMock = mock;
    }
}
