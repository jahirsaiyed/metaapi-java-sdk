package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.FileStreamField;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient.ExpertAdvisorDto;
import cloud.metaapi.sdk.clients.meta_api.ExpertAdvisorClient.NewExpertAdvisorDto;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link ExpertAdvisorClient}
 */
class ExpertAdvisorClientTest {

  private static ObjectMapper jsonMapper = JsonMapper.getInstance();
  private final String provisioningApiUrl = "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai";
  private ExpertAdvisorClient expertAdvisorClient;
  private HttpClientMock httpClient;
  
  @BeforeEach
  void setUp() throws Exception {
    httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
    expertAdvisorClient = new ExpertAdvisorClient(httpClient, "header.payload.sign");
  }

  /**
   * Tests {@link ExpertAdvisorClient#getExpertAdvisors(String)}
   */
  @Test
  void testRetrievesExpertAdvisorsFromApi() {
    List<ExpertAdvisorDto> expectedResponse = Lists.list(new ExpertAdvisorDto() {{
      expertId = "my-ea";
      period = "1H";
      symbol = "EURUSD";
      fileUploaded = false;
    }});
    httpClient.setRequestMock((actualOptions) -> {
      try {
        HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
          + "/users/current/accounts/id/expert-advisors", Method.GET);
        expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
        assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
        return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedResponse));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
        return null;
      }
    });
    List<ExpertAdvisorDto> actualResponse = expertAdvisorClient.getExpertAdvisors("id").join();
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
  
  /**
   * Tests {@link ExpertAdvisorClient#getExpertAdvisor(String, String)}
   */
  @Test
  void testRetrievesExpertAdvisorFromApi() {
    ExpertAdvisorDto expectedResponse = new ExpertAdvisorDto() {{
      expertId = "my-ea";
      period = "1H";
      symbol = "EURUSD";
      fileUploaded = false;
    }};
    httpClient.setRequestMock((actualOptions) -> {
      try {
        HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
          + "/users/current/accounts/id/expert-advisors/my-ea", Method.GET);
        expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
        assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
        return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedResponse));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
        return null;
      }
    });
    ExpertAdvisorDto actualResponse = expertAdvisorClient.getExpertAdvisor("id", "my-ea").join();
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
  
  /**
   * Tests {@link ExpertAdvisorClient#deleteExpertAdvisor(String, String)}
   */
  @Test
  void testDeletesExpertAdvisorViaApi() {
    httpClient.setRequestMock((actualOptions) -> {
      HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
        + "/users/current/accounts/id/expert-advisors/my-ea", Method.DELETE);
      expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
      assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
      return CompletableFuture.completedFuture("empty response");
    });
    expertAdvisorClient.deleteExpertAdvisor("id", "my-ea").join();
  }
  
  /**
   * Tests {@link ExpertAdvisorClient#updateExpertAdvisor(String, String, NewExpertAdvisorDto)}
   */
  @Test
  void testUpdatesExpertAdvisorViaApi() {
    NewExpertAdvisorDto update = new NewExpertAdvisorDto() {{
      preset = "a2V5MT12YWx1ZTEKa2V5Mj12YWx1ZTIKa2V5Mz12YWx1ZTMKc3VwZXI9dHJ1ZQ==";
      period = "15m";
      symbol = "EURUSD";
    }};
    httpClient.setRequestMock((actualOptions) -> {
      HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
        + "/users/current/accounts/id/expert-advisors/my-ea", Method.PUT);
      expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
      expectedOptions.setBody(update);
      assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
      return CompletableFuture.completedFuture("empty response");
    });
    expertAdvisorClient.updateExpertAdvisor("id", "my-ea", update).join();
  }
  
  /**
   * Tests {@link ExpertAdvisorClient#uploadExpertAdvisorFile(String, String, InputStream)}
   */
  @Test
  void testUploadsFileToAExpertAdvisorViaApi() throws UnsupportedEncodingException {
    InputStream file = new ByteArrayInputStream("test".getBytes("utf-8"));
    httpClient.setRequestMock((actualOptions) -> {
      HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
        + "/users/current/accounts/id/expert-advisors/my-ea/file", Method.PUT);
      Map<String, Object> formData = new HashMap<>();
      formData.put("file", new FileStreamField(file, "file"));
      expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
      expectedOptions.setBody(formData);
      assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
      return CompletableFuture.completedFuture("empty response");
    });
    expertAdvisorClient.uploadExpertAdvisorFile("id", "my-ea", file).join();
  }
}
