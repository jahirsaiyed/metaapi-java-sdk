package cloud.metaapi.sdk.clients.meta_api.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * MT version
 */
public enum Version {
    VERSION_4(4),
    VERSION_5(5);
    
    private final int version;

    /**
     * Returns enum version from its number
     * @param version version number
     * @return enum version corresponding specified number
     */
    @JsonCreator
    public static Version ofNumber(final int version) {
        for (Version item : Version.values())
            if (item.version == version) return item;
        return null;
    }
    
    private Version(int version) {
        this.version = version;
    }
    
    /**
     * Returns version number
     * @return version number
     */
    @JsonValue
    public int getNumber() {
        return version;
    }
    
    /**
     * Returns version number as string
     * @return version number as string
     */
    @Override
    public String toString() {
        return String.valueOf(version);
    }
}