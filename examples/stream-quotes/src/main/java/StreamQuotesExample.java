import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.meta_api.SynchronizationListener;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderSymbolPrice;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Note: for information on how to use this example code please read
 * https://metaapi.cloud/docs/client/usingCodeExamples
 */
public class StreamQuotesExample {

    private static String token = getEnvOrDefault("TOKEN", "<put in your token here>");
    private static String accountId = getEnvOrDefault("ACCOUNT_ID", "<put in your account id here>");
    
    private static class EURUSDListener extends SynchronizationListener {
        @Override
        public CompletableFuture<Void> onSymbolPriceUpdated(MetatraderSymbolPrice price) {
            if (price.symbol.equals("EURUSD"))
                try {
                    System.out.println("EURUSD price updated " + asJson(price));
                } catch (JsonProcessingException e) {
                    throw new CompletionException(e);
                }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    public static void main(String[] args) {
        try {
            MetaApi api = new MetaApi(token);
            MetatraderAccount account = api.getMetatraderAccountApi().getAccount(accountId).get();
            
            // wait until account is deployed and connected to broker
            System.out.println("Deploying account");
            account.deploy().get();
            System.out.println("Waiting for API server to connect to broker (may take couple of minutes)");
            account.waitConnected(null, null).get();
            
            // connect to MetaApi API
            MetaApiConnection connection = account.connect().get();
            
            SynchronizationListener eurUsdListener = new EURUSDListener();
            connection.addSynchronizationListener(eurUsdListener);
            
            System.out.println("Waiting for SDK to synchronize to terminal state "
                + "(may take some time depending on your history size)");
            connection.waitSynchronized(null, 1200, null).get();
            
            // Add symbol to MarketWatch if not yet added
            connection.subscribeToMarketData("EURUSD").get();
            
            System.out.println("Streaming EURUSD price now...");
            
            while (true) {
                Thread.sleep(1000);
            }
            
        } catch (Exception err) {
            System.err.println(err);
        }
    }
    
    private static String getEnvOrDefault(String name, String defaultValue) {
        String result = System.getenv(name);
        return (result != null ? result : defaultValue);
    }
    
    private static String asJson(Object object) throws JsonProcessingException {
        return JsonMapper.getInstance().writeValueAsString(object);
    }
}