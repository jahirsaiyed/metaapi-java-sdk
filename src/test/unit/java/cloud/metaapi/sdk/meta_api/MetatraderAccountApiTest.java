package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.NotFoundException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.AccountsFilter;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountIdDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountUpdateDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewMetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.ConnectionStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;
import cloud.metaapi.sdk.clients.models.*;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Tests {@link MetatraderAccountApi}
 */
public class MetatraderAccountApiTest {
    
    private MetatraderAccountApi api;
    private MetatraderAccountClient client;
    private MetaApiWebsocketClient metaApiWebsocketClient;
    private ConnectionRegistry connectionRegistry;

    @BeforeEach
    void setUp() throws Exception {
        client = Mockito.mock(MetatraderAccountClient.class);
        metaApiWebsocketClient = Mockito.mock(MetaApiWebsocketClient.class);
        connectionRegistry = Mockito.mock(ConnectionRegistry.class, Mockito.RETURNS_DEEP_STUBS);
        api = new MetatraderAccountApi(client, metaApiWebsocketClient, connectionRegistry);
    }
    
    /**
     * Tests {@link MetatraderAccountApi#getAccounts(Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testRetrievesMtAccounts(MetatraderAccountDto accountDto) throws Exception {
        AccountsFilter filter = new AccountsFilter() {{ provisioningProfileId = "profileId"; }};
        Mockito.when(client.getAccounts(filter)).thenReturn(CompletableFuture.completedFuture(Lists.list(accountDto)));
        List<MetatraderAccount> expectedAccounts = Lists.list(
            new MetatraderAccount(accountDto, client, metaApiWebsocketClient, connectionRegistry));
        List<MetatraderAccount> actualAccounts = api.getAccounts(filter).get();
        assertThat(actualAccounts).usingRecursiveComparison().isEqualTo(expectedAccounts);
    }
    
    /**
     * Tests {@link MetatraderAccountApi#getAccount(String)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testRetrievesMtAccountById(MetatraderAccountDto accountDto) throws Exception {
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderAccount expectedAccount = new MetatraderAccount(accountDto, client, metaApiWebsocketClient,
            connectionRegistry);
        MetatraderAccount actualAccount = api.getAccount("id").get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccountApi#getAccountByToken()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testRetrievesMtAccountByToken(MetatraderAccountDto accountDto) throws Exception {
        Mockito.when(client.getAccountByToken()).thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderAccount expectedAccount = new MetatraderAccount(accountDto, client, metaApiWebsocketClient,
            connectionRegistry);
        MetatraderAccount actualAccount = api.getAccountByToken().get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccountApi#createAccount(NewMetatraderAccountDto)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testCreatesMtAccount(MetatraderAccountDto accountDto) throws Exception {
        NewMetatraderAccountDto newAccountDto = new NewMetatraderAccountDto();
        newAccountDto.login = accountDto.login;
        newAccountDto.password = "Test1234";
        newAccountDto.name = accountDto.name;
        newAccountDto.server = accountDto.server;
        newAccountDto.provisioningProfileId = accountDto.provisioningProfileId;
        newAccountDto.magic = accountDto.magic;
        newAccountDto.timeConverter = accountDto.timeConverter;
        newAccountDto.application = accountDto.application;
        newAccountDto.type = accountDto.type;
        MetatraderAccountIdDto accountIdDto = new MetatraderAccountIdDto();
        accountIdDto.id = "id";
        Mockito.when(client.createAccount(newAccountDto)).thenReturn(CompletableFuture.completedFuture(accountIdDto));
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderAccount expectedAccount = new MetatraderAccount(accountDto, client, metaApiWebsocketClient,
            connectionRegistry);
        MetatraderAccount actualAccount = api.createAccount(newAccountDto).get();
        assertThat(actualAccount).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
    
    /**
     * Tests {@link MetatraderAccount#reload()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testReloadsMtAccount(
        MetatraderAccountDto beforeReloadAccount,
        MetatraderAccountDto afterReloadAccount
    ) throws Exception {
        beforeReloadAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        beforeReloadAccount.state = DeploymentState.DEPLOYING;
        afterReloadAccount.connectionStatus = ConnectionStatus.CONNECTED;
        afterReloadAccount.state = DeploymentState.DEPLOYED;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(beforeReloadAccount))
            .thenReturn(CompletableFuture.completedFuture(afterReloadAccount));
        MetatraderAccount account = api.getAccount("id").get();
        account.reload().get();
        assertEquals(ConnectionStatus.CONNECTED, account.getConnectionStatus());
        assertEquals(DeploymentState.DEPLOYED, account.getState());
    }
    
    /**
     * Tests {@link MetatraderAccount#remove()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testRemovesMtAccount(
        MetatraderAccountDto deployedAccount,
        MetatraderAccountDto deletingAccount
    ) throws Exception {
        deployedAccount.connectionStatus = ConnectionStatus.CONNECTED;
        deployedAccount.state = DeploymentState.DEPLOYED;
        deletingAccount.connectionStatus = ConnectionStatus.CONNECTED;
        deletingAccount.state = DeploymentState.DELETING;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(deployedAccount))
            .thenReturn(CompletableFuture.completedFuture(deletingAccount));
        Mockito.when(client.deleteAccount("id")).thenReturn(CompletableFuture.completedFuture(null));
        MetatraderAccount account = api.getAccount("id").get();
        HistoryFileManager historyFileManagerMock = Mockito.mock(HistoryFileManager.class);
        Mockito.when(historyFileManagerMock.deleteStorageFromDisk()).thenReturn(CompletableFuture.completedFuture(null));
        ServiceProvider.setHistoryFileManagerMock(historyFileManagerMock);
        account.remove().get();
        assertEquals(DeploymentState.DELETING, account.getState());
        Mockito.verify(connectionRegistry).remove("id");
        Mockito.verify(client, Mockito.times(2)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#deploy()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testDeploysMtAccount(
        MetatraderAccountDto deployedAccount,
        MetatraderAccountDto deployingAccount
    ) throws Exception {
        deployedAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deployedAccount.state = DeploymentState.UNDEPLOYED;
        deployingAccount.connectionStatus = ConnectionStatus.CONNECTED;
        deployingAccount.state = DeploymentState.DEPLOYING;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(deployedAccount))
            .thenReturn(CompletableFuture.completedFuture(deployingAccount));
        Mockito.when(client.deployAccount("id")).thenReturn(CompletableFuture.completedFuture(null));
        MetatraderAccount account = api.getAccount("id").get();
        account.deploy().get();
        assertEquals(DeploymentState.DEPLOYING, account.getState());
        Mockito.verify(client, Mockito.times(2)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#undeploy()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testUndeploysMtAccount(
        MetatraderAccountDto deployedAccount,
        MetatraderAccountDto undeployingAccount
    ) throws Exception {
        deployedAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deployedAccount.state = DeploymentState.DEPLOYED;
        undeployingAccount.connectionStatus = ConnectionStatus.CONNECTED;
        undeployingAccount.state = DeploymentState.UNDEPLOYING;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(deployedAccount))
            .thenReturn(CompletableFuture.completedFuture(undeployingAccount));
        Mockito.when(client.undeployAccount("id")).thenReturn(CompletableFuture.completedFuture(null));
        MetatraderAccount account = api.getAccount("id").get();
        account.undeploy().get();
        assertEquals(DeploymentState.UNDEPLOYING, account.getState());
        Mockito.verify(connectionRegistry).remove("id");
        Mockito.verify(client, Mockito.times(2)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#redeploy()}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testRedeploysMtAccount(
        MetatraderAccountDto deployedAccount,
        MetatraderAccountDto undeployingAccount
    ) throws Exception {
        deployedAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deployedAccount.state = DeploymentState.DEPLOYED;
        undeployingAccount.connectionStatus = ConnectionStatus.CONNECTED;
        undeployingAccount.state = DeploymentState.UNDEPLOYING;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(deployedAccount))
            .thenReturn(CompletableFuture.completedFuture(undeployingAccount));
        Mockito.when(client.redeployAccount("id")).thenReturn(CompletableFuture.completedFuture(null));
        MetatraderAccount account = api.getAccount("id").get();
        account.redeploy().get();
        assertEquals(DeploymentState.UNDEPLOYING, account.getState());
        Mockito.verify(client, Mockito.times(2)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#waitDeployed(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testWaitsForDeployment(
        MetatraderAccountDto delpoyingAccount,
        MetatraderAccountDto deployedAccount
    ) throws Exception {
        delpoyingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        delpoyingAccount.state = DeploymentState.DEPLOYING;
        deployedAccount.connectionStatus = ConnectionStatus.CONNECTED;
        deployedAccount.state = DeploymentState.DEPLOYED;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(delpoyingAccount))
            .thenReturn(CompletableFuture.completedFuture(delpoyingAccount))
            .thenReturn(CompletableFuture.completedFuture(deployedAccount));
        MetatraderAccount account = api.getAccount("id").get();
        account.waitDeployed(1, 50).get();
        assertEquals(DeploymentState.DEPLOYED, account.getState());
        Mockito.verify(client, Mockito.times(3)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#waitDeployed(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testTimesOutWaitingForDeployment(MetatraderAccountDto deployingAccount) throws Exception {
        deployingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deployingAccount.state = DeploymentState.DEPLOYING;
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(deployingAccount));
        MetatraderAccount account = api.getAccount("id").get();
        assertThrows(TimeoutException.class, () -> {
            try {
                account.waitDeployed(1, 50).get();
                throw new Exception("TimeoutException is expected");
            } catch (ExecutionException err) {
                throw err.getCause();
            }
        });
        assertEquals(DeploymentState.DEPLOYING, account.getState());
    }
    
    /**
     * Tests {@link MetatraderAccount#waitUndeployed(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testWaitsForUndeployment(
        MetatraderAccountDto undeployingAccount,
        MetatraderAccountDto undeployedAccount
    ) throws Exception {
        undeployingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        undeployingAccount.state = DeploymentState.UNDEPLOYING;
        undeployedAccount.connectionStatus = ConnectionStatus.CONNECTED;
        undeployedAccount.state = DeploymentState.UNDEPLOYED;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(undeployingAccount))
            .thenReturn(CompletableFuture.completedFuture(undeployingAccount))
            .thenReturn(CompletableFuture.completedFuture(undeployedAccount));
        MetatraderAccount account = api.getAccount("id").get();
        account.waitUndeployed(1, 50).get();
        assertEquals(DeploymentState.UNDEPLOYED, account.getState());
        Mockito.verify(client, Mockito.times(3)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#waitUndeployed(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testTimesOutWaitingForUndeployment(MetatraderAccountDto undeployingAccount) throws Exception {
        undeployingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        undeployingAccount.state = DeploymentState.UNDEPLOYING;
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(undeployingAccount));
        MetatraderAccount account = api.getAccount("id").get();
        assertThrows(TimeoutException.class, () -> {
            try {
                account.waitUndeployed(1, 50).get();
                throw new Exception("TimeoutException is expected");
            } catch (ExecutionException err) {
                throw err.getCause();
            }
        });
        assertEquals(DeploymentState.UNDEPLOYING, account.getState());
    }
    
    /**
     * Tests {@link MetatraderAccount#waitRemoved(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testWaitsUntilRemoved(MetatraderAccountDto deletingAccount) throws Exception {
        deletingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deletingAccount.state = DeploymentState.DELETING;
        CompletableFuture<MetatraderAccountDto> notFoundCompletableFuture = new CompletableFuture<>();
        notFoundCompletableFuture.completeExceptionally(new NotFoundException("Error message"));
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(deletingAccount))
            .thenReturn(CompletableFuture.completedFuture(deletingAccount))
            .thenReturn(notFoundCompletableFuture);
        MetatraderAccount account = api.getAccount("id").get();
        account.waitRemoved(1, 50).get();
        Mockito.verify(client, Mockito.times(3)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#waitRemoved(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testTimesOutWaitingUntilRemoved(MetatraderAccountDto deletingAccount) throws Exception {
        deletingAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        deletingAccount.state = DeploymentState.DELETING;
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(deletingAccount));
        MetatraderAccount account = api.getAccount("id").get();
        assertThrows(TimeoutException.class, () -> {
            try {
                account.waitRemoved(1, 50).get();
                throw new Exception("TimeoutException is expected");
            } catch (ExecutionException err) {
                throw err.getCause();
            }
        });
    }
    
    /**
     * Tests {@link MetatraderAccount#waitConnected(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testWaitsUntilBrokerConnection(
        MetatraderAccountDto disconnectedAccount,
        MetatraderAccountDto connectedAccount
    ) throws Exception {
        disconnectedAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        connectedAccount.connectionStatus = ConnectionStatus.CONNECTED;
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(disconnectedAccount))
            .thenReturn(CompletableFuture.completedFuture(disconnectedAccount))
            .thenReturn(CompletableFuture.completedFuture(connectedAccount));
        MetatraderAccount account = api.getAccount("id").get();
        account.waitConnected(1, 50).get();
        assertEquals(ConnectionStatus.CONNECTED, account.getConnectionStatus());
        Mockito.verify(client, Mockito.times(3)).getAccount("id");
    }
    
    /**
     * Tests {@link MetatraderAccount#waitConnected(int, int)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testTimesOutWaitingForBrokerConnection(MetatraderAccountDto disconnectedAccount) throws Exception {
        disconnectedAccount.connectionStatus = ConnectionStatus.DISCONNECTED;
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(disconnectedAccount));
        MetatraderAccount account = api.getAccount("id").get();
        assertThrows(TimeoutException.class, () -> {
            try {
                account.waitConnected(1, 50).get();
                throw new Exception("TimeoutException is expected");
            } catch (ExecutionException err) {
                throw err.getCause();
            }
        });
        assertEquals(ConnectionStatus.DISCONNECTED, account.getConnectionStatus());
    }
    
    /**
     * Tests {@link MetatraderAccount#connect}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDto")
    void testConnectsToAnMtTerminal(MetatraderAccountDto accountDto) {
        // Common preparation
        Mockito.when(metaApiWebsocketClient.subscribe("id")).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(client.getAccount("id")).thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderAccount account = api.getAccount("id").join();
        // Stub storage
        HistoryStorage storage = Mockito.mock(HistoryStorage.class);
        Mockito.when(storage.getLastHistoryOrderTime())
            .thenReturn(CompletableFuture.completedFuture(new IsoTime("2020-01-01T00:00:00.000Z")));
        Mockito.when(storage.getLastDealTime())
            .thenReturn(CompletableFuture.completedFuture(new IsoTime("2020-01-02T00:00:00.000Z")));
        Mockito.when(storage.loadData()).thenReturn(CompletableFuture.completedFuture(null));
        
        account.connect(storage).join();
        Mockito.verify(connectionRegistry).connect(account, storage);
    }
    
    /**
     * Tests {@link MetatraderAccount#update(MetatraderAccountUpdateDto)}
     */
    @ParameterizedTest
    @MethodSource("provideAccountDtoPair")
    void testUpdatesMtAccount(
        MetatraderAccountDto initialAccount,
        MetatraderAccountDto editedAccount
    ) throws Exception {
        MetatraderAccountUpdateDto updateDto = new MetatraderAccountUpdateDto();
        updateDto.name = "mt5a__";
        updateDto.password = "moreSecurePass";
        initialAccount.connectionStatus = ConnectionStatus.CONNECTED;
        editedAccount.connectionStatus = ConnectionStatus.CONNECTED;
        editedAccount.name = updateDto.name;
        editedAccount.server = "OtherMarkets-Demo";
        Mockito.when(client.getAccount("id"))
            .thenReturn(CompletableFuture.completedFuture(initialAccount))
            .thenReturn(CompletableFuture.completedFuture(editedAccount));
        Mockito.when(client.updateAccount("id", updateDto))
            .thenReturn(CompletableFuture.completedFuture(null));
        MetatraderAccount account = api.getAccount("id").get();
        account.update(updateDto).get();
        assertEquals(editedAccount.name, account.getName());
        assertEquals(editedAccount.server, account.getServer());
        Mockito.verify(client, Mockito.times(2)).getAccount("id");
    }
    
    private static Stream<Arguments> provideAccountDto() {
        MetatraderAccountDto account = new MetatraderAccountDto();
        account._id = "id";
        account.login = "50194988";
        account.name = "name";
        account.server = "ICMarketsSC-Demo";
        account.provisioningProfileId = "f9ce1f12-e720-4b9a-9477-c2d4cb25f076";
        account.magic = 123456;
        account.timeConverter = "icmarkets";
        account.application = "MetaApi";
        account.connectionStatus = ConnectionStatus.DISCONNECTED;
        account.state = DeploymentState.DEPLOYED;
        account.type = "cloud";
        account.accessToken = "2RUnoH1ldGbnEneCoqRTgI4QO1XOmVzbH5EVoQsA";
        return Stream.of(Arguments.of(account));
    }
    
    private static Stream<Arguments> provideAccountDtoPair() {
        return Stream.of(Arguments.of(
            provideAccountDto().iterator().next().get()[0],
            provideAccountDto().iterator().next().get()[0]
        ));
    }
}