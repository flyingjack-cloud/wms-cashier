package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptLayoutValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReceiptLayoutValidator validator = new ReceiptLayoutValidator();

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void receiptFixedFields_containsExactlyNineExpectedKeys() {
        assertThat(ReceiptFixedFields.LABELS.keySet()).containsExactlyInAnyOrder(
                "store.storeName", "store.address",
                "order.sellingTime", "order.brand", "order.model", "order.imei",
                "order.sellingPrice", "order.cost",
                "cashier.printedBy");
    }

    @Test
    void validateLayout_acceptsValidLayout() {
        JsonNode layout = json("{\"page\":{},\"rows\":["
                + "{\"height\":\"30px\",\"columns\":["
                + "{\"span\":1,\"type\":\"label\",\"field\":\"IMEI:\",\"style\":{\"bold\":true}},"
                + "{\"span\":2,\"type\":\"text\",\"field\":\"order.imei\",\"style\":{}}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsTextColumnWithExtraField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":["
                + "{\"span\":1,\"type\":\"text\",\"field\":\"extra.invoice.invoiceTitle\"}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsDividerColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsImageColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"image\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_acceptsTableColumnWithoutField() {
        JsonNode layout = json("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"table\"}]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }

    @Test
    void validateLayout_rejectsNonObject() {
        assertThatThrownBy(() -> validator.validateLayout(json("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layout must be a JSON object");
    }

    @Test
    void validateLayout_rejectsNonObjectPage() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"page\":[],\"rows\":[{\"columns\":[{\"type\":\"divider\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be a JSON object");
    }

    @Test
    void validateLayout_rejectsMissingRows() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"page\":{}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows must be an array");
    }

    @Test
    void validateLayout_rejectsEmptyRows() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows cannot be empty");
    }

    @Test
    void validateLayout_rejectsRowWithMissingColumns() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[{}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns must be an array");
    }

    @Test
    void validateLayout_rejectsRowWithEmptyColumns() {
        assertThatThrownBy(() -> validator.validateLayout(json("{\"rows\":[{\"columns\":[]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns cannot be empty");
    }

    @Test
    void validateLayout_rejectsUnsupportedColumnType() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"chart\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported column type: chart");
    }

    @Test
    void validateLayout_rejectsNonObjectStyle() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"divider\",\"style\":\"bold\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("style must be a JSON object");
    }

    @Test
    void validateLayout_rejectsTextColumnWithoutField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text column must have a field");
    }

    @Test
    void validateLayout_rejectsTextColumnWithUnknownField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\",\"field\":\"order.bogus\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field: order.bogus");
    }

    @Test
    void validateLayout_rejectsTextColumnWithMalformedExtraField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"text\",\"field\":\"extra.invoice\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown field: extra.invoice");
    }

    @Test
    void validateLayout_rejectsLabelColumnWithoutField() {
        assertThatThrownBy(() -> validator.validateLayout(
                json("{\"rows\":[{\"columns\":[{\"type\":\"label\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label column must have non-empty field text");
    }

    @Test
    void validateLayout_acceptsLabelColumnWithArbitraryText() {
        JsonNode layout = json("{\"rows\":[{\"columns\":["
                + "{\"type\":\"label\",\"field\":\"合计：\"}"
                + "]}]}");

        assertThatCode(() -> validator.validateLayout(layout)).doesNotThrowAnyException();
    }
}
