package cloud.metaapi.sdk.clients.meta_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.models.*;
import cloud.metaapi.sdk.clients.mocks.HttpClientMock;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Tests {@link HistoricalMarketDataClient}
 */
class HistoricalMarketDataClientTest {

  private final String marketDataClientApiUrl = "https://mt-market-data-client-api-v1.vint-hill.agiliumtrade.ai";
  private HistoricalMarketDataClient client;
  private HttpClientMock httpClient;
  
  @BeforeEach
  void setUp() throws ValidationException {
    httpClient = new HttpClientMock((opts) -> CompletableFuture.completedFuture("empty"));
    client = new HistoricalMarketDataClient(httpClient, "header.payload.sign", "vint-hill",
      "agiliumtrade.agiliumtrade.ai");
  }

  /**
   * Tests {@link HistoricalMarketDataClient#getHistoricalCandles}
   */
  @Test
  void testDownloadsHistoricalCandlesFromApi() throws Exception {
    List<MetatraderCandle> expectedResponse = Lists.list(new MetatraderCandle() {{
      symbol = "AUDNZD";
      timeframe = "15m";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      open = 1.03297;
      high = 1.06309;
      low = 1.02705;
      close = 1.043;
      tickVolume = 1435;
      spread = 17;
      volume = 345;
    }});
    httpClient.setRequestMock((actualOptions) -> {
      try {
        HttpRequestOptions expectedOptions = new HttpRequestOptions(
          marketDataClientApiUrl + "/users/current/accounts/accountId/historical-market-data/"
            + "symbols/AUDNZD/timeframes/15m/candles", Method.GET);
        expectedOptions.getQueryParameters().put("startTime", new IsoTime("2020-04-07T03:45:00.000Z"));
        expectedOptions.getQueryParameters().put("limit", 1);
        expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
        assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
        return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
        return null;
      }
    });
    List<MetatraderCandle> actualResponse = client.getHistoricalCandles("accountId", "AUDNZD",
      "15m", new IsoTime("2020-04-07T03:45:00.000Z"), 1).join();
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
  
  /**
   * Tests {@link HistoricalMarketDataClient#getHistoricalTicks}
   */
  @Test
  void testDownloadsHistoricalTicksFromApi() throws Exception {
    List<MetatraderTick> expectedResponse = Lists.list(new MetatraderTick() {{
      symbol = "AUDNZD";
      time = new IsoTime("2020-04-07T03:45:00.000Z");
      brokerTime = "2020-04-07 06:45:00.000";
      bid = 1.05297;
      ask = 1.05309;
      last = 0.5298;
      volume = 0.13;
      side = "buy";
    }});
    httpClient.setRequestMock((actualOptions) -> {
      try {
        HttpRequestOptions expectedOptions = new HttpRequestOptions(
          marketDataClientApiUrl + "/users/current/accounts/accountId/historical-market-data/symbols/AUDNZD/ticks", Method.GET);
        expectedOptions.getQueryParameters().put("startTime", new IsoTime("2020-04-07T03:45:00.000Z"));
        expectedOptions.getQueryParameters().put("offset", 0);
        expectedOptions.getQueryParameters().put("limit", 1);
        expectedOptions.getHeaders().put("auth-token", "header.payload.sign");
        assertThat(actualOptions).usingRecursiveComparison().isEqualTo(expectedOptions);
        return CompletableFuture.completedFuture(JsonMapper.getInstance().writeValueAsString(expectedResponse));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
        return null;
      }
    });
    List<MetatraderTick> actualResponse = client.getHistoricalTicks("accountId", "AUDNZD",
      new IsoTime("2020-04-07T03:45:00.000Z"), 0, 1).join();
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}