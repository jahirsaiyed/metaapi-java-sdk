package metaapi.cloudsdk.lib.clients;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

import metaapi.cloudsdk.lib.clients.errorHandler.*;
import metaapi.cloudsdk.lib.clients.errorHandler.InternalError;

/**
 * HTTP client library based on request-promise
 */
public class HttpClient {
    
    /**
     * Performs a request. Response errors are returned as ApiError or subclasses.
     * Also see {@link #makeReqest(HttpRequestOptions)} for more information about the result completion cases.
     * @param options request options
     * @return completable future with request results
     */
    public CompletableFuture<String> request(HttpRequestOptions options) throws Exception {
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        this.makeReqest(options).thenAccept((response) -> {
            ApiError error = checkHttpError(response);
            if (error == null) result.complete(response);
            else result.completeExceptionally(error);
        });
        return result.thenApply((response) -> response.getBody());
    }
    
    /**
     * Does the same as {@link #request(HttpRequestOptions)} but automatically converts response into json.
     * If there is a json parsing error, completes exceptionally with {@link JsonProcessingException}.
     * @return completable future with request results as json
     */
    public CompletableFuture<JsonNode> requestJson(HttpRequestOptions options) throws Exception {
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        request(options).thenAccept((response) -> {
            try {
                result.complete(JsonMapper.getInstance().readTree(response));
            } catch (JsonProcessingException e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /**
     * Makes request and returns HTTP response. If request fails,  completable future completes exceptionally
     * with {@link UnirestException}. If request is cancelled, completable future is cancelled as well.
     */
    private CompletableFuture<HttpResponse<String>> makeReqest(HttpRequestOptions options) throws Exception {
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        HttpRequest request = null;
        
        if (options.getMethod() == HttpRequestOptions.Method.GET) request = Unirest.get(options.getUrl());
        else {
            HttpRequestWithBody bodyRequest = null;
            switch (options.getMethod()) {
                case POST: bodyRequest = Unirest.post(options.getUrl()); break;
                case PUT: bodyRequest = Unirest.put(options.getUrl()); break;
                case DELETE: bodyRequest = Unirest.delete(options.getUrl()); break;
                default: break;
            }
            if (options.getBodyJson().isPresent()) {
                String jsonString = JsonMapper.getInstance().writeValueAsString(options.getBodyJson().get());
                request = bodyRequest.body(jsonString).getHttpRequest().header("content-type", "application/json");
            } else if (options.getBodyFields().isPresent()) {
                request = bodyRequest.fields(options.getBodyFields().get()).getHttpRequest();
            } else {
                request = bodyRequest.getHttpRequest();
            }
        }
        
        request
            .queryString(options.getQueryParameters())
            .headers(options.getHeaders())
            .asStringAsync(new Callback<String>() 
        {
            @Override
            public void completed(HttpResponse<String> response) {
                result.complete(response);
            }
            @Override
            public void failed(UnirestException e) {
                result.completeExceptionally(e);
            }
            @Override
            public void cancelled() {
                result.cancel(false);
            }
        });
        return result;
    }
    
    private ApiError checkHttpError(HttpResponse<String> response) {
        int statusType = response.getStatus() / 100;
        if (statusType != 4 && statusType != 5) return null;
        switch (response.getStatus()) {
            case 400: return new ValidationError("Bad Request", response.getBody());
            case 401: return new UnauthorizedError("Unauthorized");
            case 403: return new ForbiddenError("Forbidden");
            case 404: return new NotFoundError("Not Found");
            case 409: return new ConflictError("Conflict");
            case 500: return new InternalError("Internal Server Error");
            default: return new ApiError(response.getBody(), response.getStatus());
        }
    }
}