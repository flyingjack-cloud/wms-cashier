package top.flyingjack.cashier.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import top.flyingjack.cashier.BaseContainerTest;
import top.flyingjack.cashier.entity.ReceiptTemplateReq;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.service.ReceiptTemplateService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReceiptTemplateSecurityTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean WmsSecurityContext securityContext;

    @Autowired ReceiptTemplateService receiptTemplateService;

    private ReceiptTemplateReq req() {
        ReceiptTemplateReq r = new ReceiptTemplateReq();
        r.setPrinterType("A4");
        try {
            r.setLayout(new ObjectMapper().readTree(
                    "{\"rows\":[{\"columns\":[{\"span\":1,\"type\":\"text\",\"field\":\"order.imei\"}]}]}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_allowedForNonOwnerWhenIncludeDisabledFalse() {
        when(securityContext.currentGroupId()).thenReturn(970);

        assertThatCode(() -> receiptTemplateService.getTemplates(false)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_deniedForNonOwnerWhenIncludeDisabledTrue() {
        assertThatThrownBy(() -> receiptTemplateService.getTemplates(true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void getTemplates_allowedForOwnerWhenIncludeDisabledTrue() {
        when(securityContext.currentGroupId()).thenReturn(971);

        assertThatCode(() -> receiptTemplateService.getTemplates(true)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void createTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.createTemplate(req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void updateTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.updateTemplate("A4", req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void disableTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.disableTemplate("A4"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void setTemplateEnabled_deniedForNonOwner() {
        assertThatThrownBy(() -> receiptTemplateService.setTemplateEnabled("A4", true))
                .isInstanceOf(AccessDeniedException.class);
    }
}
