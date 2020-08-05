package metaapi.cloudsdk.lib.clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;

import metaapi.cloudsdk.lib.clients.HttpRequestOptions.Method;
import metaapi.cloudsdk.lib.clients.mocks.HttpClientMock;
import metaapi.cloudsdk.lib.clients.models.*;
import metaapi.cloudsdk.lib.clients.models.MetatraderAccountDto.*;

/**
 * Tests {@link MetatraderAccountClient}
 */
class MetatraderAccountClientTest {

    private static final String provisioningApiUrl = "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai";
    private static MetatraderAccountClient provisioningClient;
    private static HttpClientMock httpClient;
    
    @BeforeAll
    static void setUpBeforeClass() {
        httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
        provisioningClient = new MetatraderAccountClient(httpClient, "token");
    }

    /**
     * Tests {@link MetatraderAccountClient#getAccounts(Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderAccountDto")
    void testRetrievesMetatraderAccountsFromApi(MetatraderAccountDto account) throws Exception {
        List<MetatraderAccountDto> expectedResponse = List.of(account);
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts", Method.GET);
                expectedOptions.getQueryParameters().put("provisioningProfileId", "f9ce1f12-e720-4b9a-9477-c2d4cb25f076");
                expectedOptions.getHeaders().put("auth-token", "token");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<MetatraderAccountDto> actualResponse = provisioningClient
            .getAccounts(Optional.of("f9ce1f12-e720-4b9a-9477-c2d4cb25f076")).get();
        assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#getAccount(String)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderAccountDto")
    void testRetrievesMetatraderAccountFromApi(MetatraderAccountDto expectedAccount) throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts/id", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "token");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedAccount));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderAccountDto actualAccount = provisioningClient.getAccount("id").get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#createAccount(NewMetatraderAccountDto)}
     */
    @Test
    void testCreatesMetatraderAccountViaApi() throws Exception {
        MetatraderAccountIdDto expectedId = new MetatraderAccountIdDto();
        expectedId.id = "id";
        NewMetatraderAccountDto newAccount = new NewMetatraderAccountDto();
        newAccount.login = "50194988";
        newAccount.password = "Test1234";
        newAccount.name = "mt5a";
        newAccount.server = "ICMarketsSC-Demo";
        newAccount.provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
        newAccount.magic = 123456;
        newAccount.timeConverter = "icmarkets";
        newAccount.application = "MetaApi";
        newAccount.synchronizationMode = "automatic";
        newAccount.type = "cloud";
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts", Method.POST);
                expectedOptions.getHeaders().put("auth-token", "token");
                expectedOptions.setBody(newAccount);
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedId));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderAccountIdDto actualId = provisioningClient.createAccount(newAccount).get();
        assertThat(actualId).usingRecursiveComparison().isEqualTo(expectedId);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deployAccount(String)}
     */
    @Test
    void testDeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/deploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "token");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.deployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#undeployAccount(String)}
     */
    @Test
    void testUndeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/undeploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "token");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.undeployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#redeployAccount(String)}
     */
    @Test
    void testRedeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/redeploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "token");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.redeployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deleteAccount(String)}
     */
    @Test
    void testDeletesMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id", Method.DELETE);
            expectedOptions.getHeaders().put("auth-token", "token");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.deleteAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#updateAccount(String, MetatraderAccountUpdateDto)}
     */
    @Test
    void testUpdatesMetatraderAccountViaApi() throws Exception {
        MetatraderAccountUpdateDto updateAccount = new MetatraderAccountUpdateDto();
        updateAccount.name = "new account name";
        updateAccount.password = "new_password007";
        updateAccount.server = "ICMarketsSC2-Demo";
        updateAccount.synchronizationMode = "user";
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id", Method.PUT);
            expectedOptions.getHeaders().put("auth-token", "token");
            expectedOptions.setBody(updateAccount);
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        provisioningClient.updateAccount("id", updateAccount).get();
    }
    
    private static Stream<Arguments> provideMetatraderAccountDto() {
        MetatraderAccountDto account = new MetatraderAccountDto();
        account._id = "1eda642a-a9a3-457c-99af-3bc5e8d5c4c9";
        account.login = "50194988";
        account.name = "mt5a";
        account.server = "ICMarketsSC-Demo";
        account.provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
        account.magic = 123456;
        account.timeConverter = "icmarkets";
        account.application = "MetaApi";
        account.connectionStatus = ConnectionStatus.DISCONNECTED;
        account.state = DeploymentState.DEPLOYED;
        account.synchronizationMode = "automatic";
        account.type = "cloud";
        return Stream.of(Arguments.of(account));
    }
}