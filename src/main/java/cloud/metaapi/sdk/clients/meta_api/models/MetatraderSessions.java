package cloud.metaapi.sdk.clients.meta_api.models;

import java.util.List;

/**
 * Metatrader trade or quote session container, indexed by weekday
 */
public class MetatraderSessions {
    /**
     * List of sessions for SUNDAY, or {@code null}
     */
    public List<MetatraderSession> SUNDAY;
    /**
     * List of sessions for MONDAY, or {@code null}
     */
    public List<MetatraderSession> MONDAY;
    /**
     * List of sessions for TUESDAY, or {@code null}
     */
    public List<MetatraderSession> TUESDAY;
    /**
     * List of sessions for WEDNESDAY, or {@code null}
     */
    public List<MetatraderSession> WEDNESDAY;
    /**
     * List of sessions for THURSDAY, or {@code null}
     */
    public List<MetatraderSession> THURSDAY;
    /**
     * List of sessions for FRIDAY, or {@code null}
     */
    public List<MetatraderSession> FRIDAY;
    /**
     * List of sessions for SATURDAY, or {@code null}
     */
    public List<MetatraderSession> SATURDAY;
}