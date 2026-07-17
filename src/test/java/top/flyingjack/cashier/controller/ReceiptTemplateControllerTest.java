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
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.service.ReceiptTemplateService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReceiptTemplateControllerTest {

    @Mock ReceiptTemplateService receiptTemplateService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReceiptTemplateController(receiptTemplateService)).build();
    }

    @Test
    void getTemplates_defaultsToEnabledOnly() throws Exception {
        mockMvc.perform(get("/receipt-templates"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplates(false);
    }

    @Test
    void getTemplates_passesIncludeDisabled() throws Exception {
        mockMvc.perform(get("/receipt-templates").param("includeDisabled", "true"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplates(true);
    }

    @Test
    void getAvailableFields_resolvesToFieldsEndpointNotPrinterTypePath() throws Exception {
        mockMvc.perform(get("/receipt-templates/fields"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getAvailableFields();
        verifyNoMoreInteractions(receiptTemplateService);
    }

    @Test
    void getTemplate_passesPrinterTypeFromPath() throws Exception {
        mockMvc.perform(get("/receipt-templates/A4"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).getTemplate("A4");
    }

    @Test
    void createTemplate_passesReqToService() throws Exception {
        mockMvc.perform(post("/receipt-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"printerType\":\"A4\","
                                + "\"layout\":{\"rows\":[{\"columns\":["
                                + "{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReceiptTemplateReq> captor = ArgumentCaptor.forClass(ReceiptTemplateReq.class);
        verify(receiptTemplateService).createTemplate(captor.capture());
        assertThat(captor.getValue().getPrinterType()).isEqualTo("A4");
        assertThat(captor.getValue().getLayout().path("rows").get(0).path("columns").get(0)
                .path("field").asText()).isEqualTo("order.imei");
    }

    @Test
    void updateTemplate_passesPrinterTypeFromPath() throws Exception {
        mockMvc.perform(put("/receipt-templates/A4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReceiptTemplateReq> captor = ArgumentCaptor.forClass(ReceiptTemplateReq.class);
        verify(receiptTemplateService).updateTemplate(eq("A4"), captor.capture());
        assertThat(captor.getValue().getLayout().path("rows").get(0).path("columns").get(0)
                .path("type").asText()).isEqualTo("divider");
    }

    @Test
    void deleteTemplate_disablesIt() throws Exception {
        mockMvc.perform(delete("/receipt-templates/A4"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).disableTemplate("A4");
    }

    @Test
    void setEnabled_passesFlagToService() throws Exception {
        mockMvc.perform(put("/receipt-templates/A4/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(receiptTemplateService).setTemplateEnabled("A4", true);
    }
}
