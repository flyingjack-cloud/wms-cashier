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
import top.flyingjack.cashier.entity.OrderExtraTemplateReq;
import top.flyingjack.cashier.feign.AuthServiceClient;
import top.flyingjack.cashier.service.OrderExtraService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderExtraTemplateSecurityTest extends BaseContainerTest {

    @MockBean AuthServiceClient authServiceClient;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean WmsSecurityContext securityContext;

    @Autowired OrderExtraService orderExtraService;

    private OrderExtraTemplateReq req() {
        OrderExtraTemplateReq r = new OrderExtraTemplateReq();
        r.setCode("sec-test");
        r.setName("测试");
        try {
            r.setSchema(new ObjectMapper().readTree("{\"fields\":[{\"key\":\"a\",\"type\":\"text\"}]}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_allowedForNonOwnerWhenIncludeDisabledFalse() {
        when(securityContext.currentGroupId()).thenReturn(950);

        // 这条断言同时验证 SpEL 参数名解析生效：若 #includeDisabled 无法解析，
        // 表达式不会短路放行，此调用会被误拒
        assertThatCode(() -> orderExtraService.getTemplates(false)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void getTemplates_deniedForNonOwnerWhenIncludeDisabledTrue() {
        assertThatThrownBy(() -> orderExtraService.getTemplates(true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void getTemplates_allowedForOwnerWhenIncludeDisabledTrue() {
        when(securityContext.currentGroupId()).thenReturn(951);

        assertThatCode(() -> orderExtraService.getTemplates(true)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void createTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.createTemplate(req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void updateTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.updateTemplate("sec-test", req()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void disableTemplate_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.disableTemplate("sec-test"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEFAULT")
    void setTemplateEnabled_deniedForNonOwner() {
        assertThatThrownBy(() -> orderExtraService.setTemplateEnabled("sec-test", true))
                .isInstanceOf(AccessDeniedException.class);
    }
}
