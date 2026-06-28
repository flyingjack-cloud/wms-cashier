package top.flyingjack.cashier.oauth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import top.flyingjack.cashier.oauth.dto.UacTokenResponse;

@Service
public class UacTokenService {

    private final RestClient restClient;
    private final String clientId;
    private final String redirectUri;

    public UacTokenService(
            RestClient.Builder builder,
            @Value("${wms.oauth.auth-service-uri:http://localhost:9001}") String issuerUri,
            @Value("${wms.oauth.client-id}") String clientId,
            @Value("${wms.oauth.redirect-uri}") String redirectUri
    ) {
        this.restClient = builder.baseUrl(issuerUri).build();
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    public UacTokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("redirect_uri", redirectUri);
        return post(form);
    }

    public UacTokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);
        return post(form);
    }

    private UacTokenResponse post(MultiValueMap<String, String> form) {
        return restClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(UacTokenResponse.class);
    }
}
