package top.flyingjack.cashier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraDto;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.entity.OrderExtraTemplateDto;
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

        List<OrderExtraTemplateDto> result = orderExtraService.getTemplates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("invoice");
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
