package cloud.metaapi.sdk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderTradeResponse;
import cloud.metaapi.sdk.clients.meta_api.models.NewMetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.meta_api.ProvisioningProfile;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * MT4 sync positions test
 */
class SyncPositionsTest {

  private Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
  private String token = dotenv.get("TOKEN");
  private String login = dotenv.get("LOGIN_MT4");
  private String password = dotenv.get("PASSWORD_MT4");
  private String serverName = dotenv.get("SERVER_MT4");
  private String brokerSrvFile = dotenv.get("PATH_TO_BROKER_SRV");
  
  private static class CheckPositionsResult {
    public int local;
    public int real;
  }
  
  CheckPositionsResult checkPositions(MetaApiConnection connection) {
    return new CheckPositionsResult() {{
      local = connection.getTerminalState().getPositions().size();
      real = connection.getPositions().join().size();
    }};
  }
  
  @Test
  void testShowsCorrectPositionsAmountAfterOpeningAndClosing() 
    throws ValidationException, IOException, InterruptedException {
    if (serverName == null) {
      serverName = "Tradeview-Demo";
    }
    if (brokerSrvFile == null) {
      brokerSrvFile = "./src/test/resources/tradeview-demo.broker.srv";
    }
    if (token != null && login != null) {
      MetaApi api = new MetaApi(token, new MetaApi.Options() {{
        application = "MetaApi";
        domain = "project-stock.v2.agiliumlabs.cloud";
      }});
      List<ProvisioningProfile> profiles = api.getProvisioningProfileApi()
        .getProvisioningProfiles().join();
      Optional<ProvisioningProfile> profile = profiles.stream()
        .filter(p -> p.getName().equals(serverName))
        .findFirst();
      if (!profile.isPresent()) {
        profile = Optional.of(api.getProvisioningProfileApi()
          .createProvisioningProfile(new NewProvisioningProfileDto() {{
          name = serverName;
          version = 4;
          brokerTimezone = "EET";
          brokerDSTSwitchTimezone = "EET";
        }}).join());
        profile.get().uploadFile("broker.srv", brokerSrvFile).join();
      } else if (profile.get().getStatus().equals("new")) {
        profile.get().uploadFile("broker.srv", brokerSrvFile).join();
      }
      List<MetatraderAccount> accounts = api.getMetatraderAccountApi().getAccounts().join();
      Optional<MetatraderAccount> account = accounts.stream()
        .filter(a -> a.getLogin().equals(login) && a.getType().equals("cloud-g2"))
        .findFirst();
      if (!account.isPresent()) {
        String accountLogin = login;
        String accountPassword = password;
        ProvisioningProfile accountProfile = profile.get();
        account = Optional.of(api.getMetatraderAccountApi()
          .createAccount(new NewMetatraderAccountDto() {{
          name = "Test account-mt4";
          type = "cloud-g2";
          login = accountLogin;
          password = accountPassword;
          server = serverName;
          provisioningProfileId = accountProfile.getId();
          application = "MetaApi";
          magic = 1000;
        }}).join());
      }
      account.get().deploy().join();
      account.get().waitConnected().join();
      MetaApiConnection connection = account.get().connect().join();
      connection.waitSynchronized(new SynchronizationOptions() {{
        timeoutInSeconds = 600;
      }}).join();
      int startPositions = connection.getTerminalState().getPositions().size();
      List<String> positionIds = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        MetatraderTradeResponse result = connection.createMarketBuyOrder("GBPUSD", 0.01, 
          0.9, 2.0, null).join();
        positionIds.add(result.positionId);
        Thread.sleep(200);
      }
      Thread.sleep(200);
      CheckPositionsResult positions = checkPositions(connection);
      assertEquals(positions.local, startPositions + 10);
      assertEquals(positions.real, startPositions + 10);
      Thread.sleep(5000);
      CompletableFuture.allOf(positionIds.stream().map(id -> {
        return connection.closePosition(id, null);
      }).collect(Collectors.toList()).toArray(new CompletableFuture<?>[0])).join();
      Thread.sleep(1000);
      CompletableFuture.allOf(positionIds.stream().map(id -> {
        return CompletableFuture.runAsync(() -> {
          try {
            connection.getPosition(id).join();
            Assertions.fail();
          } catch (CompletionException err) {}
        });
      }).collect(Collectors.toList()).toArray(new CompletableFuture<?>[0])).join();
      positions = checkPositions(connection);
      assertEquals(positions.local, startPositions);
      assertEquals(positions.real, startPositions);
      account.get().undeploy().join();
    }
  }
}