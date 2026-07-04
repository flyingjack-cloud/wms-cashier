package top.flyingjack.cashier.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.flyingjack.cashier.service.OrderService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock OrderService orderService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService)).build();
    }

    @Test
    void createOrder_parsesIso8601SellingTimeParam() throws Exception {
        mockMvc.perform(post("/order")
                        .param("me_id", "10")
                        .param("selling_price", "150.00")
                        .param("selling_time", "2025-01-01T00:00:00Z")
                        .param("remark", "cash"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(orderService).insertOrder(
                eq(10), eq(new BigDecimal("150.00")), eq("cash"),
                eq(Instant.parse("2025-01-01T00:00:00Z")));
    }

    @Test
    void getOrdersByDateRange_parsesIso8601StartAndEndParams() throws Exception {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-02T00:00:00Z");
        when(orderService.getOrderCount(start, end)).thenReturn(0);
        when(orderService.getOrdersByPage(20, 0, start, end)).thenReturn(List.of());

        mockMvc.perform(get("/order/range")
                        .param("start", "2025-01-01T00:00:00Z")
                        .param("end", "2025-01-02T00:00:00Z")
                        .param("limit", "20")
                        .param("offset", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void getOrdersByDateRange_acceptsBareEpochMillisecondForBackwardCompatibility() throws Exception {
        // Spring's InstantFormatter tries Instant.ofEpochMilli(Long.parseLong(text)) before
        // falling back to ISO-8601 parsing, so old clients sending a bare ms number still work.
        Instant expected = Instant.ofEpochMilli(1783077681491L);
        when(orderService.getOrderCount(expected, expected)).thenReturn(0);
        when(orderService.getOrdersByPage(20, 0, expected, expected)).thenReturn(List.of());

        mockMvc.perform(get("/order/range")
                        .param("start", "1783077681491")
                        .param("end", "1783077681491")
                        .param("limit", "20")
                        .param("offset", "0"))
                .andExpect(status().isOk());
    }
}
