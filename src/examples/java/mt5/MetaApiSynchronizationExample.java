package mt5;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;

import cloud.metaapi.sdk.clients.meta_api.TradeException;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.meta_api.models.NewMetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.PendingTradeOptions;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.meta_api.ProvisioningProfile;
import cloud.metaapi.sdk.meta_api.TerminalState;
import cloud.metaapi.sdk.util.JsonMapper;

/**
 * Note: for information on how to use this example code please read https://metaapi.cloud/docs/client/usingCodeExamples
 */
public class MetaApiSynchronizationExample {

    private static String token = getEnvOrDefault("NAME", "<put in your token here>");
    private static String login = getEnvOrDefault("LOGIN", "<put in your MT login here>");
    private static String password = getEnvOrDefault("PASSWORD", "<put in your MT password here>");
    private static String serverName = getEnvOrDefault("SERVER", "<put in your MT server name here>");
    private static String serverDatFile = getEnvOrDefault("PATH_TO_SERVERS_DAT", "/path/to/your/servers.dat");

    public static void main(String[] args) {
        try {
            MetaApi api = new MetaApi(token);
            List<ProvisioningProfile> profiles = api.getProvisioningProfileApi().getProvisioningProfiles().get();
            
            // create test MetaTrader account profile
            Optional<ProvisioningProfile> profile = profiles.stream()
                .filter(p -> p.getName().equals(serverName))
                .findFirst();
            if (!profile.isPresent()) {
                System.out.println("Creating account profile");
                NewProvisioningProfileDto newDto = new NewProvisioningProfileDto() {{ name = serverName; version = 5; }};
                profile = Optional.of(api.getProvisioningProfileApi().createProvisioningProfile(newDto).get());
                profile.get().uploadFile("servers.dat", serverDatFile).get();
            }
            if (profile.isPresent() && profile.get().getStatus().equals("new")) {
                System.out.println("Uploading servers.dat");
                profile.get().uploadFile("servers.dat", serverDatFile).get();
            } else {
                System.out.println("Account profile already created");
            }
            
            // Add test MetaTrader account
            List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts().get();
            Optional<MetatraderAccount> account = accounts.stream()
                .filter(a -> a.getLogin().equals(login) && a.getType().startsWith("cloud"))
                .findFirst();
            if (!account.isPresent()) {
                System.out.println("Adding MT5 account to MetaApi");
                String mtLogin = login;
                String mtPassword = password;
                ProvisioningProfile provisioningProfile = profile.get();
                account = Optional.of(api.getMetatraderAccountApi().createAccount(new NewMetatraderAccountDto() {{
                    name = "Test account";
                    type = "cloud";
                    login = mtLogin;
                    password = mtPassword;
                    server = serverName;
                    provisioningProfileId = provisioningProfile.getId();
                    timeConverter = "icmarkets";
                    application = "MetaApi";
                    magic = 1000;
                }}).get());
            } else {
                System.out.println("MT5 account already added to MetaApi");
            }
            
            // wait until account is deployed and connected to broker
            System.out.println("Deploying account");
            account.get().deploy().get();
            System.out.println("Waiting for API server to connect to broker (may take couple of minutes)");
            account.get().waitConnected().get();
            
            // connect to MetaApi API
            MetaApiConnection connection = account.get().connect().get();
            
            System.out.println("Waiting for SDK to synchronize to terminal state "
                + "(may take some time depending on your history size)");
            connection.waitSynchronized(null, 600, null).get();

            // access local copy of terminal state
            System.out.println("Testing terminal state access");
            TerminalState terminalState = connection.getTerminalState();
            System.out.println("connected: " + terminalState.isConnected());
            System.out.println("connected to broker: " + terminalState.isConnectedToBroker());
            System.out.println("account information: " + asJson(terminalState.getAccountInformation().orElse(null)));
            System.out.println("positions: " + asJson(terminalState.getPositions()));
            System.out.println("orders: " + asJson(terminalState.getOrders()));
            System.out.println("specifications: " + asJson(terminalState.getSpecifications()));
            System.out.println("EURUSD specification: " + asJson(terminalState.getSpecification("EURUSD").orElse(null)));
            System.out.println("EURUSD price: " + asJson(terminalState.getPrice("EURUSD").orElse(null)));
            
            // trade
            System.out.println("Submitting pending order");
            try {
                MetatraderTradeResponse result = connection
                    .createLimitBuyOrder("GBPUSD", 0.07, 1.0, 0.9, 2.0, new PendingTradeOptions() {{
                        comment = "comm"; clientId = "TE_GBPUSD_7hyINWqAlE"; 
                    }}).get();
                System.out.println("Trade successful, result code is " + result.stringCode);
            } catch (ExecutionException err) {
                System.out.println("Trade failed with result code " + ((TradeException) err.getCause()).stringCode);
            }
            
            // finally, undeploy account
            System.out.println("Undeploying MT5 account so that it does not consume any unwanted resources");
            account.get().undeploy().get();
        } catch (Exception err) {
            System.err.println(err);
        }
        System.exit(0);
    }
    
    private static String getEnvOrDefault(String name, String defaultValue) {
        String result = System.getenv(name);
        return (result != null ? result : defaultValue);
    }
    
    private static String asJson(Object object) throws JsonProcessingException {
        return JsonMapper.getInstance().writeValueAsString(object);
    }
}