package cloud.metaapi.sdk.meta_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import cloud.metaapi.sdk.clients.meta_api.MetatraderDemoAccountClient;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDemoAccountDto;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT4DemoAccount;
import cloud.metaapi.sdk.clients.meta_api.models.NewMT5DemoAccount;

/**
 * Tests {@link MetatraderDemoAccountApi}, {@link MetatraderDemoAccount}
 */
class MetatraderDemoAccountApiTest {

    private MetatraderDemoAccountApi api;
    private MetatraderDemoAccountClient client;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(MetatraderDemoAccountClient.class);
        api = new MetatraderDemoAccountApi(client);
    }
    
    /**
     * Tests {@link MetatraderDemoAccountApi#createMT4DemoAccount(String, NewMT4DemoAccount)}
     */
    @Test
    void testCreatesMt4DemoAccount() {
        NewMT4DemoAccount newAccountData = new NewMT4DemoAccount() {{
            balance = 10;
            email = "test@test.com";
            leverage = 15;
        }};
        MetatraderDemoAccountDto accountDto = new MetatraderDemoAccountDto() {{
            login = "12345";
            password = "qwerty";
            serverName = "HugosWay-Demo3";
        }};
        Mockito.when(client.createMT4DemoAccount("profileId1", newAccountData))
            .thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderDemoAccount expected = new MetatraderDemoAccount(accountDto);
        MetatraderDemoAccount actual = api.createMT4DemoAccount("profileId1", newAccountData).join();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link MetatraderDemoAccountApi#createMT5DemoAccount(String, NewMT5DemoAccount)}
     */
    @Test
    void testCreatesMt5DemoAccount() {
        NewMT5DemoAccount newAccountData = new NewMT5DemoAccount() {{
            balance = 15;
            email = "test@test.com";
            leverage = 20;
            serverName = "server";
        }};
        MetatraderDemoAccountDto accountDto = new MetatraderDemoAccountDto() {{
            login = "12345";
            password = "qwerty";
            serverName = "HugosWay-Demo3";
        }};
        Mockito.when(client.createMT5DemoAccount("profileId2", newAccountData))
            .thenReturn(CompletableFuture.completedFuture(accountDto));
        MetatraderDemoAccount expected = new MetatraderDemoAccount(accountDto);
        MetatraderDemoAccount actual = api.createMT5DemoAccount("profileId2", newAccountData).join();
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
}