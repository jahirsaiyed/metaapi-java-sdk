package cloud.metaapi.sdk.clients.copy_factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.JsonMapper;
import cloud.metaapi.sdk.clients.copy_factory.models.*;

/**
 * Tests {@link ConfigurationClient}
 */
class ConfigurationClientTest {

    private final static String copyFactoryApiUrl = "https://trading-api-v1.agiliumtrade.agiliumtrade.ai";
    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private ConfigurationClient copyFactoryClient;
    private HttpClientMock httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
    
    @BeforeEach
    void setUp() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "header.payload.sign");
    }

    /**
     * Tests {@link ConfigurationClient#generateAccountId()}
     */
    @Test
    void testGeneratesAccountId() {
        assertEquals(64, copyFactoryClient.generateAccountId().length());
    }
    
    /**
     * Tests {@link ConfigurationClient#updateAccount(String, CopyFactoryAccountUpdate)}
     */
    @Test
    void testUpdatesCopyFactoryAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/configuration/accounts/" + 
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Method.PUT);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.setBody(new CopyFactoryAccountUpdate() {{
                name = "Demo account";
                connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
                reservedMarginFraction = 0.25;
                subscriptions = Lists.list(new CopyFactoryStrategySubscription() {{
                    strategyId = "ABCD";
                    multiplier = 1.0;
                }});
            }});
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        copyFactoryClient.updateAccount("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 
            new CopyFactoryAccountUpdate() {{
                name = "Demo account";
                connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
                reservedMarginFraction = 0.25;
                subscriptions = Lists.list(new CopyFactoryStrategySubscription() {{
                    strategyId = "ABCD";
                    multiplier = 1.0;
                }});
            }}
        ).get();
    }
    
    /**
     * Tests {@link ConfigurationClient#updateAccount(String, CopyFactoryAccountUpdate)}
     */
    @Test
    void testDoesNotUpdatesCopyFactoryAccountViaApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.updateAccount("id", new CopyFactoryAccountUpdate() {{}}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke updateAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#getAccounts()}
     */
    @Test
    void testRetrieveCopyFactoryAccountsFromApi() throws Exception {
        List<CopyFactoryAccount> expectedAccounts = Lists.list(new CopyFactoryAccount() {{
            _id = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            name = "Demo account";
            connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
            reservedMarginFraction = 0.25;
            subscriptions = Lists.list(new CopyFactoryStrategySubscription() {{
                strategyId = "ABCD";
                multiplier = 1.0;
            }});
        }});
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    copyFactoryApiUrl + "/users/current/configuration/accounts", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedAccounts));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<CopyFactoryAccount> actualAccounts = copyFactoryClient.getAccounts().get();
        assertThat(actualAccounts).usingRecursiveComparison().isEqualTo(expectedAccounts);
    }
    
    /**
     * Tests {@link ConfigurationClient#getAccounts()}
     */
    @Test
    void testDoesNotRetrieveCopyFactoryAccountsFromApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.getAccounts().get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getAccounts method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#removeAccount(String)}
     */
    @Test
    void testRemovesCopyFactoryAccountViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/configuration/accounts/" + 
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Method.DELETE);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        copyFactoryClient.removeAccount("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").get();
    }
    
    /**
     * Tests {@link ConfigurationClient#removeAccount(String)}
     */
    @Test
    void testDoesNotRemoveCopyFactoryAccountViaApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.removeAccount("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke removeAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#generateStrategyId()}
     */
    @Test
    void testGeneratesStrategyId() throws Exception {
        StrategyId expectedId = new StrategyId() {{ id = "ABCD"; }};
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    copyFactoryApiUrl + "/users/current/configuration/unused-strategy-id", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedId));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        StrategyId actualId = copyFactoryClient.generateStrategyId().get();
        assertThat(actualId).usingRecursiveComparison().isEqualTo(expectedId);
    }
    
    /**
     * Tests {@link ConfigurationClient#generateStrategyId()}
     */
    @Test
    void testDoesNotGeneratesStrategyIdWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.generateStrategyId().get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke generateStrategyId method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#updateStrategy(String, CopyFactoryStrategyUpdate)}
     */
    @Test
    void testUpdatesStrategyViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/configuration/strategies/ABCD", Method.PUT);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.setBody(new CopyFactoryStrategyUpdate() {{
                name = "Test strategy";
                positionLifecycle = "hedging";
                connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
                maxTradeRisk = 0.1;
                stopOutRisk = new CopyFactoryStrategyStopOutRisk() {{
                    value = 0.4;
                    startTime = new IsoTime("2020-08-24T00:00:00.000Z");
                }};
                timeSettings = new CopyFactoryStrategyTimeSettings() {{
                    lifetimeInHours = 192;
                    openingIntervalInMinutes = 5;
                }};
            }});
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        copyFactoryClient.updateStrategy("ABCD", new CopyFactoryStrategyUpdate() {{
            name = "Test strategy";
            positionLifecycle = "hedging";
            connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
            maxTradeRisk = 0.1;
            stopOutRisk = new CopyFactoryStrategyStopOutRisk() {{
                value = 0.4;
                startTime = new IsoTime("2020-08-24T00:00:00.000Z");
            }};
            timeSettings = new CopyFactoryStrategyTimeSettings() {{
                lifetimeInHours = 192;
                openingIntervalInMinutes = 5;
            }};
        }}).get();
    }
    
    /**
     * Tests {@link ConfigurationClient#updateStrategy(String, CopyFactoryStrategyUpdate)}
     */
    @Test
    void testDoesNotUpdatesStrategyViaApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.updateStrategy("ABCD", new CopyFactoryStrategyUpdate() {{}}).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke updateStrategy method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#getStrategies()}
     */
    @Test
    void testRetrieveStrategiesFromApi() throws Exception {
        List<CopyFactoryStrategy> expectedStrategies = Lists.list(new CopyFactoryStrategy() {{
            _id = "ABCD";
            platformCommissionRate = 0.01;
            name = "Test strategy";
            positionLifecycle = "hedging";
            connectionId = "e8867baa-5ec2-45ae-9930-4d5cea18d0d6";
            maxTradeRisk = 0.1;
            stopOutRisk = new CopyFactoryStrategyStopOutRisk() {{
                value = 0.4;
                startTime = new IsoTime("2020-08-24T00:00:00.000Z");
            }};
            timeSettings = new CopyFactoryStrategyTimeSettings() {{
                lifetimeInHours = 192;
                openingIntervalInMinutes = 5;
            }};
        }});
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    copyFactoryApiUrl + "/users/current/configuration/strategies", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedStrategies));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<CopyFactoryStrategy> actualStrategies = copyFactoryClient.getStrategies().get();
        assertThat(actualStrategies).usingRecursiveComparison().isEqualTo(expectedStrategies);
    }
    
    /**
     * Tests {@link ConfigurationClient#getStrategies()}
     */
    @Test
    void testDoesNotRetrieveStrategiesFromApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.getStrategies().get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getStrategies method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link ConfigurationClient#removeStrategy(String)}
     */
    @Test
    void testRemovesStrategyViaApi() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/configuration/strategies/ABCD", Method.DELETE);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        copyFactoryClient.removeStrategy("ABCD").get();
    }
    
    /**
     * Tests {@link ConfigurationClient#removeStrategy(String)}
     */
    @Test
    void testDoesNotRemoveStrategyViaApiWithAccountToken() throws Exception {
        copyFactoryClient = new ConfigurationClient(httpClient, "token");
        try {
            copyFactoryClient.removeStrategy("id").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke removeStrategy method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
}