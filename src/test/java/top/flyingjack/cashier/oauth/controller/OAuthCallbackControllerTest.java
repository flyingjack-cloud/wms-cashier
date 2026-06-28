package top.flyingjack.cashier.oauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import top.flyingjack.cashier.config.ResourceServerConfig;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.flyingjack.cashier.config.CustomJwtAuthenticationConverter;
import top.flyingjack.cashier.oauth.dto.CallbackRequest;
import top.flyingjack.cashier.oauth.dto.OAuthSession;
import top.flyingjack.cashier.oauth.dto.UacTokenResponse;
import top.flyingjack.cashier.oauth.service.OAuthSessionService;
import top.flyingjack.cashier.oauth.service.UacTokenService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OAuthCallbackController.class)
@Import(ResourceServerConfig.class)
class OAuthCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OAuthSessionService sessionService;

    @MockBean
    private UacTokenService tokenService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // satisfies ResourceServerConfig → CustomJwtAuthenticationConverter dependency
    @MockBean
    private CustomJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void callback_exchangesCodeAndSetsSessionCookie() throws Exception {
        UacTokenResponse uacResp = new UacTokenResponse(
                "access-token-xyz", "Bearer", 7200L, "refresh-token-abc",
                "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI0MiJ9.sig"
        );
        when(tokenService.exchangeCode(anyString(), anyString())).thenReturn(uacResp);

        Jwt jwtMock = mock(Jwt.class);
        when(jwtMock.getSubject()).thenReturn("42");
        when(jwtDecoder.decode(anyString())).thenReturn(jwtMock);
        when(sessionService.createSession(anyString(), anyLong())).thenReturn("session-id-001");

        CallbackRequest req = new CallbackRequest("auth-code-123", "verifier-xyz");

        mockMvc.perform(post("/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("access-token-xyz"))
                .andExpect(jsonPath("$.data.expires_in").value(7200))
                .andExpect(cookie().exists("WMS_SESSION"))
                .andExpect(cookie().httpOnly("WMS_SESSION", true))
                .andExpect(cookie().value("WMS_SESSION", "session-id-001"));

        verify(sessionService).createSession("refresh-token-abc", 42L);
    }

    @Test
    void callback_returns401WhenUacFails() throws Exception {
        when(tokenService.exchangeCode(anyString(), anyString()))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_REQUEST));

        CallbackRequest req = new CallbackRequest("bad-code", "verifier");

        mockMvc.perform(post("/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesTokenAndReturnsNewAccessToken() throws Exception {
        OAuthSession existingSession = new OAuthSession("old-refresh", 42L);
        when(sessionService.getSession("session-id-001")).thenReturn(existingSession);

        UacTokenResponse uacResp = new UacTokenResponse(
                "new-access-token", "Bearer", 7200L, "new-refresh-token", null
        );
        when(tokenService.refreshToken("old-refresh")).thenReturn(uacResp);

        mockMvc.perform(post("/oauth/refresh")
                        .cookie(new Cookie("WMS_SESSION", "session-id-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("new-access-token"));

        verify(sessionService).rotateSession("session-id-001", "new-refresh-token");
    }

    @Test
    void refresh_returns401WhenNoCookie() throws Exception {
        mockMvc.perform(post("/oauth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_returns401WhenSessionNotFound() throws Exception {
        when(sessionService.getSession(anyString())).thenReturn(null);

        mockMvc.perform(post("/oauth/refresh")
                        .cookie(new Cookie("WMS_SESSION", "invalid")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_deletesSessionAndClearsCookie() throws Exception {
        mockMvc.perform(post("/oauth/logout")
                        .cookie(new Cookie("WMS_SESSION", "session-id-001")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("WMS_SESSION", 0));

        verify(sessionService).deleteSession("session-id-001");
    }

    @Test
    void logout_succeedsEvenWithoutCookie() throws Exception {
        mockMvc.perform(post("/oauth/logout"))
                .andExpect(status().isOk());

        verify(sessionService, never()).deleteSession(anyString());
    }
}
