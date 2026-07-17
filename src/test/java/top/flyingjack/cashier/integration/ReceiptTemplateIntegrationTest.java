package top.flyingjack.cashier.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.ReceiptTemplate;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.mapper.ReceiptTemplateMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReceiptTemplateIntegrationTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;

    @Autowired ReceiptTemplateMapper receiptTemplateMapper;

    private static final String LAYOUT =
            "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}";

    private ReceiptTemplate template(int groupId, String printerType) {
        ReceiptTemplate t = new ReceiptTemplate();
        t.setGroupId(groupId);
        t.setPrinterType(printerType);
        t.setLayout(LAYOUT);
        t.setEnabled(true);
        return t;
    }

    @Test
    void insertTemplate_roundTripsJsonbAndAssignsId() {
        ReceiptTemplate t = template(910, "A4");
        receiptTemplateMapper.insertTemplate(t);

        assertThat(t.getId()).isGreaterThan(0);

        ReceiptTemplate found = receiptTemplateMapper.findTemplateByPrinterType(910, "A4");
        assertThat(found).isNotNull();
        assertThat(found.getPrinterType()).isEqualTo("A4");
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getLayout()).contains("order.imei");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void insertTemplate_rejectsDuplicatePrinterTypeInSameGroup() {
        receiptTemplateMapper.insertTemplate(template(911, "A4"));

        assertThatThrownBy(() -> receiptTemplateMapper.insertTemplate(template(911, "A4")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertTemplate_allowsDifferentPrinterTypesInSameGroup() {
        receiptTemplateMapper.insertTemplate(template(912, "A4"));
        receiptTemplateMapper.insertTemplate(template(912, "THERMAL_58"));

        assertThat(receiptTemplateMapper.findTemplates(912, false)).hasSize(2);
    }

    @Test
    void insertTemplate_allowsSamePrinterTypeInDifferentGroups() {
        receiptTemplateMapper.insertTemplate(template(913, "A4"));
        receiptTemplateMapper.insertTemplate(template(914, "A4"));

        assertThat(receiptTemplateMapper.findTemplateByPrinterType(913, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplateByPrinterType(914, "A4")).isNotNull();
    }

    @Test
    void updateTemplate_persistsLayout() {
        ReceiptTemplate t = template(915, "A4");
        receiptTemplateMapper.insertTemplate(t);

        t.setLayout("{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"divider\"}]}]}");
        receiptTemplateMapper.updateTemplate(t);

        ReceiptTemplate found = receiptTemplateMapper.findTemplateByPrinterType(915, "A4");
        assertThat(found.getLayout()).contains("divider").doesNotContain("order.imei");
    }

    @Test
    void updateEnabled_hidesTemplateFromEnabledQueries() {
        receiptTemplateMapper.insertTemplate(template(916, "A4"));

        receiptTemplateMapper.updateEnabled(916, "A4", false);

        assertThat(receiptTemplateMapper.findEnabledTemplateByPrinterType(916, "A4")).isNull();
        assertThat(receiptTemplateMapper.findTemplateByPrinterType(916, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplates(916, false)).isEmpty();
        assertThat(receiptTemplateMapper.findTemplates(916, true)).hasSize(1);
    }

    @Test
    void updateEnabled_reenablesTemplate() {
        receiptTemplateMapper.insertTemplate(template(917, "A4"));
        receiptTemplateMapper.updateEnabled(917, "A4", false);

        receiptTemplateMapper.updateEnabled(917, "A4", true);

        assertThat(receiptTemplateMapper.findEnabledTemplateByPrinterType(917, "A4")).isNotNull();
        assertThat(receiptTemplateMapper.findTemplates(917, false)).hasSize(1);
    }
}
