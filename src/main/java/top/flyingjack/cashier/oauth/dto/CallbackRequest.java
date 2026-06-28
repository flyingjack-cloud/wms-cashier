package top.flyingjack.cashier.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CallbackRequest(
        String code,
        @JsonProperty("code_verifier") String codeVerifier
) {}
