package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import top.flyingjack.cashier.entity.AvailableFieldsDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.ReceiptTemplate;
import top.flyingjack.cashier.entity.ReceiptTemplateDto;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.mapper.OrderExtraMapper;
import top.flyingjack.cashier.mapper.ReceiptTemplateMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReceiptTemplateServiceTest {
    ReceiptTemplateMapper receiptTemplateMapper;
    OrderExtraMapper orderExtraMapper;
    WmsSecurityContext securityContext;
    ReceiptTemplateService receiptTemplateService;

    @BeforeEach
    void setUp() {
        receiptTemplateMapper = mock(ReceiptTemplateMapper.class);
        orderExtraMapper = mock(OrderExtraMapper.class);
        securityContext = mock(WmsSecurityContext.class);
        receiptTemplateService = new ReceiptTemplateService(receiptTemplateMapper, orderExtraMapper,
                securityContext, new ObjectMapper(), new ReceiptLayoutValidator());
        when(securityContext.currentGroupId()).thenReturn(1);
    }

    private JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReceiptTemplateReq req(String printerType, String layout) {
        ReceiptTemplateReq r = new ReceiptTemplateReq();
        r.setPrinterType(printerType);
        r.setLayout(json(layout));
        return r;
    }

    private ReceiptTemplate template() {
        ReceiptTemplate t = new ReceiptTemplate();
        t.setId(7);
        t.setGroupId(1);
        t.setPrinterType("A4");
        t.setEnabled(true);
        t.setLayout("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}");
        return t;
    }

    private static final String VALID_LAYOUT =
            "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}";

    @Test
    void getTemplates_returnsLayoutAsJson() {
        when(receiptTemplateMapper.findTemplates(1, false)).thenReturn(List.of(template()));

        List<ReceiptTemplateDto> result = receiptTemplateService.getTemplates(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrinterType()).isEqualTo("A4");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(0).getLayout().path("rows").get(0).path("columns").get(0)
                .path("field").asText()).isEqualTo("order.imei");
    }

    @Test
    void getTemplate_returnsEnabledTemplateOnly() {
        when(receiptTemplateMapper.findEnabledTemplateByPrinterType(1, "A4")).thenReturn(template());

        ReceiptTemplateDto result = receiptTemplateService.getTemplate("A4");

        assertThat(result.getPrinterType()).isEqualTo("A4");
    }

    @Test
    void getTemplate_rejectsUnknownOrDisabledPrinterType() {
        when(receiptTemplateMapper.findEnabledTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.getTemplate("A4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");
    }

    @Test
    void createTemplate_insertsEnabledTemplate() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT));

        ArgumentCaptor<ReceiptTemplate> captor = ArgumentCaptor.forClass(ReceiptTemplate.class);
        verify(receiptTemplateMapper).insertTemplate(captor.capture());
        ReceiptTemplate saved = captor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(1);
        assertThat(saved.getPrinterType()).isEqualTo("A4");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getLayout()).contains("order.imei");
    }

    @Test
    void createTemplate_rejectsUnsupportedPrinterType() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("DOT_MATRIX", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported printer type: DOT_MATRIX");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsInvalidLayout() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", "{\"rows\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows cannot be empty");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsDuplicatePrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template already exists: A4");

        verify(receiptTemplateMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsRaceDuplicateInsert() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate key"))
                .when(receiptTemplateMapper).insertTemplate(any());

        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req("A4", VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template already exists: A4");
    }

    @Test
    void updateTemplate_persistsNewLayout() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        ReceiptTemplateDto result = receiptTemplateService.updateTemplate("A4",
                req(null, "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}"));

        ArgumentCaptor<ReceiptTemplate> captor = ArgumentCaptor.forClass(ReceiptTemplate.class);
        verify(receiptTemplateMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getLayout()).contains("divider").doesNotContain("order.imei");
        assertThat(result.getLayout().path("rows").get(0).path("columns").get(0)
                .path("type").asText()).isEqualTo("divider");
    }

    @Test
    void updateTemplate_rejectsUnknownPrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4", req(null, VALID_LAYOUT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");

        verify(receiptTemplateMapper, never()).updateTemplate(any());
    }

    @Test
    void updateTemplate_rejectsInvalidLayout() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4",
                req(null, "{\"rows\":[{\"columns\":[{\"type\":\"chart\"}]}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported column type: chart");

        verify(receiptTemplateMapper, never()).updateTemplate(any());
    }

    @Test
    void disableTemplate_setsEnabledFalse() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(template());

        receiptTemplateService.disableTemplate("A4");

        verify(receiptTemplateMapper).updateEnabled(1, "A4", false);
    }

    @Test
    void setTemplateEnabled_rejectsUnknownPrinterType() {
        when(receiptTemplateMapper.findTemplateByPrinterType(1, "A4")).thenReturn(null);

        assertThatThrownBy(() -> receiptTemplateService.setTemplateEnabled("A4", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt template not found: A4");

        verify(receiptTemplateMapper, never()).updateEnabled(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getAvailableFields_returnsFixedAndExtraFields() {
        OrderExtraTemplate invoiceTemplate = new OrderExtraTemplate();
        invoiceTemplate.setCode("invoice");
        invoiceTemplate.setName("发票信息");
        invoiceTemplate.setSchemaJson("{\"fields\":["
                + "{\"key\":\"invoiceTitle\",\"label\":\"发票抬头\",\"type\":\"text\"},"
                + "{\"key\":\"taxNo\",\"type\":\"text\"}"
                + "]}");
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of(invoiceTemplate));

        AvailableFieldsDto result = receiptTemplateService.getAvailableFields();

        assertThat(result.getFixed()).hasSize(9);
        assertThat(result.getFixed()).extracting("field").contains(
                "store.storeName", "order.imei", "cashier.printedBy");

        assertThat(result.getExtra()).hasSize(2);
        assertThat(result.getExtra().get(0).getField()).isEqualTo("extra.invoice.invoiceTitle");
        assertThat(result.getExtra().get(0).getLabel()).isEqualTo("发票信息 - 发票抬头");
        assertThat(result.getExtra().get(0).getTemplateCode()).isEqualTo("invoice");
        assertThat(result.getExtra().get(0).getKey()).isEqualTo("invoiceTitle");
        assertThat(result.getExtra().get(1).getField()).isEqualTo("extra.invoice.taxNo");
        assertThat(result.getExtra().get(1).getLabel()).isEqualTo("发票信息 - taxNo");
    }

    @Test
    void getAvailableFields_returnsEmptyExtraWhenNoOrderExtraTemplates() {
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of());

        AvailableFieldsDto result = receiptTemplateService.getAvailableFields();

        assertThat(result.getExtra()).isEmpty();
        assertThat(result.getFixed()).hasSize(9);
    }
}
