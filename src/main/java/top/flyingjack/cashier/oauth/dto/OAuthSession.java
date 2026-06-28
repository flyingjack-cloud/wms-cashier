package top.flyingjack.cashier.oauth.dto;

public record OAuthSession(
        String refreshToken,
        Long userId
) {}
