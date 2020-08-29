package mt4;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.MetaApi;
import cloud.metaapi.sdk.MetaApiConnection;
import cloud.metaapi.sdk.MetatraderAccount;
import cloud.metaapi.sdk.ProvisioningProfile;
import cloud.metaapi.sdk.clients.JsonMapper;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.clients.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.models.NewMetatraderAccountDto;
import cloud.metaapi.sdk.clients.models.NewProvisioningProfileDto;

/**
 * Note: for information on how to use this example code please read https://metaapi.cloud/docs/client/usingCodeExamples
 */
public class MetaApiRpcExample {

    private static String token = getEnvOrDefault("NAME", "<put in your token here>");
    private static String login = getEnvOrDefault("LOGIN", "<put in your MT login here>");
    private static String password = getEnvOrDefault("PASSWORD", "<put in your MT password here>");
    private static String serverName = getEnvOrDefault("SERVER", "<put in your MT server name here>");
    private static String brokerSrvFile = getEnvOrDefault("PATH_TO_BROKER_SRV", "/path/to/your/broker.srv");
  
    private static MetaApi api = new MetaApi(token);
    
    public static void main(String[] args) {
        try {
            List<ProvisioningProfile> profiles = api.getProvisioningProfileApi()
                .getProvisioningProfiles(Optional.empty(), Optional.empty()).get();
            
            // create test MetaTrader account profile
            Optional<ProvisioningProfile> profile = profiles.stream()
                .filter(p -> p.getName().equals(serverName))
                .findFirst();
            if (profile.isEmpty()) {
                System.out.println("Creating account profile");
                NewProvisioningProfileDto newDto = new NewProvisioningProfileDto() {{ name = serverName; version = 4; }};
                profile = Optional.of(api.getProvisioningProfileApi().createProvisioningProfile(newDto).get());
                profile.get().uploadFile("broker.srv", brokerSrvFile).get();
            }
            if (profile.isPresent() && profile.get().getStatus().equals("new")) {
                System.out.println("Uploading broker.srv");
                profile.get().uploadFile("broker.srv", brokerSrvFile).get();
            } else {
                System.out.println("Account profile already created");
            }
            
            // Add test MetaTrader account
            List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts(Optional.empty()).get();
            Optional<MetatraderAccount> account = accounts.stream()
                .filter(   a -> a.getLogin().equals(login) 
                        && a.getSynchronizationMode().equals("automatic") 
                        && a.getType().equals("cloud")
                ).findFirst();
            if (account.isEmpty()) {
                System.out.println("Adding MT4 account to MetaApi");
                String mtLogin = login;
                String mtPassword = password;
                ProvisioningProfile provisioningProfile = profile.get();
                account = Optional.of(api.getMetatraderAccountApi().createAccount(new NewMetatraderAccountDto() {{
                    name = "Test account";
                    type = "cloud";
                    login = mtLogin;
                    password = mtPassword;
                    server = serverName;
                    synchronizationMode = "automatic";
                    provisioningProfileId = provisioningProfile.getId();
                    timeConverter = "icmarkets";
                    application = "MetaApi";
                    magic = 1000;
                }}).get());
            } else {
                System.out.println("MT4 account already added to MetaApi");
            }
            
            // wait until account is deployed and connected to broker
            System.out.println("Deploying account");
            account.get().deploy().get();
            System.out.println("Waiting for API server to connect to broker (may take couple of minutes)");
            account.get().waitConnected().get();
            
            // connect to MetaApi API
            MetaApiConnection connection = account.get().connect(Optional.empty()).get();
            
            System.out.println("Waiting for SDK to synchronize to terminal state "
                + "(may take some time depending on your history size)");
            connection.waitSynchronized().get();
            
            // invoke RPC API (replace ticket numbers with actual ticket numbers which exist in your MT account)
            System.out.println("Testing MetaAPI RPC API");
            System.out.println("account information: " + asJson(connection.getAccountInformation().get()));
            System.out.println("positions: " + asJson(connection.getPositions().get()));
            // System.out.println(asJson(connection.getPosition("1234567").get()));
            System.out.println("open orders:" + asJson(connection.getOrders().get()));
            // System.out.println(asJson(connection.getOrder("1234567").get()));
            System.out.println("history orders by ticket: " + asJson(connection.getHistoryOrdersByTicket("1234567").get()));
            System.out.println("history orders by position: " + asJson(connection.getHistoryOrdersByPosition("1234567").get()));
            System.out.println("history orders (~last 3 months): " + asJson(connection.getHistoryOrdersByTimeRange(
                new IsoTime(Date.from(Instant.now().plusSeconds(-90 * 24 * 60 * 60))), 
                new IsoTime(Date.from(Instant.now())), 
                0, 1000).get()));
            System.out.println("history deals by ticket: " + asJson(connection.getDealsByTicket("1234567").get()));
            System.out.println("history deals by position: " + asJson(connection.getDealsByPosition("1234567").get()));
            System.out.println("history deals (~last 3 months): " + asJson(connection.getDealsByTimeRange(
                new IsoTime(Date.from(Instant.now().plusSeconds(-90 * 24 * 60 * 60))), 
                new IsoTime(Date.from(Instant.now())), 
                0, 1000).get()));
            
            // trade
            System.out.println("Submitting pending order");
            MetatraderTradeResponse result = connection.createLimitBuyOrder(
                "GBPUSD", 0.07, 1.0, Optional.of(0.9), Optional.of(2.0), 
                Optional.of("comm"), Optional.of("TE_GBPUSD_7hyINWqAlE")
            ).get();
            if (result.stringCode.equals("TRADE_RETCODE_DONE")) {
                System.out.println("Trade successful");
            } else {
                System.out.println("Trade failed with " + result.stringCode + " error");
            }
            
            // finally, undeploy account
            System.out.println("Undeploying MT4 account so that it does not consume any unwanted resources");
            account.get().undeploy().get();
            
            System.exit(0);
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