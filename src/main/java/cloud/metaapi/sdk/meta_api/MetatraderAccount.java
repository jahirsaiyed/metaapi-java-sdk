package cloud.metaapi.sdk.meta_api;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import cloud.metaapi.sdk.clients.TimeoutException;
import cloud.metaapi.sdk.clients.error_handler.NotFoundException;
import cloud.metaapi.sdk.clients.meta_api.MetaApiWebsocketClient;
import cloud.metaapi.sdk.clients.meta_api.MetatraderAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountUpdateDto;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.ConnectionStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;
import cloud.metaapi.sdk.util.ServiceProvider;

/**
 * Implements a MetaTrader account entity
 */
public class MetatraderAccount {
    
    private MetatraderAccountDto data;
    private MetatraderAccountClient metatraderAccountClient;
    private MetaApiWebsocketClient metaApiWebsocketClient;
    
    /**
     * Constructs a MetaTrader account entity
     * @param data MetaTrader account data
     * @param metatraderAccountClient MetaTrader account REST API client
     * @param metaApiWebsocketClient MetaApi websocket client
     */
    public MetatraderAccount(
        MetatraderAccountDto data,
        MetatraderAccountClient metatraderAccountClient,
        MetaApiWebsocketClient metaApiWebsocketClient
    ) {
        this.data = data;
        this.metatraderAccountClient = metatraderAccountClient;
        this.metaApiWebsocketClient = metaApiWebsocketClient;
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
     * Returns synchronization mode, can be automatic or user. See
     * https://metaapi.cloud/docs/client/websocket/synchronizationMode/ for more details.
     * @return synchronization mode
     */
    public String getSynchronizationMode() {
        return data.synchronizationMode;
    }
    
    /**
     * Returns id of the account's provisioning profile
     * @return id of the account's provisioning profile
     */
    public String getProvisioningProfileId() {
        return data.provisioningProfileId;
    }
    
    /**
     * Returns algorithm used to parse your broker timezone. Supported values are icmarkets for
     * America/New_York DST switch and roboforex for EET DST switch (the values will be changed soon)
     * @return algorithm used to parse your broker timezone
     */
    public String getTimeConverter() {
        return data.timeConverter;
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
     * Returns terminal & broker connection status
     * @return terminal & broker connection status
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
            try {
                metatraderAccountClient.deleteAccount(getId()).get();
                HistoryFileManager fileManager = ServiceProvider.createHistoryFileManager(getId(), null);
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
     * @returns completable future resolving when account is scheduled for deployment
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
     * @returns completable future resolving when account is scheduled for undeployment
     */
    public CompletableFuture<Void> undeploy() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                metatraderAccountClient.undeployAccount(getId()).get();
                reload().get();
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    
    /**
     * Schedules account for redeployment. It takes some time for API server to be restarted and account
     * to reach the DEPLOYED state
     * @returns completable future resolving when account is scheduled for redeployment
     */
    public CompletableFuture<Void> redeploy() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                metatraderAccountClient.redeployAccount(getId()).get();
                reload().get();
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
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
     * Connects to MetaApi with default history storage
     * @returns MetaApi connection
     */
    public CompletableFuture<MetaApiConnection> connect() {
        return connect(null);
    }
    
    /**
     * Connects to MetaApi
     * @param historyStorage optional history storage
     * @returns MetaApi connection
     */
    public CompletableFuture<MetaApiConnection> connect(HistoryStorage historyStorage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MetaApiConnection connection = ServiceProvider
                    .createMetaApiConnection(metaApiWebsocketClient, this, historyStorage);
                if (getSynchronizationMode().equals("user")) connection.initialize().get();
                connection.subscribe().get();
                return connection;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
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