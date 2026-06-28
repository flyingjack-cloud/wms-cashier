package top.flyingjack.cashier.oauth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import top.flyingjack.cashier.oauth.dto.CallbackRequest;
import top.flyingjack.cashier.oauth.dto.OAuthSession;
import top.flyingjack.cashier.oauth.dto.TokenResult;
import top.flyingjack.cashier.oauth.dto.UacTokenResponse;
import top.flyingjack.cashier.oauth.service.OAuthSessionService;
import top.flyingjack.cashier.oauth.service.UacTokenService;
import top.flyingjack.common.dto.ApiRes;
import top.flyingjack.common.error.ErrorCode;
import top.flyingjack.common.error.exception.BusinessException;

@RestController
@RequestMapping("/oauth")
public class OAuthCallbackController {

    private static final String COOKIE_NAME = "WMS_SESSION";
    private static final int SESSION_MAX_AGE = 7 * 24 * 3600;

    private final OAuthSessionService sessionService;
    private final UacTokenService tokenService;
    private final JwtDecoder jwtDecoder;
    private final boolean cookieSecure;

    public OAuthCallbackController(
            OAuthSessionService sessionService,
            UacTokenService tokenService,
            JwtDecoder jwtDecoder,
            @Value("${wms.oauth.session.cookie-secure:false}") boolean cookieSecure) {
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.jwtDecoder = jwtDecoder;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/callback")
    public ApiRes<TokenResult> callback(@RequestBody CallbackRequest request, HttpServletResponse response) {
        UacTokenResponse uacResp;
        try {
            uacResp = tokenService.exchangeCode(request.code(), request.codeVerifier());
        } catch (HttpClientErrorException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Jwt idToken = jwtDecoder.decode(uacResp.idToken());
        long userId = Long.parseLong(idToken.getSubject());
        String sessionId = sessionService.createSession(uacResp.refreshToken(), userId);

        response.addCookie(buildCookie(sessionId, SESSION_MAX_AGE));
        return ApiRes.success(new TokenResult(uacResp.accessToken(), uacResp.expiresIn()));
    }

    @PostMapping("/refresh")
    public ApiRes<TokenResult> refresh(
            @CookieValue(name = COOKIE_NAME, required = false) String sessionId) {
        if (sessionId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        OAuthSession session = sessionService.getSession(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        UacTokenResponse uacResp = tokenService.refreshToken(session.refreshToken());
        sessionService.rotateSession(sessionId, uacResp.refreshToken());

        return ApiRes.success(new TokenResult(uacResp.accessToken(), uacResp.expiresIn()));
    }

    @PostMapping("/logout")
    public ApiRes<Void> logout(
            @CookieValue(name = COOKIE_NAME, required = false) String sessionId,
            HttpServletResponse response) {
        if (sessionId != null) {
            sessionService.deleteSession(sessionId);
            response.addCookie(buildCookie("", 0));
        }
        return ApiRes.success();
    }

    private Cookie buildCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
