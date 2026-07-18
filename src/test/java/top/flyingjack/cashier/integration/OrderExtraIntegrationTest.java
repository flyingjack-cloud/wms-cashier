package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.OrderExtra;
import top.flyingjack.cashier.entity.OrderExtraTemplate;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.OrderExtraMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderExtraIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired OrderExtraMapper orderExtraMapper;

    private static final String SCHEMA =
            "{\"fields\":[{\"key\":\"invoiceTitle\",\"type\":\"text\",\"required\":true}]}";

    private OrderExtraTemplate template(int groupId, String code) {
        OrderExtraTemplate t = new OrderExtraTemplate();
        t.setGroupId(groupId);
        t.setCode(code);
        t.setName("发票信息");
        t.setVersion(1);
        t.setSchemaJson(SCHEMA);
        t.setEnabled(true);
        return t;
    }

    @Test
    void insertTemplate_roundTripsJsonbAndAssignsId() {
        OrderExtraTemplate t = template(900, "invoice");
        orderExtraMapper.insertTemplate(t);

        assertThat(t.getId()).isGreaterThan(0);

        OrderExtraTemplate found = orderExtraMapper.findTemplateByCode(900, "invoice");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("发票信息");
        assertThat(found.getVersion()).isEqualTo(1);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getSchemaJson()).contains("invoiceTitle");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void insertTemplate_rejectsDuplicateCodeInSameGroup() {
        orderExtraMapper.insertTemplate(template(901, "invoice"));

        assertThatThrownBy(() -> orderExtraMapper.insertTemplate(template(901, "invoice")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertTemplate_allowsSameCodeInDifferentGroups() {
        orderExtraMapper.insertTemplate(template(902, "invoice"));
        orderExtraMapper.insertTemplate(template(903, "invoice"));

        assertThat(orderExtraMapper.findTemplateByCode(902, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplateByCode(903, "invoice")).isNotNull();
    }

    @Test
    void updateTemplate_persistsNameSchemaAndVersion() {
        OrderExtraTemplate t = template(904, "invoice");
        orderExtraMapper.insertTemplate(t);

        t.setName("增值税发票");
        t.setSchemaJson("{\"fields\":[{\"key\":\"taxNo\",\"type\":\"text\"}]}");
        t.setVersion(2);
        orderExtraMapper.updateTemplate(t);

        OrderExtraTemplate found = orderExtraMapper.findTemplateByCode(904, "invoice");
        assertThat(found.getName()).isEqualTo("增值税发票");
        assertThat(found.getVersion()).isEqualTo(2);
        assertThat(found.getSchemaJson()).contains("taxNo").doesNotContain("invoiceTitle");
    }

    @Test
    void updateEnabled_hidesTemplateFromEnabledQueries() {
        orderExtraMapper.insertTemplate(template(905, "invoice"));

        orderExtraMapper.updateEnabled(905, "invoice", false);

        assertThat(orderExtraMapper.findEnabledTemplateByCode(905, "invoice")).isNull();
        assertThat(orderExtraMapper.findTemplateByCode(905, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplates(905, false)).isEmpty();
        assertThat(orderExtraMapper.findTemplates(905, true)).hasSize(1);
    }

    @Test
    void updateEnabled_reenablesTemplate() {
        orderExtraMapper.insertTemplate(template(906, "invoice"));
        orderExtraMapper.updateEnabled(906, "invoice", false);

        orderExtraMapper.updateEnabled(906, "invoice", true);

        assertThat(orderExtraMapper.findEnabledTemplateByCode(906, "invoice")).isNotNull();
        assertThat(orderExtraMapper.findTemplates(906, false)).hasSize(1);
    }

    @Test
    void upsertExtra_insertsThenUpdatesOnConflict() {
        OrderExtra extra = new OrderExtra();
        extra.setGroupId(907);
        extra.setOrderId(1);
        extra.setTemplateId(null);
        extra.setTemplateCode("invoice");
        extra.setTemplateName("发票信息");
        extra.setTemplateVersion(1);
        extra.setPayload("{\"invoiceTitle\":\"甲公司\"}");
        orderExtraMapper.upsertExtra(extra);

        extra.setPayload("{\"invoiceTitle\":\"乙公司\"}");
        extra.setTemplateVersion(2);
        orderExtraMapper.upsertExtra(extra);

        List<OrderExtra> found = orderExtraMapper.findExtrasByOrderId(907, 1);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getPayload()).contains("乙公司");
        assertThat(found.get(0).getTemplateVersion()).isEqualTo(2);
    }
}
