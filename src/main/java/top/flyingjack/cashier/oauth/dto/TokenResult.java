package top.flyingjack.cashier.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResult(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn
) {}
