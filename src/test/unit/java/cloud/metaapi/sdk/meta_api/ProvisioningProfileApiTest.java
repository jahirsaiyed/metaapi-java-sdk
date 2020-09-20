package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.meta_api.ProvisioningProfileClient;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileUpdateDto;

/**
 * Tests {@link ProvisioningProfileApi}
 */
class ProvisioningProfileApiTest {

    private ProvisioningProfileClient client;
    private ProvisioningProfileApi api;
    
    @BeforeEach
    void setUp() throws Exception {
        client = Mockito.mock(ProvisioningProfileClient.class);
        api = new ProvisioningProfileApi(client);
    }

    /**
     * Tests {@link ProvisioningProfileApi#getProvisioningProfiles(Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRetrievesProvisioningProfiles(ProvisioningProfileDto expectedDto) throws Exception {
        Mockito.when(client.getProvisioningProfiles(4, "new"))
            .thenReturn(CompletableFuture.completedFuture(Lists.list(expectedDto)));
        List<ProvisioningProfile> expectedProfiles = Lists.list(new ProvisioningProfile(expectedDto, client));
        List<ProvisioningProfile> actualProfiles = api.getProvisioningProfiles(4, "new").get();
        assertThat(actualProfiles).usingRecursiveComparison().isEqualTo(expectedProfiles);
    }
    
    /**
     * Tests {@link ProvisioningProfileApi#getProvisioningProfile(String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRertrievesProvisioningProfileById(ProvisioningProfileDto expectedDto) throws Exception {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(expectedDto));
        ProvisioningProfile actualProfile = api.getProvisioningProfile("id").get();
        assertEquals(expectedDto._id, actualProfile.getId());
        assertEquals(expectedDto.name, actualProfile.getName());
        assertEquals(expectedDto.status, actualProfile.getStatus());
        assertEquals(expectedDto.version, actualProfile.getVersion());
    }
    
    /**
     * Tests {@link ProvisioningProfile#reload()}
     */
    @Test
    void testReloadsProvisioningProfile() throws Exception {
        ProvisioningProfileDto firstCallProfile = new ProvisioningProfileDto();
        firstCallProfile._id = "id";
        firstCallProfile.status = "new";
        ProvisioningProfileDto secondCallProfile = new ProvisioningProfileDto();
        secondCallProfile._id = "id";
        secondCallProfile.status = "active";
        Mockito.when(client.getProvisioningProfile("id"))
            .thenReturn(CompletableFuture.completedFuture(firstCallProfile))
            .thenReturn(CompletableFuture.completedFuture(secondCallProfile));
        ProvisioningProfile profile = api.getProvisioningProfile("id").get();
        assertEquals("new", profile.getStatus());
        profile.reload().get();
        assertEquals("active", profile.getStatus());
        Mockito.verify(client, Mockito.times(2)).getProvisioningProfile("id");
    }
    
    /**
     * Tests {@link ProvisioningProfile#remove()}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRemovesProvisioningProfile(ProvisioningProfileDto profileDto) throws Exception {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.deleteProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").get();
        profile.remove().get();
        Mockito.verify(client).deleteProvisioningProfile("id");
    }
    
    /**
     * Tests {@link ProvisioningProfile#uploadFile(String, String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testUploadsAFileToProvisioningProfile(ProvisioningProfileDto profileDto) throws Exception {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.uploadProvisioningProfileFile("id", "broker.srv", "/path/to/file.srv"))
            .thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").get();
        profile.uploadFile("broker.srv", "/path/to/file.srv").get();
        Mockito.verify(client).uploadProvisioningProfileFile("id", "broker.srv", "/path/to/file.srv");
    }
    
    /**
     * Tests {@link ProvisioningProfile#update(ProvisioningProfileUpdateDto)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testUpdatesProvisioningProfile(ProvisioningProfileDto profileDto) throws Exception {
        ProvisioningProfileUpdateDto updateDto = new ProvisioningProfileUpdateDto();
        updateDto.name = "name";
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.updateProvisioningProfile("id", updateDto))
            .thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").get();
        profile.update(updateDto).get();
        Mockito.verify(client).updateProvisioningProfile("id", updateDto);
    }
    
    private static Stream<Arguments> provideProvisioningProfile() {
        ProvisioningProfileDto profile = new ProvisioningProfileDto();
        profile._id = "id";
        profile.name = "name";
        profile.version = 4;
        profile.status = "new";
        return Stream.of(Arguments.of(profile));
    }
}