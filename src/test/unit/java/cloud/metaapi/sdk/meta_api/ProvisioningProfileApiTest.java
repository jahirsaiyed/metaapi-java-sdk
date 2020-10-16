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
import cloud.metaapi.sdk.clients.meta_api.models.NewProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileDto;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileIdDto;
import cloud.metaapi.sdk.clients.meta_api.models.ProvisioningProfileUpdateDto;

/**
 * Tests {@link ProvisioningProfileApi}
 */
class ProvisioningProfileApiTest {

    private ProvisioningProfileClient client;
    private ProvisioningProfileApi api;
    
    @BeforeEach
    void setUp() {
        client = Mockito.mock(ProvisioningProfileClient.class);
        api = new ProvisioningProfileApi(client);
    }

    /**
     * Tests {@link ProvisioningProfileApi#getProvisioningProfiles(Optional, Optional)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRetrievesProvisioningProfiles(ProvisioningProfileDto expectedDto) {
        Mockito.when(client.getProvisioningProfiles(4, "new"))
            .thenReturn(CompletableFuture.completedFuture(Lists.list(expectedDto)));
        List<ProvisioningProfile> expectedProfiles = Lists.list(new ProvisioningProfile(expectedDto, client));
        List<ProvisioningProfile> actualProfiles = api.getProvisioningProfiles(4, "new").join();
        assertThat(actualProfiles).usingRecursiveComparison().isEqualTo(expectedProfiles);
    }
    
    /**
     * Tests {@link ProvisioningProfileApi#getProvisioningProfile(String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRertrievesProvisioningProfileById(ProvisioningProfileDto expectedDto) {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(expectedDto));
        ProvisioningProfile actualProfile = api.getProvisioningProfile("id").join();
        assertEquals(expectedDto._id, actualProfile.getId());
        assertEquals(expectedDto.name, actualProfile.getName());
        assertEquals(expectedDto.status, actualProfile.getStatus());
        assertEquals(expectedDto.version, actualProfile.getVersion());
        assertEquals(expectedDto.brokerTimezone, actualProfile.getBrokerTimezone());
        assertEquals(expectedDto.brokerDSTSwitchTimezone, actualProfile.getBrokerDSTSwitchTimezone());
    }
    
    /**
     * Tests {@link ProvisioningProfileApi#createProvisioningProfile(NewProvisioningProfileDto)}
     */
    @Test
    void testCreatesProvisioningProfile() {
        ProvisioningProfileIdDto expectedId = new ProvisioningProfileIdDto() {{ id = "id"; }};
        Mockito.when(client.createProvisioningProfile(Mockito.any(NewProvisioningProfileDto.class)))
            .thenReturn(CompletableFuture.completedFuture(expectedId));
        NewProvisioningProfileDto newDto = new NewProvisioningProfileDto() {{
            name = "name";
            version = 4;
            brokerTimezone = "EET";
            brokerDSTSwitchTimezone = "EET";
        }};
        ProvisioningProfile profile = api.createProvisioningProfile(newDto).join();
        assertEquals("id", profile.getId());
        assertEquals("name", profile.getName());
        assertEquals(4, profile.getVersion());
        assertEquals("new", profile.getStatus());
        assertEquals("EET", profile.getBrokerTimezone());
        assertEquals("EET", profile.getBrokerDSTSwitchTimezone());
        Mockito.verify(client).createProvisioningProfile(newDto);
    }
    
    /**
     * Tests {@link ProvisioningProfile#reload()}
     */
    @Test
    void testReloadsProvisioningProfile() {
        ProvisioningProfileDto firstCallProfile = new ProvisioningProfileDto();
        firstCallProfile._id = "id";
        firstCallProfile.status = "new";
        firstCallProfile.brokerTimezone = "EET";
        firstCallProfile.brokerDSTSwitchTimezone = "EET";
        ProvisioningProfileDto secondCallProfile = new ProvisioningProfileDto();
        secondCallProfile._id = "id";
        secondCallProfile.status = "active";
        secondCallProfile.brokerTimezone = "EET";
        secondCallProfile.brokerDSTSwitchTimezone = "EET";
        Mockito.when(client.getProvisioningProfile("id"))
            .thenReturn(CompletableFuture.completedFuture(firstCallProfile))
            .thenReturn(CompletableFuture.completedFuture(secondCallProfile));
        ProvisioningProfile profile = api.getProvisioningProfile("id").join();
        assertEquals("new", profile.getStatus());
        profile.reload().join();
        assertEquals("active", profile.getStatus());
        Mockito.verify(client, Mockito.times(2)).getProvisioningProfile("id");
    }
    
    /**
     * Tests {@link ProvisioningProfile#remove()}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testRemovesProvisioningProfile(ProvisioningProfileDto profileDto) {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.deleteProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").join();
        profile.remove().join();
        Mockito.verify(client).deleteProvisioningProfile("id");
    }
    
    /**
     * Tests {@link ProvisioningProfile#uploadFile(String, String)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testUploadsAFileToProvisioningProfile(ProvisioningProfileDto profileDto) {
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.uploadProvisioningProfileFile("id", "broker.srv", "/path/to/file.srv"))
            .thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").join();
        profile.uploadFile("broker.srv", "/path/to/file.srv").join();
        Mockito.verify(client).uploadProvisioningProfileFile("id", "broker.srv", "/path/to/file.srv");
    }
    
    /**
     * Tests {@link ProvisioningProfile#update(ProvisioningProfileUpdateDto)}
     */
    @ParameterizedTest
    @MethodSource("provideProvisioningProfile")
    void testUpdatesProvisioningProfile(ProvisioningProfileDto profileDto) {
        ProvisioningProfileUpdateDto updateDto = new ProvisioningProfileUpdateDto();
        updateDto.name = "name";
        Mockito.when(client.getProvisioningProfile("id")).thenReturn(CompletableFuture.completedFuture(profileDto));
        Mockito.when(client.updateProvisioningProfile("id", updateDto))
            .thenReturn(CompletableFuture.completedFuture(null));
        ProvisioningProfile profile = api.getProvisioningProfile("id").join();
        profile.update(updateDto).join();
        Mockito.verify(client).updateProvisioningProfile("id", updateDto);
    }
    
    private static Stream<Arguments> provideProvisioningProfile() {
        ProvisioningProfileDto profile = new ProvisioningProfileDto();
        profile._id = "id";
        profile.name = "name";
        profile.version = 4;
        profile.status = "new";
        profile.brokerTimezone = "EET";
        profile.brokerDSTSwitchTimezone = "EET";
        return Stream.of(Arguments.of(profile));
    }
}