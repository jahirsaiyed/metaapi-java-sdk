package cloud.metaapi.sdk.clients.meta_api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.FileStreamField;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;

/**
 * metaapi.cloud expert advisor API client (see https://metaapi.cloud/docs/provisioning/)
 */
public class ExpertAdvisorClient extends MetaApiClient {
  
  /**
   * Constructs client instance. Domain is set to {@code agiliumtrade.agiliumtrade.ai} 
   * @param httpClient HTTP client
   * @param token authorization token
   */
  public ExpertAdvisorClient(HttpClient httpClient, String token) {
    super(httpClient, token);
  }
  
  /**
   * Constructs client instance
   * @param httpClient HTTP client
   * @param token authorization token
   * @param domain domain to connect to
   */
  public ExpertAdvisorClient(HttpClient httpClient, String token, String domain) {
    super(httpClient, token, domain);
  }
  
  /**
   * Expert advisor model
   */
  public static class ExpertAdvisorDto {
    /**
     * Expert advisor id
     */
    public String expertId;
    /**
     * Expert advisor period
     */
    public String period;
    /**
     * Expert advisor symbol
     */
    public String symbol;
    /**
     * True if expert file was uploaded
     */
    public boolean fileUploaded;
  }
  
  /**
   * Retrieves expert advisors by account id (see
   * https://metaapi.cloud/docs/provisioning/api/expertAdvisor/readExpertAdvisors/)
   * Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @return completable future resolving with expert advisors found
   */
  public CompletableFuture<List<ExpertAdvisorDto>> getExpertAdvisors(String accountId) {
    if (isNotJwtToken()) return handleNoAccessError("getExpertAdvisors");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + accountId + "/expert-advisors", Method.GET);
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, ExpertAdvisorDto[].class)
      .thenApply(array -> Arrays.asList(array));
  }
  
  /**
   * Retrieves an expert advisor by id (see
   * https://metaapi.cloud/docs/provisioning/api/expertAdvisor/readExpertAdvisor/).
   * Thrown an error if expert is not found. Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @param expertId expert advisor id
   * @return completable future resolving with expert advisor found
   */
  public CompletableFuture<ExpertAdvisorDto> getExpertAdvisor(String accountId, String expertId) {
    if (isNotJwtToken()) return handleNoAccessError("getExpertAdvisor");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + accountId + "/expert-advisors/" + expertId, Method.GET);
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, ExpertAdvisorDto.class);
  }
  
  /**
   * Updated expert advisor data
   */
  public static class NewExpertAdvisorDto {
    /**
     * Expert advisor period. For MetaTrader 4 allowed periods are 1m, 5m, 15m, 30m, 1h,
     * 4h, 1d, 1w, 1mn For MetaTrader 5 allowed periods are 1m, 2m, 3m, 4m, 5m, 6m, 10m,
     * 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h, 6h, 8h, 12h, 1d, 1w, 1mn
     */
    public String period;
    /**
     * Expert advisor symbol
     */
    public String symbol;
    /**
     * Base64-encoded preset file
     */
    public String preset;
  }
  
  /**
   * Updates or creates expert advisor data (see
   * https://metaapi.cloud/docs/provisioning/api/expertAdvisor/updateExpertAdvisor/).
   * Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @param expertId expert id
   * @param expert new expert advisor data
   * @return completable future resolving when expert advisor is updated
   */
  public CompletableFuture<Void> updateExpertAdvisor(String accountId, String expertId, NewExpertAdvisorDto expert) {
    if (isNotJwtToken()) return handleNoAccessError("updateExpertAdvisor");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + accountId + "/expert-advisors/" + expertId, Method.PUT);
    opts.getHeaders().put("auth-token", token);
    opts.setBody(expert);
    return httpClient.request(opts).thenApply(body -> null);
  }
  
  /**
   * Uploads an expert advisor file (see https://metaapi.cloud/docs/provisioning/api/expertAdvisor/uploadEAFile/)
   * Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @param expertId expert id
   * @param filePath file path to a file to upload
   * @return completable future resolving when file upload is completed
   */
  public CompletableFuture<Void> uploadExpertAdvisorFile(String accountId, String expertId, String filePath) {
    return CompletableFuture.runAsync(() -> {
      try {
        uploadExpertAdvisorFile(accountId, expertId, new FileStreamField(new File(filePath))).join();
      } catch (FileNotFoundException e) {
        throw new CompletionException(e);
      }
    });
  }
  
  /**
   * Uploads an expert advisor file (see https://metaapi.cloud/docs/provisioning/api/expertAdvisor/uploadEAFile/)
   * Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @param expertId expert id
   * @param fileContents input stream containing file contents to upload
   * @return completable future resolving when file upload is completed
   */
  public CompletableFuture<Void> uploadExpertAdvisorFile(String accountId, String expertId, InputStream fileContents) {
    return uploadExpertAdvisorFile(accountId, expertId, new FileStreamField(fileContents, "file"));
  }
  
  private CompletableFuture<Void> uploadExpertAdvisorFile(String accountId, String expertId, FileStreamField file) {
    if (isNotJwtToken()) return handleNoAccessError("uploadExpertAdvisorFile");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + accountId + "/expert-advisors/" + expertId + "/file", Method.PUT);
    Map<String, Object> formData = new ConcurrentHashMap<>();
    formData.put("file", file);
    opts.setBody(formData);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply(body -> null);
  }
  
  /**
   * Deletes an expert advisor (see https://metaapi.cloud/docs/provisioning/api/expertAdvisor/deleteExpertAdvisor/)
   * Method is accessible only with API access token
   * @param accountId Metatrader account id
   * @param expertId expert id
   * @return completable future resolving when expert advisor is deleted
   */
  public CompletableFuture<Void> deleteExpertAdvisor(String accountId, String expertId) {
    if (isNotJwtToken()) return handleNoAccessError("deleteExpertAdvisor");
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/"
      + accountId + "/expert-advisors/" + expertId, Method.DELETE);
    opts.getHeaders().put("auth-token", token);
    return httpClient.request(opts).thenApply(body -> null);
  }
}