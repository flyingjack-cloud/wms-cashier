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
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
import top.flyingjack.cashier.service.OrderExtraService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void getTemplates_defaultsToEnabledOnly() throws Exception {
        mockMvc.perform(get("/order-extra/templates"))
                .andExpect(status().isOk());

        verify(orderExtraService).getTemplates(false);
    }

    @Test
    void getTemplates_passesIncludeDisabled() throws Exception {
        mockMvc.perform(get("/order-extra/templates").param("includeDisabled", "true"))
                .andExpect(status().isOk());

        verify(orderExtraService).getTemplates(true);
    }

    @Test
    void createTemplate_passesReqToService() throws Exception {
        mockMvc.perform(post("/order-extra/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invoice\",\"name\":\"发票信息\","
                                + "\"schema\":{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderExtraTemplateReq> captor =
                ArgumentCaptor.forClass(OrderExtraTemplateReq.class);
        verify(orderExtraService).createTemplate(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("invoice");
        assertThat(captor.getValue().getName()).isEqualTo("发票信息");
        assertThat(captor.getValue().getSchema().path("fields").get(0).path("key").asText())
                .isEqualTo("a");
    }

    @Test
    void updateTemplate_passesCodeFromPath() throws Exception {
        mockMvc.perform(put("/order-extra/templates/invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"增值税发票\","
                                + "\"schema\":{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderExtraTemplateReq> captor =
                ArgumentCaptor.forClass(OrderExtraTemplateReq.class);
        verify(orderExtraService).updateTemplate(eq("invoice"), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("增值税发票");
    }

    @Test
    void deleteTemplate_disablesIt() throws Exception {
        mockMvc.perform(delete("/order-extra/templates/invoice"))
                .andExpect(status().isOk());

        verify(orderExtraService).disableTemplate("invoice");
    }

    @Test
    void setEnabled_passesFlagToService() throws Exception {
        mockMvc.perform(put("/order-extra/templates/invoice/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(orderExtraService).setTemplateEnabled("invoice", true);
    }
}
