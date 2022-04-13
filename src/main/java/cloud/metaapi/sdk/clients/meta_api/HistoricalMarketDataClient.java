package cloud.metaapi.sdk.clients.meta_api;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.HttpClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions;
import cloud.metaapi.sdk.clients.MetaApiClient;
import cloud.metaapi.sdk.clients.HttpRequestOptions.Method;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderCandle;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTick;
import cloud.metaapi.sdk.clients.models.IsoTime;

/**
 * metaapi.cloud historical market data API client
 */
public class HistoricalMarketDataClient extends MetaApiClient {
  
  /**
   * Constructs historical market data API client instance
   * @param httpClient HTTP client
   * @param token authorization token
   * @param region region to connect to, or {@code null}
   * @param domain domain to connect to
   */
  public HistoricalMarketDataClient(HttpClient httpClient, String token, String region, String domain) {
    super(httpClient, token, domain);
    if (region != null) {
      List<String> domainLevels = Arrays.asList(domain.split("\\."));
      domain = String.join(".", domainLevels.subList(1, domainLevels.size()));
      this.host = "https://mt-market-data-client-api-v1." + region + "." + domain;
    } else {
      this.host = "https://mt-market-data-client-api-v1." + domain;
    }
  }

  /**
   * Returns historical candles for a specific symbol and timeframe from a MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalCandles/
   * @param accountId MetaTrader account id
   * @param symbol symbol to retrieve candles for (e.g. a currency pair or an index)
   * @param timeframe defines the timeframe according to which the candles must be generated.
   * Allowed values for MT5 are 1m, 2m, 3m, 4m, 5m, 6m, 10m, 12m, 15m, 20m, 30m, 1h, 2h, 3h, 4h,
   * 6h, 8h, 12h, 1d, 1w, 1mn. Allowed values for MT4 are 1m, 5m, 15m 30m, 1h, 4h, 1d, 1w, 1mn
   * @param startTime time to start loading candles from. Note that candles are loaded in backwards
   * direction, so this should be the latest time. Leave {@code null} to request latest candles.
   * @param limit maximum number of candles to retrieve, or {@code null}. Must be less or equal to 1000
   * @return completable future resolving with historical candles downloaded
   */
  public CompletableFuture<List<MetatraderCandle>> getHistoricalCandles(String accountId,
    String symbol, String timeframe, IsoTime startTime, Integer limit) {
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" + accountId
      + "/historical-market-data/symbols/" + symbol + "/timeframes/" + timeframe + "/candles", Method.GET);
    if (startTime != null) {
      opts.getQueryParameters().put("startTime", startTime);
    }
    if (limit != null) {
      opts.getQueryParameters().put("limit", limit);
    }
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, MetatraderCandle[].class).thenApply(array -> Arrays.asList(array));
  }

  /**
   * Returns historical ticks for a specific symbol from a MetaTrader account.
   * See https://metaapi.cloud/docs/client/restApi/api/retrieveMarketData/readHistoricalTicks/
   * @param accountId MetaTrader account id
   * @param symbol symbol to retrieve ticks for (e.g. a currency pair or an index)
   * @param startTime time to start loading ticks from. Note that ticks are loaded in forward
   * direction, so this should be the earliest time. Leave {@code null} to request latest candles.
   * @param offset number of ticks to skip, or {@code null} (you can use it to avoid requesting
   * ticks from previous request twice)
   * @param limit maximum number of ticks to retrieve, or {@code null}. Must be less or equal to 1000
   * @return completable future resolving with historical ticks downloaded
   */
  public CompletableFuture<List<MetatraderTick>> getHistoricalTicks(String accountId,
    String symbol, IsoTime startTime, Integer offset, Integer limit) {
    HttpRequestOptions opts = new HttpRequestOptions(host + "/users/current/accounts/" +
      accountId + "/historical-market-data/symbols/" + symbol + "/ticks", Method.GET);
    if (startTime != null) {
      opts.getQueryParameters().put("startTime", startTime);
    }
    if (offset != null) {
      opts.getQueryParameters().put("offset", offset);
    }
    if (limit != null) {
      opts.getQueryParameters().put("limit", limit);
    }
    opts.getHeaders().put("auth-token", token);
    return httpClient.requestJson(opts, MetatraderTick[].class).thenApply(array -> Arrays.asList(array));
  }
}
