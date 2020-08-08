package cloud.metaapi.sdk.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Class for getting a singleton instance of a configured JSON object mapper
 */
public class JsonMapper {
    
    private static ObjectMapper mapper = null;
    
    /**
     * Returns a singleton instance of a configured JSON object mapper
     * @return json object mapper instance
     */
    public static ObjectMapper getInstance() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new Jdk8Module());
        }
        return mapper;
    }
    
    private JsonMapper() {}
}