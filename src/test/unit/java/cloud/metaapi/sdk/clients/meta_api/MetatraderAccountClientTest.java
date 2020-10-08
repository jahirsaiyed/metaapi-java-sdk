package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.*;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.*;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link MetatraderAccountClient}
 */
class MetatraderAccountClientTest {

    private final String provisioningApiUrl = "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai";
    private MetatraderAccountClient accountClient;
    private HttpClientMock httpClient;
    
    @BeforeEach
    void setUp() {
        httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
        accountClient = new MetatraderAccountClient(httpClient, "header.payload.sign");
    }

    /**
     * Tests {@link MetatraderAccountClient#getAccounts(AccountsFilter)}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderAccountDto")
    void testRetrievesMetatraderAccountsFromApi(MetatraderAccountDto account) throws Exception {
        List<MetatraderAccountDto> expectedResponse = Lists.list(account);
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts", Method.GET);
                expectedOptions.getQueryParameters().put("provisioningProfileId", "f9ce1f12-e720-4b9a-9477-c2d4cb25f076");
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<MetatraderAccountDto> actualResponse = accountClient.getAccounts(new AccountsFilter() {{
            provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
        }}).get();
        assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#getAccounts(AccountsFilter)}
     */
    @Test
    void testDoesNotRetrieveMetatraderAccountsFromApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.getAccounts(new AccountsFilter() {{
                provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
            }}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getAccounts method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
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
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedAccount));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderAccountDto actualAccount = accountClient.getAccount("id").get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#getAccountByToken()}
     */
    @ParameterizedTest
    @MethodSource("provideMetatraderAccountDto")
    void testRetrievesMetatraderAccountByTokenFromApi(MetatraderAccountDto expectedAccount) throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts/accessToken/token", Method.GET);
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedAccount));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderAccountDto actualAccount = accountClient.getAccountByToken().get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#getAccountByToken()}
     */
    @Test
    void testDoesNotRetrieveMetatraderAccountByTokenViaApiWithApiToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "header.payload.sign");
        try {
            accountClient.getAccountByToken().get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getAccountByToken method, because you have connected with API access token. "
                + "Please use account access token to invoke this method.",
                e.getCause().getMessage()
            );
        }
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
        newAccount.application = "MetaApi";
        newAccount.type = "cloud";
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    provisioningApiUrl + "/users/current/accounts", Method.POST);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                expectedOptions.setBody(newAccount);
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedId));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderAccountIdDto actualId = accountClient.createAccount(newAccount).get();
        assertThat(actualId).usingRecursiveComparison().isEqualTo(expectedId);
    }
    
    /**
     * Tests {@link MetatraderAccountClient#createAccount(NewMetatraderAccountDto)}
     */
    @Test
    void testDoesNotCreateMetatraderAccountViaApiWithAcountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.createAccount(new NewMetatraderAccountDto() {{}}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke createAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deployAccount(String)}
     */
    @Test
    void testDeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/deploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        accountClient.deployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deployAccount(String)}
     */
    @Test
    void testDoesNotDeployMetatraderAccountViaApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.deployAccount("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke deployAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    /**
     * Tests {@link MetatraderAccountClient#undeployAccount(String)}
     */
    @Test
    void testUndeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/undeploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        accountClient.undeployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#undeployAccount(String)}
     */
    @Test
    void testDoesNotUndeployMetatraderAccountViaApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.undeployAccount("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke undeployAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    /**
     * Tests {@link MetatraderAccountClient#redeployAccount(String)}
     */
    @Test
    void testRedeploysMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id/redeploy", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        accountClient.redeployAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#redeployAccount(String)}
     */
    @Test
    void testDoesNotRedeployMetatraderAccountViaApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.redeployAccount("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke redeployAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deleteAccount(String)}
     */
    @Test
    void testDeletesMetatraderAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id", Method.DELETE);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        accountClient.deleteAccount("id").get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#deleteAccount(String)}
     */
    @Test
    void testDoesNotDeleteMetatraderAccountViaApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.deleteAccount("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke deleteAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    /**
     * Tests {@link MetatraderAccountClient#updateAccount(String, MetatraderAccountUpdateDto)}
     */
    @Test
    void testUpdatesMetatraderAccountViaApi() throws Exception {
        MetatraderAccountUpdateDto updateAccount = new MetatraderAccountUpdateDto();
        updateAccount.name = "new account name";
        updateAccount.password = "new_password007";
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                provisioningApiUrl + "/users/current/accounts/id", Method.PUT);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.setBody(updateAccount);
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture("empty response");
        });
        accountClient.updateAccount("id", updateAccount).get();
    }
    
    /**
     * Tests {@link MetatraderAccountClient#updateAccount(String, MetatraderAccountUpdateDto)}
     */
    @Test
    void testDoesNotUpdateMetatraderAccountViaApiWithAccountToken() throws Exception {
        accountClient = new MetatraderAccountClient(httpClient, "token");
        try {
            accountClient.updateAccount("id", new MetatraderAccountUpdateDto() {{}}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke updateAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        }
    }
    
    private static Stream<Arguments> provideMetatraderAccountDto() {
        MetatraderAccountDto account = new MetatraderAccountDto();
        account._id = "1eda642a-a9a3-457c-99af-3bc5e8d5c4c9";
        account.login = "50194988";
        account.name = "mt5a";
        account.server = "ICMarketsSC-Demo";
        account.provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
        account.magic = 123456;
        account.application = "MetaApi";
        account.connectionStatus = ConnectionStatus.DISCONNECTED;
        account.state = DeploymentState.DEPLOYED;
        account.type = "cloud";
        return Stream.of(Arguments.of(account));
    }
}