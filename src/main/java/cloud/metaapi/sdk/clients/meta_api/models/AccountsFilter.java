package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.ConnectionStatus;
import cloud.metaapi.sdk.clients.meta_api.models.MetatraderAccountDto.DeploymentState;

public class AccountsFilter {
    /**
     * Search offset (must be greater or equal to 0) or {@code null} (defaults to 0)
     */
    public Integer offset;
    /**
     * Search limit (must be greater or equal to 1 and less or equal to 1000) or {@code null} (defaults to 1000) 
     */
    public Integer limit;
    /**
     * MT version or {@code null}
     */
    public List<Version> version;
    /**
     * Account type or {@code null}
     */
    public List<AccountType> type;
    /**
     * Account state or {@code null}
     */
    public List<DeploymentState> state;
    /**
     * Connection status or {@code null}
     */
    public List<ConnectionStatus> connectionStatus;
    /**
     * If it is not {@code null}, searches over _id, name, server and login to match query
     */
    public String query;
    /**
     * Provisioning profile id or {@code null}
     */
    public String provisioningProfileId;
}