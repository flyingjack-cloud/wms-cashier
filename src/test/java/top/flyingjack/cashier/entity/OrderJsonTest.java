package top.flyingjack.cashier.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
class OrderJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sellingTime_parsesIso8601UtcString() throws Exception {
        String json = "{\"sellingTime\": \"2025-01-01T00:00:00Z\"}";

        Order order = objectMapper.readValue(json, Order.class);

        assertThat(order.getSellingTime()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void sellingTime_rejectsOffsetLessLocalString() {
        String json = "{\"sellingTime\": \"2025-01-01T00:00:00\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, Order.class))
                .isInstanceOf(InvalidFormatException.class);
    }
}
