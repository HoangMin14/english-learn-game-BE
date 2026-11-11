package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.config.CognitoOAuthSettings;
import com.aws.alphabetuniverse.auth.dto.CognitoOAuthTokenResponse;
import com.aws.alphabetuniverse.auth.dto.GoogleAuthRequest;
import com.aws.alphabetuniverse.auth.service.CognitoOAuthService;
import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class CognitoOAuthServiceImpl implements CognitoOAuthService {
    private final RestClient client;
    private final CognitoOAuthSettings settings;

    public CognitoOAuthServiceImpl(
            @Qualifier("cognitoOAuthRestClient") RestClient client,
            CognitoOAuthSettings settings
    ) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public CognitoOAuthTokenResponse exchangeAuthorizationCode(GoogleAuthRequest request) {
        if (request == null) {
            throw new CognitoOAuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Google sign-in request is required");
        }
        request.validate();
        settings.requireAllowedRedirectUri(request.redirectUri());
        MultiValueMap<String, String> form = baseForm("authorization_code");
        form.add("code", request.code());
        form.add("redirect_uri", request.redirectUri());
        form.add("code_verifier", request.codeVerifier());
        return requestTokens(form, false);
    }

    @Override
    public CognitoOAuthTokenResponse refreshTokens(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CognitoOAuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Refresh token is required");
        }
        MultiValueMap<String, String> form = baseForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return requestTokens(form, true);
    }

    private MultiValueMap<String, String> baseForm(String grant) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grant);
        form.add("client_id", settings.clientId());
        return form;
    }

    private CognitoOAuthTokenResponse requestTokens(MultiValueMap<String, String> form, boolean refresh) {
        try {
            // Authorization codes are single-use. Do not retry or follow redirects.
            return client.post().uri(settings.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 429) {
                            throw new CognitoOAuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                                    "Too many authentication requests. Please try again later");
                        }
                        if (status >= 500) {
                            throw unavailable();
                        }
                        if (status != 200) {
                            throw tokenError(status, readError(response), refresh);
                        }
                        CognitoOAuthTokenResponse tokens = response.bodyTo(CognitoOAuthTokenResponse.class);
                        if (tokens == null || blank(tokens.accessToken()) || blank(tokens.idToken())
                                || (!refresh && blank(tokens.refreshToken()))
                                || tokens.expiresIn() == null || tokens.expiresIn() <= 0
                                || !"Bearer".equalsIgnoreCase(tokens.tokenType())) {
                            throw CognitoOAuthException.invalidResponse();
                        }
                        return tokens;
                    });
        } catch (ResourceAccessException exception) {
            // Do not attach/log upstream bodies or requests that can contain tokens.
            throw unavailable();
        } catch (RestClientException exception) {
            throw CognitoOAuthException.invalidResponse();
        }
    }

    private String readError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<?, ?> body = response.bodyTo(Map.class);
            return body != null && body.get("error") instanceof String error ? error : "";
        } catch (RestClientException exception) {
            return "";
        }
    }

    private CognitoOAuthException tokenError(int status, String error, boolean refresh) {
        if ("invalid_grant".equals(error)) {
            return refresh
                    ? new CognitoOAuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
                    "Refresh token is invalid or expired")
                    : new CognitoOAuthException(HttpStatus.BAD_REQUEST, "OAUTH_CODE_INVALID",
                    "Phiên đăng nhập Google không còn hợp lệ. Vui lòng đăng nhập lại.");
        }
        if ("invalid_client".equals(error) || "unauthorized_client".equals(error)
                || "invalid_scope".equals(error) || "unsupported_grant_type".equals(error)) {
            return CognitoOAuthException.configurationError();
        }
        if ("temporarily_unavailable".equals(error) || "server_error".equals(error)) {
            return unavailable();
        }
        if (status == 403 || "access_denied".equals(error)) {
            return new CognitoOAuthException(HttpStatus.FORBIDDEN, "COGNITO_OAUTH_FORBIDDEN",
                    "Cognito blocked the authentication request");
        }
        if ("invalid_request".equals(error)) {
            return new CognitoOAuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Cognito rejected the authentication request");
        }
        return CognitoOAuthException.invalidResponse();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static CognitoOAuthException unavailable() {
        return new CognitoOAuthException(HttpStatus.SERVICE_UNAVAILABLE, "COGNITO_UNAVAILABLE",
                "Authentication service is temporarily unavailable");
    }
}
