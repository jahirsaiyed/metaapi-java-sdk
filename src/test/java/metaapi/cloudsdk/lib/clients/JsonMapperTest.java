package metaapi.cloudsdk.lib.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import metaapi.cloudsdk.lib.clients.EnumModelExample.ExampleEnum;

/**
 * Class for testing json enum parsing
 */
class EnumModelExample {
    
    /**
     * Some example enum
     */
    public enum ExampleEnum { SOME_VALUE }
    
    /**
     * Some example field using the enum
     */
    public ExampleEnum field;
}

/**
 * Class for testing json list parsing
 */
class ListModelExample {

    /**
     * Some example list field
     */
    public List<Integer> list;
}

/**
 * Tests {@link JsonMapper}
 */
public class JsonMapperTest {
    
    /**
     * Tests {@link JsonMapper#getInstance()}
     */
    @Test
    void testTransformsStringFieldIntoEnumValueByName() throws Exception {
        String json = "{\"field\": \"SOME_VALUE\"}";
        EnumModelExample model = JsonMapper.getInstance().readValue(json, EnumModelExample.class);
        assertEquals(ExampleEnum.SOME_VALUE, model.field);
    }
    
    /**
     * Tests {@link JsonMapper#getInstance()}
     */
    @Test
    void testTransformsModelEnumValueNameIntoJsonString() throws Exception {
        EnumModelExample model = new EnumModelExample();
        model.field = ExampleEnum.SOME_VALUE;
        JsonNode actualJson = JsonMapper.getInstance().valueToTree(model);
        assertTrue(actualJson.get("field").isTextual());
        assertEquals(actualJson.get("field").asText(), "SOME_VALUE");
    }
    
    /**
     * Tests {@link JsonMapper#getInstance()}
     */
    @Test
    void testTransformsListIntoJsonArray() throws Exception {
        String jsonString = "{\"list\": [28, 42]}";
        ListModelExample expected = new ListModelExample();
        expected.list = new ArrayList<>();
        expected.list.add(28);
        expected.list.add(42);
        ListModelExample actual = JsonMapper.getInstance().readValue(jsonString, ListModelExample.class);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
    
    /**
     * Tests {@link JsonMapper#getInstance()}
     */
    @Test
    void testTransformsJsonArrayIntoLists() throws Exception {
        ListModelExample model = new ListModelExample();
        model.list = new LinkedList<>();
        model.list.add(28);
        model.list.add(42);
        String expected = "{\"list\":[28,42]}";
        String actual = JsonMapper.getInstance().writeValueAsString(model);
        assertEquals(expected, actual);
    }
}