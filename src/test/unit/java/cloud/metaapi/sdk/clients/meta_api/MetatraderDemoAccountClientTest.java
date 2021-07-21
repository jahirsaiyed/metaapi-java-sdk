package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDemoAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT4DemoAccount;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT5DemoAccount;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link MetatraderDemoAccountClient}
 */
class MetatraderDemoAccountClientTest {

    private final String provisioningApiUrl = "https://mt-provisioning-api-v1.agiliumtrade.agiliumtrade.ai";
    private MetatraderDemoAccountClient demoAccountClient;
    private HttpClientMock httpClient;
    
    @BeforeEach
    void setUp() throws ValidationException {
        httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
        demoAccountClient = new MetatraderDemoAccountClient(httpClient, "header.payload.sign");
    }

    /**
     * Tests {@link MetatraderDemoAccountClient#createMT4DemoAccount(String, NewMT4DemoAccount)}
     */
    @Test
    void testCreatesNewMetatrader4DemoAccountViaApi() {
        MetatraderDemoAccountDto expected = new MetatraderDemoAccountDto() {{
            login = "12345";
            password = "qwerty";
            serverName = "HugosWay-Demo3";
            investorPassword = "qwerty";
        }};
        NewMT4DemoAccount newAccount = new NewMT4DemoAccount() {{
            balance = 10;
            email = "test@test.com";
            leverage = 15;
            serverName = "server";
        }};
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
                    + "/users/current/provisioning-profiles/profileId1/mt4-demo-accounts", Method.POST);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                expectedOptions.setBody(newAccount);
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expected));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderDemoAccountDto account = demoAccountClient.createMT4DemoAccount("profileId1", newAccount).join();
        assertThat(account).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetatraderDemoAccountClient#createMT4DemoAccount(String, NewMT4DemoAccount)}
     */
    @Test
    void testDoesNotCreateMetatrader4AccountViaApiWithAccountToken() throws Exception {
        demoAccountClient = new MetatraderDemoAccountClient(httpClient, "token");
        try {
            demoAccountClient.createMT4DemoAccount("profileId1", null).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke createMT4DemoAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link MetatraderDemoAccountClient#createMT5DemoAccount(String, NewMT5DemoAccount)}
     */
    @Test
    void testCreatesNewMetatrader5DemoAccountViaApi() {
        MetatraderDemoAccountDto expected = new MetatraderDemoAccountDto() {{
            login = "12345";
            password = "qwerty";
            serverName = "HugosWay-Demo3";
            investorPassword = "qwerty";
        }};
        NewMT5DemoAccount newAccount = new NewMT5DemoAccount() {{
            balance = 10;
            email = "test@test.com";
            leverage = 15;
            serverName = "server";
        }};
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(provisioningApiUrl
                    + "/users/current/provisioning-profiles/profileId2/mt5-demo-accounts", Method.POST);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                expectedOptions.setBody(newAccount);
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expected));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        MetatraderDemoAccountDto account = demoAccountClient.createMT5DemoAccount("profileId2", newAccount).join();
        assertThat(account).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetatraderDemoAccountClient#createMT5DemoAccount(String, NewMT4DemoAccount)}
     */
    @Test
    void testDoesNotCreateMetatrader5AccountViaApiWithAccountToken() throws Exception {
        demoAccountClient = new MetatraderDemoAccountClient(httpClient, "token");
        try {
            demoAccountClient.createMT5DemoAccount("profileId1", null).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke createMT5DemoAccount method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
}