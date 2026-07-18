package top.flyingjack.cashier.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Component
public class OrderExtraSchemaValidator {
    private static final Set<String> SUPPORTED_TYPES =
            Set.of("text", "textarea", "number", "boolean", "date", "datetime", "select");

    public void validateSchema(JsonNode schema) {
        Assert.isTrue(schema != null && schema.isObject(), "schema must be a JSON object");
        JsonNode fields = schema.path("fields");
        Assert.isTrue(fields.isArray(), "template fields must be an array");
        Assert.isTrue(!fields.isEmpty(), "template fields cannot be empty");
        Set<String> keys = new HashSet<>();
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Assert.hasText(key, "template field key cannot be empty");
            Assert.isTrue(keys.add(key), "duplicate template field key: " + key);
            String type = field.path("type").asText("text");
            Assert.isTrue(SUPPORTED_TYPES.contains(type), "unsupported field type: " + type);
            if ("select".equals(type)) {
                assertSelectOptions(key, field);
            }
        }
    }

    public void validatePayload(JsonNode schema, JsonNode payload) {
        JsonNode fields = schema.path("fields");
        Assert.isTrue(fields.isArray(), "template fields must be an array");
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Assert.hasText(key, "template field key cannot be empty");
            JsonNode value = payload.get(key);
            boolean required = field.path("required").asBoolean(false);
            if (required) {
                Assert.isTrue(value != null && !value.isNull()
                                && !(value.isTextual() && !StringUtils.hasText(value.asText())),
                        "missing required field: " + key);
            }
            if (value != null && !value.isNull()) {
                validateFieldType(key, field, value);
            }
        }
    }

    private void validateFieldType(String key, JsonNode field, JsonNode value) {
        switch (field.path("type").asText("text")) {
            case "number":
                Assert.isTrue(value.isNumber(), "field must be number: " + key);
                break;
            case "boolean":
                Assert.isTrue(value.isBoolean(), "field must be boolean: " + key);
                break;
            case "select":
                Assert.isTrue(value.isTextual(), "field must be text: " + key);
                validateSelectValue(key, field, value);
                break;
            case "text":
            case "textarea":
            case "date":
            case "datetime":
                Assert.isTrue(value.isTextual(), "field must be text: " + key);
                break;
            default:
                break;
        }
    }

    private void validateSelectValue(String key, JsonNode field, JsonNode value) {
        JsonNode options = assertSelectOptions(key, field);
        for (JsonNode option : options) {
            if (option.asText().equals(value.asText())) {
                return;
            }
        }
        throw new IllegalArgumentException("invalid option for field: " + key);
    }

    private JsonNode assertSelectOptions(String key, JsonNode field) {
        JsonNode options = field.path("options");
        Assert.isTrue(options.isArray() && !options.isEmpty(), "select field must have options: " + key);
        return options;
    }
}
