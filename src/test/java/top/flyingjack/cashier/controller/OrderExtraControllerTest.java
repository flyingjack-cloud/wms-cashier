package top.flyingjack.cashier.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.flyingjack.cashier.service.OrderExtraService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderExtraControllerTest {

    @Mock OrderExtraService orderExtraService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderExtraController(orderExtraService)).build();
    }

    @Test
    void saveExtra_passesDynamicPayloadToService() throws Exception {
        mockMvc.perform(put("/order/123/extra/invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceTitle\":\"上海某某公司\",\"serialNo\":\"SN123\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(orderExtraService).saveExtra(org.mockito.ArgumentMatchers.eq(123),
                org.mockito.ArgumentMatchers.eq("invoice"), captor.capture());
        assertThat(captor.getValue()).containsEntry("invoiceTitle", "上海某某公司");
        assertThat(captor.getValue()).containsEntry("serialNo", "SN123");
    }

    @Test
    void getTemplates_callsService() throws Exception {
        mockMvc.perform(get("/order-extra/templates"))
                .andExpect(status().isOk());

        verify(orderExtraService).getTemplates(false);
    }
}
