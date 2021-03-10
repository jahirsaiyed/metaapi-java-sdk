package cloud.metaapi.sdk.clients.meta_api;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.FileStreamField;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.*;

/**
 * metaapi.cloud provisioning profile API client (see https://metaapi.cloud/docs/provisioning/)
 */
public class ProvisioningProfileClient extends MetaApiClient {
    
    /**
     * Constructs provisioning API client instance with default domain agiliumtrade.agiliumtrade.ai
     * @param httpClient HTTP client
     * @param token authorization token
     */
    public ProvisioningProfileClient(HttpClient httpClient, String token) {
        super(httpClient, token);
    }
    
    /**
     * Constructs provisioning API client instance
     * @param httpClient HTTP client
     * @param token authorization token
     * @param domain domain to connect to
     */
    public ProvisioningProfileClient(HttpClient httpClient, String token, String domain) {
        super(httpClient, token, domain);
    }
    
    /**
     * Retrieves provisioning profiles owned by user
     * (see https://metaapi.cloud/docs/provisioning/api/provisioningProfile/readProvisioningProfiles/).
     * Method is accessible only with API access token.
     * @param version optional version filter (allowed values are 4 and 5) or {@code null}
     * @param status optional status filter (allowed values are new and active) or {@code null}
     * @return completable future resolving with provisioning profiles found
     */
    public CompletableFuture<List<ProvisioningProfileDto>> getProvisioningProfiles(Integer version, String status) {
        if (isNotJwtToken()) return handleNoAccessError("getProvisioningProfiles");
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/provisioning-profiles", Method.GET);
        if (version != null) opts.getQueryParameters().put("version", version);
        if (status != null) opts.getQueryParameters().put("status", status);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, ProvisioningProfileDto[].class)
            .thenApply(array -> Arrays.asList(array));
    }
    
    /**
     * Retrieves a provisioning profile by id (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/readProvisioningProfile/).
     * Completable future is completed with an error if profile is not found.
     * Method is accessible only with API access token.
     * @param id provisioning profile id
     * @return completable future resolving with provisioning profile found
     */
    public CompletableFuture<ProvisioningProfileDto> getProvisioningProfile(String id) {
        if (isNotJwtToken()) return handleNoAccessError("getProvisioningProfile");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/provisioning-profiles/" + id, Method.GET);
        opts.getHeaders().put("auth-token", token);
        return httpClient.requestJson(opts, ProvisioningProfileDto.class);
    }
    
    /**
     * Creates a new provisioning profile (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/createNewProvisioningProfile/). After creating a
     * provisioning profile you are required to upload extra files in order to activate the profile for further use.
     * Method is accessible only with API access token.
     * @param provisioningProfile provisioning profile to create
     * @return completable future resolving with an id of the provisioning profile created
     */
    public CompletableFuture<ProvisioningProfileIdDto> createProvisioningProfile(
        NewProvisioningProfileDto provisioningProfile
    ) {
        if (isNotJwtToken()) return handleNoAccessError("createProvisioningProfile");
        HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/provisioning-profiles", Method.POST);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(provisioningProfile);
        return httpClient.requestJson(opts, ProvisioningProfileIdDto.class);
    }
    
    /**
     * Uploads a file to a provisioning profile (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/uploadFilesToProvisioningProfile/).
     * Method is accessible only with API access token.
     * @param provisioningProfileId provisioning profile id to upload file to
     * @param fileName name of the file to upload. Allowed values are servers.dat for MT5 profile, broker.srv for
     * MT4 profile
     * @param filePath file path to a file to upload
     * @return completable future resolving when file upload is completed
     */
    public CompletableFuture<Void> uploadProvisioningProfileFile(
        String provisioningProfileId, String fileName, String filePath
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                uploadProvisioningProfileFile(
                    provisioningProfileId,
                    new FileStreamField(new FileInputStream(filePath), fileName)
                 ).join();
            } catch (FileNotFoundException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Uploads a file to a provisioning profile (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/uploadFilesToProvisioningProfile/).
     * Method is accessible only with API access token.
     * @param provisioningProfileId provisioning profile id to upload file to
     * @param fileName name of the file to upload. Allowed values are servers.dat for MT5 profile, broker.srv for
     * MT4 profile
     * @param fileContents input stream containing file contents to upload
     * @return completable future resolving when file upload is completed
     */
    public CompletableFuture<Void> uploadProvisioningProfileFile(
        String provisioningProfileId, String fileName, InputStream fileContents
    ) {
        return uploadProvisioningProfileFile(provisioningProfileId, new FileStreamField(fileContents, fileName));
    }
    
    /**
     * Deletes a provisioning profile (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/deleteProvisioningProfile/). 
     * Please note that in order to delete a provisioning profile you need to delete MT accounts connected to it first.
     * Method is accessible only with API access token.
     * @param id provisioning profile id
     * @return completable future resolving when provisioning profile is deleted
     */
    public CompletableFuture<Void> deleteProvisioningProfile(String id) {
        if (isNotJwtToken()) return handleNoAccessError("deleteProvisioningProfile");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/provisioning-profiles/" + id, Method.DELETE);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((body) -> null);
    }
    
    /**
     * Updates existing provisioning profile data (see
     * https://metaapi.cloud/docs/provisioning/api/provisioningProfile/updateProvisioningProfile/).
     * Method is accessible only with API access token.
     * @param id provisioning profile id
     * @param provisioningProfile updated provisioning profile
     * @return completable future resolving when provisioning profile is updated
     */
    public CompletableFuture<Void> updateProvisioningProfile(
        String id, ProvisioningProfileUpdateDto provisioningProfile
    ) {
        if (isNotJwtToken()) return handleNoAccessError("updateProvisioningProfile");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/provisioning-profiles/" + id, Method.PUT);
        opts.getHeaders().put("auth-token", token);
        opts.setBody(provisioningProfile);
        return httpClient.request(opts).thenApply((body) -> null);
    }
    
    private CompletableFuture<Void> uploadProvisioningProfileFile(
        String provisioningProfileId, FileStreamField file
    ) {
        if (isNotJwtToken()) return handleNoAccessError("uploadProvisioningProfileFile");
        HttpRequestOptions opts = new HttpRequestOptions(
            host + "/users/current/provisioning-profiles/" + provisioningProfileId + "/" + file.getFileName(),
            Method.PUT);
        Map<String, Object> formData = new HashMap<>();
        formData.put("file", file);
        opts.setBody(formData);
        opts.getHeaders().put("auth-token", token);
        return httpClient.request(opts).thenApply((body) -> null);
    }
}