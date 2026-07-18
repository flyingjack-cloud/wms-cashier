package top.flyingjack.cashier.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ReceiptLayoutValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of("text", "label", "image", "divider", "table");
    private static final Pattern EXTRA_FIELD_PATTERN = Pattern.compile("^extra\\.[^.]+\\.[^.]+$");

    public void validateLayout(JsonNode layout) {
        Assert.isTrue(layout != null && layout.isObject(), "layout must be a JSON object");
        if (layout.has("page")) {
            Assert.isTrue(layout.path("page").isObject(), "page must be a JSON object");
        }
        JsonNode rows = layout.path("rows");
        Assert.isTrue(rows.isArray(), "rows must be an array");
        Assert.isTrue(!rows.isEmpty(), "rows cannot be empty");
        for (JsonNode row : rows) {
            validateRow(row);
        }
    }

    private void validateRow(JsonNode row) {
        JsonNode columns = row.path("columns");
        Assert.isTrue(columns.isArray(), "columns must be an array");
        Assert.isTrue(!columns.isEmpty(), "columns cannot be empty");
        for (JsonNode column : columns) {
            validateColumn(column);
        }
    }

    private void validateColumn(JsonNode column) {
        String type = column.path("type").asText("");
        Assert.isTrue(SUPPORTED_TYPES.contains(type), "unsupported column type: " + type);
        if (column.has("style")) {
            Assert.isTrue(column.path("style").isObject(), "style must be a JSON object");
        }
        switch (type) {
            case "text":
                String textField = column.path("field").isMissingNode() ? null : column.path("field").asText();
                Assert.hasText(textField, "text column must have a field");
                Assert.isTrue(isKnownField(textField), "unknown field: " + textField);
                break;
            case "label":
                String labelText = column.path("field").isMissingNode() ? null : column.path("field").asText();
                Assert.hasText(labelText, "label column must have non-empty field text");
                break;
            default:
                break;
        }
    }

    private boolean isKnownField(String field) {
        return ReceiptFixedFields.LABELS.containsKey(field) || EXTRA_FIELD_PATTERN.matcher(field).matches();
    }
}
