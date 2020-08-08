package cloud.metaapi.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import cloud.metaapi.sdk.clients.ProvisioningProfileClient;
import cloud.metaapi.sdk.clients.models.NewProvisioningProfileDto;
import cloud.metaapi.sdk.clients.models.ProvisioningProfileDto;

/**
 * Exposes provisioning profile API logic to the consumers
 */
public class ProvisioningProfileApi {

    private ProvisioningProfileClient provisioningProfileClient;
    
    /**
     * Constructs a provisioning profile API instance
     * @param provisioningProfileClient provisioning profile REST API client
     */
    public ProvisioningProfileApi(ProvisioningProfileClient provisioningProfileClient) {
        this.provisioningProfileClient = provisioningProfileClient;
    }
    
    /**
     * Retrieves provisioning profiles
     * @param version optional version filter (allowed values are 4 and 5)
     * @param status optional status filter (allowed values are new and active)
     * @return completable future resolving with a list of provisioning profile entities
     */
    public CompletableFuture<List<ProvisioningProfile>> getProvisioningProfiles(
        Optional<Integer> version, Optional<String> status
    ) throws Exception {
        return provisioningProfileClient.getProvisioningProfiles(version, status).thenApply(profiles -> {
            List<ProvisioningProfile> result = new ArrayList<>();
            profiles.forEach(profileDto -> result.add(new ProvisioningProfile(profileDto, provisioningProfileClient)));
            return result;
        });
    }
    
    /**
     * Retrieves a provisioning profile by id
     * @param provisioningProfileId provisioning profile id
     * @return completable future resolving with provisioning profile entity
     */
    public CompletableFuture<ProvisioningProfile> getProvisioningProfile(String provisioningProfileId) throws Exception {
        return provisioningProfileClient.getProvisioningProfile(provisioningProfileId).thenApply(profile -> {
            return new ProvisioningProfile(profile, provisioningProfileClient);
        });
    }
    
    /**
     * Creates a provisioning profile
     * @param profile provisioning profile data
     * @return completable future resolving with provisioning profile entity
     */
    public CompletableFuture<ProvisioningProfile> createProvisioningProfile(
        NewProvisioningProfileDto profile
    ) throws Exception {
        return provisioningProfileClient.createProvisioningProfile(profile).thenApply(id -> {
            ProvisioningProfileDto createdProfile = new ProvisioningProfileDto();
            createdProfile._id = id.id;
            createdProfile.status = "new";
            createdProfile.name = createdProfile.name;
            createdProfile.version = createdProfile.version;
            return new ProvisioningProfile(createdProfile, provisioningProfileClient);
        });
    }
}