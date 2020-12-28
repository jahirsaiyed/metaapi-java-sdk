package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.NotFoundException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountUpdateDto;
import cloud.metaapi.sdk.clients.models.IsoTime;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.ConnectionStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.Extension;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Implements a MetaTrader account entity
 */
public class MetatraderAccount {
    
    private MetatraderAccountDto data;
    private MetatraderAccountClient metatraderAccountClient;
    private ConnectionRegistry connectionRegistry;
    
    /**
     * Constructs a MetaTrader account entity
     * @param data MetaTrader account data
     * @param metatraderAccountClient MetaTrader account REST API client
     * @param metaApiWebsocketClient MetaApi websocket client
     * @param connectionRegistry metatrader account connection registry
     */
    public MetatraderAccount(
        MetatraderAccountDto data,
        MetatraderAccountClient metatraderAccountClient,
        MetaApiWebsocketClient metaApiWebsocketClient,
        ConnectionRegistry connectionRegistry
    ) {
        this.data = data;
        this.metatraderAccountClient = metatraderAccountClient;
        this.connectionRegistry = connectionRegistry;
    }
    
    /**
     * Returns account id
     * @return account id
     */
    public String getId() {
        return data._id;
    }
    
    /**
     * Returns account name
     * @return account name
     */
    public String getName() {
        return data.name;
    }
    
    /**
     * Returns account type. Possible values are cloud and self-hosted.
     * @return account type
     */
    public String getType() {
        return data.type;
    }
    
    /**
     * Returns account login
     * @return account login
     */
    public String getLogin() {
        return data.login;
    }
    
    /**
     * Returns MetaTrader server which hosts the account
     * @return MetaTrader server which hosts the account
     */
    public String getServer() {
        return data.server;
    }
    
    /**
     * Returns id of the account's provisioning profile
     * @return id of the account's provisioning profile
     */
    public String getProvisioningProfileId() {
        return data.provisioningProfileId;
    }
    
    /**
     * Returns application name to connect the account to. Currently allowed values are MetaApi and AgiliumTrade
     * @return application name to connect the account to
     */
    public String getApplication() {
        return data.application;
    }
    
    /**
     * Returns MetaTrader magic to place trades using
     * @return MetaTrader magic to place trades using
     */
    public int getMagic() {
        return data.magic;
    }
    
    /**
     * Returns account deployment state
     * @return account deployment state
     */
    public DeploymentState getState() {
        return data.state;
    }
    
    /**
     * Returns terminal and broker connection status
     * @return terminal and broker connection status
     */
    public ConnectionStatus getConnectionStatus() {
        return data.connectionStatus;
    }
    
    /**
     * Returns authorization access token to be used for accessing single account data.
     * Intended to be used in browser API.
     * @return authorization token
     */
    public String getAccessToken() {
        return data.accessToken;
    }
    
    /**
     * Returns flag indicating if trades should be placed as manual trades on this account
     * @return flag indicating if trades should be placed as manual trades on this account
     */
    public boolean isManualTrades() {
        return data.manualTrades;
    }
    
    /**
     * Returns API extensions
     * @return API extensions
     */
    public List<Extension> getExtensions() {
        return data.extensions;
    }
    
    /**
     * Returns extra information which can be stored together with your account
     * @return extra information which can be stored together with your account
     */
    public Map<String, Object> getMetadata() {
        return data.metadata;
    }
    
    /**
     * Reloads MetaTrader account from API
     * @return completable future resolving when MetaTrader account is updated
     */
    public CompletableFuture<Void> reload() {
        return metatraderAccountClient.getAccount(getId()).thenAccept(account -> data = account);
    }
    
    /**
     * Removes MetaTrader account. Cloud account transitions to DELETING state. 
     * It takes some time for an account to be eventually deleted. Self-hosted 
     * account is deleted immediately.
     * @return completable future resolving when account is scheduled for deletion
     */
    public CompletableFuture<Void> remove() {
        return CompletableFuture.supplyAsync(() -> {
            connectionRegistry.remove(getId());
            try {
                metatraderAccountClient.deleteAccount(getId()).get();
                HistoryFileManager fileManager = ServiceProvider.createHistoryFileManager(getId(), "MetaApi", null);
                fileManager.deleteStorageFromDisk().get();
                if (!getType().equals("self-hosted")) {
                    try {
                        reload().get();
                    } catch (ExecutionException e) {
                        if (!(e.getCause() instanceof NotFoundException)) throw e.getCause();
                    }
                }
                return null;
            } catch (Throwable e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Schedules account for deployment. It takes some time for API server to be started and account 
     * to reach the DEPLOYED state
     * @return completable future resolving when account is scheduled for deployment
     */
    public CompletableFuture<Void> deploy() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                metatraderAccountClient.deployAccount(getId()).get();
                reload().get();
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Schedules account for undeployment. It takes some time for API server to be stopped and account 
     * to reach the UNDEPLOYED state
     * @return completable future resolving when account is scheduled for undeployment
     */
    public CompletableFuture<Void> undeploy() {
        return CompletableFuture.runAsync(() -> {
            connectionRegistry.remove(getId());
            metatraderAccountClient.undeployAccount(getId()).join();
            reload().join();
        });
    }
    
    /**
     * Schedules account for redeployment. It takes some time for API server to be restarted and account
     * to reach the DEPLOYED state
     * @return completable future resolving when account is scheduled for redeployment
     */
    public CompletableFuture<Void> redeploy() {
        return CompletableFuture.runAsync(() -> {
            metatraderAccountClient.redeployAccount(getId()).join();
            reload().join();
        });
    }
    
    /**
     * Waits until API server has finished deployment and account reached the DEPLOYED state.
     * Completes exceptionally with {@link TimeoutException} if account have not reached the DEPLOYED state
     * withing timeout allowed.
     * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
     * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
     * @return completable future which resolves when account is deployed
     */
    public CompletableFuture<Void> waitDeployed(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            long startTime = Instant.now().getEpochSecond();
            long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
            try {
                reload().get();
                while (getState() != DeploymentState.DEPLOYED && timeoutTime > Instant.now().getEpochSecond()) {
                    Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
                    reload().get();
                };
                if (getState() != DeploymentState.DEPLOYED) 
                    throw new TimeoutException("Timed out waiting for account " + getId() + " to be deployed");
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Waits until API server has finished undeployment and account reached the UNDEPLOYED state.
     * Completes exceptionally with {@link TimeoutException} if account have not reached the UNDEPLOYED state
     * withing timeout allowed.
     * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
     * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
     * @return completable future which resolves when account is undeployed
     */
    public CompletableFuture<Void> waitUndeployed(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            long startTime = Instant.now().getEpochSecond();
            long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
            try {
                reload().get();
                while (getState() != DeploymentState.UNDEPLOYED && timeoutTime > Instant.now().getEpochSecond()) {
                    Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
                    reload().get();
                };
                if (getState() != DeploymentState.UNDEPLOYED) 
                    throw new TimeoutException("Timed out waiting for account " + getId() + " to be undeployed");
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Waits until account has been deleted. Completes exceptionally with {@link TimeoutException} 
     * if account was not deleted withing timeout allowed.
     * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
     * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
     * @return completable future which resolves when account is deleted
     */
    public CompletableFuture<Void> waitRemoved(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            long startTime = Instant.now().getEpochSecond();
            long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
            try {
                reload().get();
                while (timeoutTime > Instant.now().getEpochSecond()) {
                    Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
                    reload().get();
                };
                throw new TimeoutException("Timed out waiting for account " + getId() + " to be deleted");
            } catch (Exception e) {
                if (e.getCause() instanceof NotFoundException) result.complete(null);
                else result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Waits until API server has connected to the terminal and terminal has connected to the broker.
     * Completes exceptionally with {@link TimeoutException} if account have not connected to the broker
     * withing timeout allowed. Wait timeout in seconds is 5m and interval between account reloads while 
     * waiting for a change is 1s.
     * @return completable future which resolves when API server is connected to the broker
     */
    public CompletableFuture<Void> waitConnected() {
        return waitConnected(null, null);
    }
    
    /**
     * Waits until API server has connected to the terminal and terminal has connected to the broker.
     * Completes exceptionally with {@link TimeoutException} if account have not connected to the broker
     * withing timeout allowed.
     * @param timeoutInSeconds optional wait timeout in seconds, default is 5m
     * @param intervalInMilliseconds optional interval between account reloads while waiting for a change, default is 1s
     * @return completable future which resolves when API server is connected to the broker
     */
    public CompletableFuture<Void> waitConnected(Integer timeoutInSeconds, Integer intervalInMilliseconds) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            long startTime = Instant.now().getEpochSecond();
            long timeoutTime = startTime + (timeoutInSeconds != null ? timeoutInSeconds : 300);
            try {
                reload().get();
                while (    getConnectionStatus() != ConnectionStatus.CONNECTED 
                        && timeoutTime > Instant.now().getEpochSecond()
                ) {
                    Thread.sleep(intervalInMilliseconds != null ? intervalInMilliseconds : 1000);
                    reload().get();
                };
                if (getConnectionStatus() != ConnectionStatus.CONNECTED) 
                    throw new TimeoutException("Timed out waiting for account " + getId() + " to connect to the broker");
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Connects to MetaApi with default history storage. There is only one connection per account.
     * Subsequent calls to this method will return the same connection.
     * @return MetaApi connection
     */
    public CompletableFuture<MetaApiConnection> connect() {
        return connect(null, null);
    }
    
    /**
     * Connects to MetaApi. There is only one connection per account. 
     * Subsequent calls to this method will return the same connection.
     * @param historyStorage optional history storage, or {@code null}
     * @return MetaApi connection
     */
    public CompletableFuture<MetaApiConnection> connect(HistoryStorage historyStorage) {
        return connect(historyStorage, null);
    }
    
    /**
     * Connects to MetaApi. There is only one connection per account. 
     * Subsequent calls to this method will return the same connection.
     * @param historyStorage optional history storage, or {@code null}
     * @param historyStartTime history start time, or {@code null}. Used for tests
     * @return MetaApi connection
     */
    public CompletableFuture<MetaApiConnection> connect(HistoryStorage historyStorage, IsoTime historyStartTime) {
        return connectionRegistry.connect(this, historyStorage, historyStartTime);
    }
    
    /**
     * Updates MetaTrader account data
     * @param account MetaTrader account update
     * @return completable future resolving when account is updated
     */
    public CompletableFuture<Void> update(MetatraderAccountUpdateDto account) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                metatraderAccountClient.updateAccount(getId(), account).get();
                reload().get();
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
}