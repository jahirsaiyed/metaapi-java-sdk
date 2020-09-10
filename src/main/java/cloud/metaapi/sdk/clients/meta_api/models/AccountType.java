package cloud.metaapi.sdk.clients.meta_api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Account type
 */
public enum AccountType {
    CLOUD("cloud"),
    SELF_HOSTED("self-hosted");
    
    private final String type;

    /**
     * Returns enum account type from its value
     * @param value value of enum type
     * @return enum type corresponding specified value
     */
    @JsonCreator
    public static AccountType ofValue(final String value) {
        for (AccountType item : AccountType.values())
            if (item.type.equals(value)) return item;
        return null;
    }
    
    private AccountType(String type) {
        this.type = type;
    }
    
    /**
     * Returns the value of type
     * @return the value of type
     */
    @JsonValue
    public String getValue() {
        return type;
    }
    
    /**
     * Returns the value of type
     * @return the value of type
     */
    @Override
    public String toString() {
        return type;
    }
}