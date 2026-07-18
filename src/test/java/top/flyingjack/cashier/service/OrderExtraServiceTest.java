package top.flyingjack.cashier.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.OrderExtraTemplateDto;
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
import top.flyingjack.cashier.mapper.OrderExtraMapper;
import top.flyingjack.cashier.mapper.OrderMapper;
import top.flyingjack.cashier.security.WmsSecurityContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderExtraServiceTest {
    OrderExtraMapper orderExtraMapper;
    OrderMapper orderMapper;
    WmsSecurityContext securityContext;
    OrderExtraService orderExtraService;

    @BeforeEach
    void setUp() {
        orderExtraMapper = mock(OrderExtraMapper.class);
        orderMapper = mock(OrderMapper.class);
        securityContext = mock(WmsSecurityContext.class);
        orderExtraService = new OrderExtraService(orderExtraMapper, orderMapper, securityContext,
                new ObjectMapper(), new OrderExtraSchemaValidator());
        when(securityContext.currentGroupId()).thenReturn(1);
    }

    @Test
    void getTemplates_returnsSchemaAsJson() {
        when(orderExtraMapper.findTemplates(1, false)).thenReturn(List.of(template()));

        List<OrderExtraTemplateDto> result = orderExtraService.getTemplates(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("invoice");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(0).getSchema().path("fields").get(0).path("key").asText())
                .isEqualTo("invoiceTitle");
    }

    @Test
    void saveExtra_validatesAndUpsertsPayloadSnapshot() {
        when(orderMapper.findMeIdById(10, 1)).thenReturn(99);
        when(orderExtraMapper.findEnabledTemplateByCode(1, "invoice")).thenReturn(template());

        orderExtraService.saveExtra(10, "invoice", Map.of(
                "invoiceTitle", "上海某某公司",
                "taxNo", "9131"
        ));

        ArgumentCaptor<OrderExtra> captor = ArgumentCaptor.forClass(OrderExtra.class);
        verify(orderExtraMapper).upsertExtra(captor.capture());
        OrderExtra extra = captor.getValue();
        assertThat(extra.getGroupId()).isEqualTo(1);
        assertThat(extra.getOrderId()).isEqualTo(10);
        assertThat(extra.getTemplateCode()).isEqualTo("invoice");
        assertThat(extra.getTemplateName()).isEqualTo("发票信息");
        assertThat(extra.getTemplateVersion()).isEqualTo(1);
        assertThat(extra.getPayload()).contains("\"invoiceTitle\":\"上海某某公司\"");
    }

    @Test
    void saveExtra_rejectsMissingRequiredField() {
        when(orderMapper.findMeIdById(10, 1)).thenReturn(99);
        when(orderExtraMapper.findEnabledTemplateByCode(1, "invoice")).thenReturn(template());

        assertThatThrownBy(() -> orderExtraService.saveExtra(10, "invoice", Map.of("taxNo", "9131")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field: invoiceTitle");

        verify(orderExtraMapper, never()).upsertExtra(any());
    }

    @Test
    void getExtra_returnsPayloadAsJson() {
        OrderExtra extra = new OrderExtra();
        extra.setOrderId(10);
        extra.setTemplateCode("invoice");
        extra.setTemplateName("发票信息");
        extra.setTemplateVersion(1);
        extra.setPayload("{\"invoiceTitle\":\"上海某某公司\"}");
        when(orderMapper.findMeIdById(10, 1)).thenReturn(99);
        when(orderExtraMapper.findExtraByOrderIdAndCode(1, 10, "invoice")).thenReturn(extra);

        OrderExtraDto result = orderExtraService.getExtra(10, "invoice");

        assertThat(result.getTemplateCode()).isEqualTo("invoice");
        assertThat(result.getPayload().path("invoiceTitle").asText()).isEqualTo("上海某某公司");
    }

    private JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private OrderExtraTemplateReq req(String code, String name, String schema) {
        OrderExtraTemplateReq r = new OrderExtraTemplateReq();
        r.setCode(code);
        r.setName(name);
        r.setSchema(json(schema));
        return r;
    }

    @Test
    void createTemplate_insertsWithVersionOne() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(null);

        orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}"));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).insertTemplate(captor.capture());
        OrderExtraTemplate saved = captor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(1);
        assertThat(saved.getCode()).isEqualTo("invoice");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getSchemaJson()).contains("\"key\":\"a\"");
    }

    @Test
    void createTemplate_rejectsDuplicateCode() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        assertThatThrownBy(() -> orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template already exists: invoice");

        verify(orderExtraMapper, never()).insertTemplate(any());
    }

    @Test
    void createTemplate_rejectsRaceDuplicateInsert() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate key"))
                .when(orderExtraMapper).insertTemplate(any());

        assertThatThrownBy(() -> orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template already exists: invoice");
    }

    @Test
    void createTemplate_rejectsInvalidSchema() {
        assertThatThrownBy(() -> orderExtraService.createTemplate(
                req("invoice", "发票信息", "{\"fields\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template fields cannot be empty");

        verify(orderExtraMapper, never()).insertTemplate(any());
    }

    @Test
    void updateTemplate_bumpsVersionWhenSchemaChanges() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        OrderExtraTemplateDto result = orderExtraService.updateTemplate("invoice",
                req(null, "发票信息", "{\"fields\":[{\"key\":\"newKey\",\"type\":\"text\"}]}"));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void updateTemplate_keepsVersionWhenOnlyNameChanges() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        // schema 与 template() 的 schemaJson 语义相同，仅键顺序与空白不同
        String sameSchema = "{\"fields\":[ "
                + "{\"type\":\"text\",\"key\":\"invoiceTitle\",\"required\":true,\"label\":\"发票抬头\"},"
                + "{\"required\":false,\"key\":\"taxNo\",\"type\":\"text\",\"label\":\"税号\"} ]}";

        OrderExtraTemplateDto result = orderExtraService.updateTemplate("invoice",
                req(null, "增值税发票", sameSchema));

        ArgumentCaptor<OrderExtraTemplate> captor = ArgumentCaptor.forClass(OrderExtraTemplate.class);
        verify(orderExtraMapper).updateTemplate(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getName()).isEqualTo("增值税发票");
        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    void updateTemplate_rejectsUnknownCode() {
        when(orderExtraMapper.findTemplateByCode(1, "ghost")).thenReturn(null);

        assertThatThrownBy(() -> orderExtraService.updateTemplate("ghost",
                req(null, "x", "{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template not found: ghost");

        verify(orderExtraMapper, never()).updateTemplate(any());
    }

    @Test
    void disableTemplate_setsEnabledFalse() {
        when(orderExtraMapper.findTemplateByCode(1, "invoice")).thenReturn(template());

        orderExtraService.disableTemplate("invoice");

        verify(orderExtraMapper).updateEnabled(1, "invoice", false);
    }

    @Test
    void setTemplateEnabled_rejectsUnknownCode() {
        when(orderExtraMapper.findTemplateByCode(1, "ghost")).thenReturn(null);

        assertThatThrownBy(() -> orderExtraService.setTemplateEnabled("ghost", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template not found: ghost");

        verify(orderExtraMapper, never()).updateEnabled(anyInt(), anyString(), anyBoolean());
    }

    private OrderExtraTemplate template() {
        OrderExtraTemplate template = new OrderExtraTemplate();
        template.setId(5);
        template.setGroupId(1);
        template.setCode("invoice");
        template.setName("发票信息");
        template.setVersion(1);
        template.setEnabled(true);
        template.setSchemaJson("{\"fields\":["
                + "{\"key\":\"invoiceTitle\",\"label\":\"发票抬头\",\"type\":\"text\",\"required\":true},"
                + "{\"key\":\"taxNo\",\"label\":\"税号\",\"type\":\"text\",\"required\":false}"
                + "]}");
        return template;
    }
}
