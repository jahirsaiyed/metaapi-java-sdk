package cloud.metaapi.sdk;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cloud.metaapi.sdk.clients.error_handler.ValidationException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.models.NewMetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.SynchronizationOptions;
import cloud.metaapi.sdk.meta_api.MetaApi;
import cloud.metaapi.sdk.meta_api.MetaApiConnection;
import cloud.metaapi.sdk.meta_api.MetatraderAccount;
import cloud.metaapi.sdk.meta_api.MetatraderAccountApi;
import cloud.metaapi.sdk.meta_api.ProvisioningProfile;
import cloud.metaapi.sdk.meta_api.ProvisioningProfileApi;
import cloud.metaapi.sdk.util.JsonMapper;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * MT5 double synchronization test
 */
class DoubleSynchronizationTest {

  private Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
  private String token = dotenv.get("TOKEN");
  private String login = dotenv.get("LOGIN");
  private String password = dotenv.get("PASSWORD");
  private String serverName = dotenv.get("SERVER");
  private String serverDatFile = dotenv.get("PATH_TO_SERVERS_DAT");
  
  @BeforeAll
  static void setUpBeforeClass() throws IOException {
    Files.createDirectories(FileSystems.getDefault().getPath(".", ".metaapi"));
  }

  @AfterEach
  void tearDown() throws IOException {
    FileUtils.cleanDirectory(new File("./.metaapi"));
  }

  @Test
  void testDoesNotCorruptFilesAfterSimultaneousSynchronization() throws ValidationException, IOException {
    MetaApi api = new MetaApi(token, new MetaApi.Options() {{
      domain = "project-stock.v3.agiliumlabs.cloud";
    }});
    final ProvisioningProfileApi profileApi = api.getProvisioningProfileApi();
    final MetatraderAccountApi accountApi = api.getMetatraderAccountApi();
    if (token != null) {
      List<ProvisioningProfile> profiles = profileApi.getProvisioningProfiles().join();
      assertTimeoutPreemptively(Duration.ofMinutes(10), () -> {
        ProvisioningProfile profile = profiles.stream()
          .filter(p -> p.getName().equals(serverName))
          .findFirst().orElseGet(() -> {
            return profileApi.createProvisioningProfile(new NewProvisioningProfileDto() {{
              name = serverName;
              version = 5;
            }}).thenApply(p -> {
              p.uploadFile("servers.dat", serverDatFile);
              return p;
            }).join();
          });
        if (profile.getStatus().equals("new")) {
          profile.uploadFile("servers.dat", serverDatFile).join();
        }
        List<MetatraderAccount> accounts = accountApi.getAccounts().join();
        MetatraderAccount account = accounts.stream()
          .filter(a -> a.getLogin().equals(login) && a.getType().startsWith("cloud"))
          .findFirst().orElseGet(() -> {
            String envLogin = login;
            String envPassword = password;
            return accountApi.createAccount(new NewMetatraderAccountDto() {{
              name = "Test account";
              type = "cloud";
              login = envLogin;
              password = envPassword;
              server = serverName;
              provisioningProfileId = profile.getId();
              application = "MetaApi";
              magic = 1000;
            }}).join();
          });
        MetatraderAccount accountCopy = accountApi.getAccount(account.getId()).join();
        CompletableFuture.allOf(
          account.deploy(),
          accountCopy.deploy()
        ).join();
        CompletableFuture.allOf(
          account.waitConnected(),
          accountCopy.waitConnected()
        ).join();
        MetaApiConnection connection = account.connect().join();
        MetaApiConnection connectionCopy = accountCopy.connect().join();
        CompletableFuture.allOf(
          connection.waitSynchronized(new SynchronizationOptions() {{
            this.timeoutInSeconds = 600;
          }}),
          connectionCopy.waitSynchronized(new SynchronizationOptions() {{
            this.timeoutInSeconds = 600;
          }})
        ).join();
        account.undeploy().join();
        accountCopy.undeploy().join();
        MetaApiWebsocketClient websocketClient = ((MetaApiWebsocketClient) FieldUtils
          .readField(api, "metaApiWebsocketClient", true));
        websocketClient.removeAllListeners();
        if (Files.exists(FileSystems.getDefault().getPath(".", ".metaapi", account.getId() + "-MetaApi-deals.bin"))) {
          JsonMapper.getInstance().readTree(new File("./.metaapi/" + account.getId() + "-MetaApi-deals.bin"));
        }
        if (Files.exists(FileSystems.getDefault().getPath(".", ".metaapi", account.getId() + "-MetaApi-historyOrders.bin"))) {
          JsonMapper.getInstance().readTree(new File("./.metaapi/" + account.getId() + "-MetaApi-historyOrders.bin"));
        }
      });
    }
  }
}