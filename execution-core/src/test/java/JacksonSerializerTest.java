import com.example.executioncore.domain.serializer.JacksonSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JacksonSerializerTest {
    private final JacksonSerializer serializer = new JacksonSerializer();

    record TestDto(String value) {}

    record ApiResponse<T>(T body) {}

    @Test
    void shouldDeserializeNestedParameterizedResponseType() {
        TypeReference<ApiResponse<List<TestDto>>> responseType = new TypeReference<>() {};
        String json = """
                {"body":[{"value":"first"},{"value":"second"}]}
                """;

        ApiResponse<List<TestDto>> response = serializer.deserialize(json, responseType.getType());

        assertInstanceOf(TestDto.class, response.body().get(0));
        assertEquals("first", response.body().get(0).value());
        assertEquals("second", response.body().get(1).value());
    }
}
