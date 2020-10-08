package cloud.metaapi.sdk.meta_api;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileUpdateDto;

/**
 * Implements a provisioning profile entity
 */
public class ProvisioningProfile {

    private ProvisioningProfileDto data;
    private ProvisioningProfileClient provisioningProfileClient;
    
    /**
     * Constructs a provisioning profile entity
     * @param data provisioning profile data
     * @param provisioningProfileClient provisioning profile REST API client
     */
    public ProvisioningProfile(ProvisioningProfileDto data, ProvisioningProfileClient provisioningProfileClient) {
        this.data = data;
        this.provisioningProfileClient = provisioningProfileClient;
    }
    
    /**
     * Returns profile id
     * @return profile id
     */
    public String getId() {
        return data._id;
    }
    
    /**
     * Returns profile name
     * @return profile name
     */
    public String getName() {
        return data.name;
    }
    
    /**
     * Returns profile version. Possible values are 4 and 5
     * @return profile version
     */
    public int getVersion() {
        return data.version;
    }
    
    /**
     * Returns profile status. Possible values are new and active
     * @return profile status
     */
    public String getStatus() {
        return data.status;
    }

    /**
     * Returns broker timezone name from Time Zone Database
     * @return broker timezone name
     */

    public String getBrokerTimezone() {
        return data.brokerTimezone;
    }
    
    /**
     * Returns broker DST timezone name from Time Zone Database
     * @return broker DST timezone name
     */
    public String getBrokerDSTTimezone() {
        return data.brokerDSTTimezone;
    }
    
    /**
     * Reloads provisioning profile from API
     * @return completable future resolving when provisioning profile is updated
     */
    public CompletableFuture<Void> reload() {
        return provisioningProfileClient.getProvisioningProfile(getId()).thenAccept(profile -> data = profile);
    }
    
    /**
     * Removes provisioning profile. The current object instance should be discarded after returned promise resolves.
     * @return completable future resolving when provisioning profile is removed
     */
    public CompletableFuture<Void> remove() {
        return provisioningProfileClient.deleteProvisioningProfile(getId());
    }
    
    /**
     * Uploads a file to provisioning profile.
     * @param fileName name of the file to upload. Allowed values are servers.dat for MT5 profile, broker.srv for
     * MT4 profile
     * @param filePath path to a file to upload
     * @return completable future which resolves when the file is uploaded
     */
    public CompletableFuture<Void> uploadFile(String fileName, String filePath) {
        return provisioningProfileClient.uploadProvisioningProfileFile(getId(), fileName, filePath);
    }
    
    /**
     * Uploads a file to provisioning profile.
     * @param fileName name of the file to upload. Allowed values are servers.dat for MT5 profile, broker.srv for
     * MT4 profile
     * @param fileContents input stream containing file contents
     * @return completable future which resolves when the file is uploaded
     */
    public CompletableFuture<Void> uploadFile(String fileName, InputStream fileContents) {
        return provisioningProfileClient.uploadProvisioningProfileFile(getId(), fileName, fileContents);
    }
    
    /**
     * Updates provisioning profile
     * @param profile provisioning profile update
     * @return completable future resolving when provisioning profile is updated
     */
    public CompletableFuture<Void> update(ProvisioningProfileUpdateDto profile) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                provisioningProfileClient.updateProvisioningProfile(getId(), profile).get();
                reload().get();
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }
}