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
import cloud.metaapi.sdk.clients.copy_factory.models.*;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link TradingClient}
 */
class TradingClientTest {

    private final static String copyFactoryApiUrl = "https://trading-api-v1.agiliumtrade.agiliumtrade.ai";
    private static ObjectMapper jsonMapper = JsonMapper.getInstance();
    private TradingClient tradingClient;
    private HttpClientMock httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
    
    @BeforeEach
    void setUp() throws Exception {
        tradingClient = new TradingClient(httpClient, "header.payload.sign");
    }
    
    /**
     * Tests {@link TradingClient#resynchronize(String, List)}
     */
    @Test
    void testResynchronizesCopyFactoryAccount() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/accounts/"
                    + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/resynchronize", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            expectedOptions.getQueryParameters().put("strategyId", Lists.list("ABCD"));
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        tradingClient
            .resynchronize("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Lists.list("ABCD")).get();
    }
    
    /**
     * Tests {@link TradingClient#resynchronize(String, List)}
     */
    @Test
    void testDoesNotResynchronizeAccountWithAccountToken() throws Exception {
        tradingClient = new TradingClient(httpClient, "token");
        try {
            tradingClient.resynchronize("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", null).get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke resynchronize method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link TradingClient#getStopouts(String)}
     */
    @Test
    void testRetrieveStopouts() throws Exception {
        List<CopyFactoryStrategyStopout> expectedStopouts = Lists.list(new CopyFactoryStrategyStopout() {{
            reason = "max-drawdown";
            stoppedAt = new IsoTime("2020-08-08T07:57:30.328Z");
            strategy = new CopyFactoryStrategyIdAndName() {{
                id = "ABCD";
                name = "Strategy";
            }};
            reasonDescription = "total strategy equity drawdown exceeded limit";
        }});
        httpClient.setRequestMock((actualOptions) -> {
            try {
                HttpRequestOptions expectedOptions = new HttpRequestOptions(
                    copyFactoryApiUrl + "/users/current/accounts/"
                        + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/stopouts", Method.GET);
                expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
                assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
                return CompletableFuture.completedFuture(jsonMapper.writeValueAsString(expectedStopouts));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
        List<CopyFactoryStrategyStopout> actualStopouts = tradingClient
            .getStopouts("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").get();
        assertThat(actualStopouts).usingRecursiveComparison().isEqualTo(expectedStopouts);
    }
    
    /**
     * Tests {@link TradingClient#getStopouts(String)}
     */
    @Test
    void testDoesNotRetrieveStopoutsWithAccountToken() throws Exception {
        tradingClient = new TradingClient(httpClient, "token");
        try {
            tradingClient.getStopouts("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke getStopouts method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
    
    /**
     * Tests {@link TradingClient#resetStopout(String, String)}
     */
    @Test
    void testResetsStopout() throws Exception {
        httpClient.setRequestMock((actualOptions) -> {
            HttpRequestOptions expectedOptions = new HttpRequestOptions(
                copyFactoryApiUrl + "/users/current/accounts/"
                    + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/"
                    + "stopouts/daily-equity/reset", Method.POST);
            expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
            assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
            return CompletableFuture.completedFuture(null);
        });
        tradingClient
            .resetStopout("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "daily-equity").get();
    }
    
    /**
     * Tests {@link TradingClient#resetStopout(String, String)}
     */
    @Test
    void testDoesResetStopoutWithAccountToken() throws Exception {
        tradingClient = new TradingClient(httpClient, "token");
        try {
            tradingClient
                .resetStopout("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "daily-equity").get();
        } catch (ExecutionException e) {
            assertEquals(
                "You can not invoke resetStopout method, because you have connected with account access token. "
                + "Please use API access token from https://app.metaapi.cloud/token page to invoke this method.",
                e.getCause().getMessage()
            );
        };
    }
}