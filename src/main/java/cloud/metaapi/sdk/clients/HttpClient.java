package cloud.metaapi.sdk.clients;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.MultipartBody;

import cloud.metaapi.sdk.clients.HttpRequestOptions.FileStreamField;
import cloud.metaapi.sdk.clients.errorHandler.*;
import cloud.metaapi.sdk.clients.models.Error;

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
            ApiException error = checkHttpError(response);
            if (error == null) result.complete(response);
            else result.completeExceptionally(error);
        });
        return result.thenApply((response) -> response.getBody());
    }
    
    /**
     * Does the same as {@link #request(HttpRequestOptions)} but automatically converts response into json.
     * If there is a json parsing error, completes exceptionally with {@link JsonProcessingException}.
     * @param options request options
     * @param valueType class into which the response will be transformed
     * @return completable future with request results as json
     */
    public <T> CompletableFuture<T> requestJson(HttpRequestOptions options, Class<T> valueType) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        request(options).thenAccept((response) -> {
            try {
                result.complete(JsonMapper.getInstance().readValue(response, valueType));
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
                Map<String, Object> fields = options.getBodyFields().get();
                if (fields.isEmpty()) request = bodyRequest.getHttpRequest();
                else {
                    MultipartBody multipartBody = bodyRequest.fields(null);
                    fields.forEach((name, value) -> {
                        if (value instanceof FileStreamField) {
                            FileStreamField fileField = (FileStreamField) value;
                            multipartBody.field(name, fileField.getStream(), fileField.getFileName());
                        } else multipartBody.field(name, value);
                    });
                    request = multipartBody.getHttpRequest();
                }
            } else request = bodyRequest.getHttpRequest();
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
    
    private ApiException checkHttpError(HttpResponse<String> response) {
        int statusType = response.getStatus() / 100;
        if (statusType != 4 && statusType != 5) return null;
        Error error;
        try {
            error = JsonMapper.getInstance().readValue(response.getBody(), Error.class);
        } catch (JsonProcessingException e) {
            error = null;
        }
        switch (response.getStatus()) {
            case 400: return new ValidationException(
                error != null ? error.message : response.getStatusText(),
                error != null ? error.details.get() : List.of()
            );
            case 401: return new UnauthorizedException(error != null ? error.message : response.getStatusText());
            case 403: return new ForbiddenException(error != null ? error.message : response.getStatusText());
            case 404: return new NotFoundException(error != null ? error.message : response.getStatusText());
            case 409: return new ConflictException(error != null ? error.message : response.getStatusText());
            case 500: return new InternalException(error != null ? error.message : response.getStatusText());
            default: return new ApiException(
                error != null ? error.message : response.getStatusText(),
                response.getStatus()
            );
        }
    }
}