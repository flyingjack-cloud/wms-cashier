package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderExtraSchemaValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderExtraSchemaValidator validator = new OrderExtraSchemaValidator();

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void validateSchema_acceptsValidSchema() {
        JsonNode schema = json("{\"fields\":["
                + "{\"key\":\"invoiceTitle\",\"label\":\"发票抬头\",\"type\":\"text\",\"required\":true},"
                + "{\"key\":\"payMethod\",\"type\":\"select\",\"options\":[\"现金\",\"微信\"]}"
                + "]}");

        assertThatCode(() -> validator.validateSchema(schema)).doesNotThrowAnyException();
    }

    @Test
    void validateSchema_rejectsNonObject() {
        assertThatThrownBy(() -> validator.validateSchema(json("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema must be a JSON object");
    }

    @Test
    void validateSchema_rejectsMissingFields() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"foo\":1}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields must be an array");
    }

    @Test
    void validateSchema_rejectsEmptyFields() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"fields\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields cannot be empty");
    }

    @Test
    void validateSchema_rejectsBlankKey() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template field key cannot be empty");
    }

    @Test
    void validateSchema_rejectsDuplicateKey() {
        assertThatThrownBy(() -> validator.validateSchema(json("{\"fields\":["
                + "{\"key\":\"a\",\"type\":\"text\"},{\"key\":\"a\",\"type\":\"number\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate template field key: a");
    }

    @Test
    void validateSchema_rejectsUnsupportedType() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"foo\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported field type: foo");
    }

    @Test
    void validateSchema_rejectsSelectWithoutOptions() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"select\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select field must have options: a");
    }

    @Test
    void validateSchema_rejectsSelectWithEmptyOptions() {
        assertThatThrownBy(() -> validator.validateSchema(
                json("{\"fields\":[{\"key\":\"a\",\"type\":\"select\",\"options\":[]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select field must have options: a");
    }

    @Test
    void validatePayload_rejectsMissingRequiredField() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\",\"required\":true}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field: a");
    }

    @Test
    void validatePayload_rejectsBlankRequiredText() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\",\"required\":true}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"   \"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field: a");
    }

    @Test
    void validatePayload_rejectsWrongNumberType() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"number\"}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field must be number: a");
    }

    @Test
    void validatePayload_rejectsWrongBooleanType() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"boolean\"}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"a\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field must be boolean: a");
    }

    @Test
    void validatePayload_acceptsSelectValueInOptions() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"payMethod\",\"type\":\"select\","
                + "\"options\":[\"现金\",\"微信\"]}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"payMethod\":\"微信\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePayload_rejectsSelectValueNotInOptions() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"payMethod\",\"type\":\"select\","
                + "\"options\":[\"现金\",\"微信\"]}]}");

        assertThatThrownBy(() -> validator.validatePayload(schema, json("{\"payMethod\":\"比特币\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid option for field: payMethod");
    }

    @Test
    void validatePayload_toleratesUndeclaredFields() {
        // 显式决策：schema 未声明的字段原样保留，不报错（见 spec"显式决策"章节）
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"a\":\"x\",\"b\":\"y\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePayload_skipsTypeCheckForNullValue() {
        JsonNode schema = json("{\"fields\":[{\"key\":\"a\",\"type\":\"number\",\"required\":false}]}");

        assertThatCode(() -> validator.validatePayload(schema, json("{\"a\":null}")))
                .doesNotThrowAnyException();
    }
}
