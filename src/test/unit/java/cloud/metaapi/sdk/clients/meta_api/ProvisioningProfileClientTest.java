package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.FileStreamField;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.*;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link ProvisioningProfileClient}
 */
class ProvisioningProfileClientTest {

    private final String provisioningApiUrl = "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai";
    private ProvisioningProfileClient provisioningClient;
    private HttpClientMock httpClient;
    
    @BeforeEach
    void setUp() {
        httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
        provisioningClient = new ProvisioningProfileClient(httpClient, "header.payload.sign");
    }

    /**
     * Tests {@link ProvisioningProfileClient#getProvisioningProfiles(Integer, String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfileDto")
    void testRetrievesProvisioningProfilesFromApi(ProvisioningProfileDto profile) throws Exception {
        List<ProvisioningProfileDto> expectedResponse = Lists.list(profile);
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/provisioning-profiles", Method.GET);
                expectedOptions.getQueryParameters().put("version", 5);
                expectedOptions.getQueryParameters().put("status", "active");
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<ProvisioningProfileDto> actualResponse = provisioningClient.getProvisioningProfiles(5, "active").get();
        assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#getProvisioningProfiles(Integer, String)}
     */
    @Test
    void testDoesNotRetrieveProvisioningProfilesFromApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.getProvisioningProfiles(5, "active").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getProvisioningProfiles method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#getProvisioningProfile(String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfileDto")
    void testRetrievesProvisioningProfileFromApI(ProvisioningProfileDto expectedProfile) throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/provisioning-profiles/id", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedProfile));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        ProvisioningProfileDto actualProfile = provisioningClient.getProvisioningProfile("id").get();
        assertThat(actualProfile).usingRecursiveComparison().isEqualTo(expectedProfile);
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#getProvisioningProfile(String)}
     */
    @Test
    void testDoesNotRetrieveProvisioningProfileFromApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.getProvisioningProfile("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getProvisioningProfile method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#createProvisioningProfile(NewProvisioningProfileDto)}
     */
    @Test
    void testCreatesProvisioningProfileViaApi() throws Exception {
        ProvisioningProfileIdDto expectedResponse = new ProvisioningProfileIdDto();
        expectedResponse.id = "id";
        NewProvisioningProfileDto profile = new NewProvisioningProfileDto();
        profile.name = "name";
        profile.version = 4;
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/provisioning-profiles", Method.POST);
                expectedOptions.setBody(profile);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        ProvisioningProfileIdDto actualResponse = provisioningClient.createProvisioningProfile(profile).get();
        assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#createProvisioningProfile(NewProvisioningProfileDto)}
     */
    @Test
    void testDoesNotCreateProvisioningProfileViaApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.createProvisioningProfile(new NewProvisioningProfileDto() {{}}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke createProvisioningProfile method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#uploadProvisioningProfileFile(String, String, InputStream)}
     */
    @Test
    void testUploadsFileToAProvisioningProfileViaApi() throws Exception {
        InputStream file = new ByteArrayInputStream("test".getBytes("utf-8"));
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/provisioning-profiles/id/servers.dat", Method.PUT);
            Map<String, Object> formData = new HashMap<>();
            formData.put("file", new FileStreamField(file, "servers.dat"));
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.setBody(formData);
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.uploadProvisioningProfileFile("id", "servers.dat", file).get();
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#uploadProvisioningProfileFile(String, String, InputStream)}
     */
    @Test
    void testDoesNotUploadProvisioningProfileFileViaApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.uploadProvisioningProfileFile("id", "servers.dat", Mockito.mock(InputStream.class)).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke uploadProvisioningProfileFile method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#deleteProvisioningProfile(String)}
     */
    @Test
    void testDeletesProvisioningProfileViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/provisioning-profiles/id", Method.DELETE);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.deleteProvisioningProfile("id").get();
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#deleteProvisioningProfile(String)}
     */
    @Test
    void testDoesNotDeleteProvisioningProfileViaApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.deleteProvisioningProfile("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke deleteProvisioningProfile method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#updateProvisioningProfile(String, ProvisioningProfileUpdateDto)}
     */
    @Test
    void testUpdatesProvisioningProfileViaUpdate() throws Exception {
        ProvisioningProfileUpdateDto updateProfile = new ProvisioningProfileUpdateDto();
        updateProfile.name = "new name";
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/provisioning-profiles/id", Method.PUT);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.setBody(updateProfile);
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.updateProvisioningProfile("id", updateProfile).get();
    }
    
    /**
     * Tests {@link ProvisioningProfileClient#updateProvisioningProfile(String, ProvisioningProfileUpdateDto)}
     */
    @Test
    void testDoesNotUpdateProvisioningProfileViaApiWithAccountToken() throws Exception {
        provisioningClient = new ProvisioningProfileClient(httpClient, "token");
        try {
            provisioningClient.updateProvisioningProfile("id", new ProvisioningProfileUpdateDto()).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke updateProvisioningProfile method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    private static Stream<Arguments> provideProvisioningProfileDto() {
        ProvisioningProfileDto profile = new ProvisioningProfileDto();
        profile._id = "id";
        profile.name = "name";
        profile.version = 4;
        profile.status = "active";
        return Stream.of(Arguments.of(profile));
    }
}