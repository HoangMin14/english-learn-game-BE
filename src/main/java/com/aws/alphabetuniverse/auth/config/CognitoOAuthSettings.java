package com.aws.alphabetuniverse.auth.config;

import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class CognitoOAuthSettings {
    private final URI tokenEndpoint;
    private final String clientId;
    private final Set<String> allowedRedirectUris;
    private final boolean oauthRefresh;

    public CognitoOAuthSettings(
            @Value("${aws.cognito.domain}") String domain,
            @Value("${aws.cognito.client-id}") String clientId,
            @Value("${aws.cognito.oauth.allowed-redirect-uris:${AWS_COGNITO_ALLOWED_REDIRECT_URIS:}}")
            String redirectUris,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${aws.cognito.oauth.refresh-mode:${AWS_COGNITO_REFRESH_MODE:sdk}}") String refreshMode
    ) {
        URI base;
        try {
            base = URI.create(domain.strip().replaceAll("/+$", ""));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cognito OAuth domain must be an HTTPS origin");
        }
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || !base.getRawPath().isEmpty()
                || (base.getPort() != -1 && base.getPort() != 443)) {
            throw new IllegalStateException("Cognito OAuth domain must be an HTTPS origin");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Cognito App Client ID is required");
        }
        this.tokenEndpoint = base.resolve("/oauth2/token");
        this.clientId = clientId;
        String configuredUris = redirectUris == null || redirectUris.isBlank()
                ? frontendUrl.strip().replaceAll("/+$", "") + "/callback" : redirectUris;
        this.allowedRedirectUris = Arrays.stream(configuredUris.split(","))
                .map(String::strip).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        if (allowedRedirectUris.isEmpty() || allowedRedirectUris.stream().anyMatch(uri -> !validCallback(uri))) {
            throw new IllegalStateException("Configure exact HTTPS callback URLs or HTTP localhost callback URLs");
        }
        String mode = refreshMode == null ? "" : refreshMode.strip().toLowerCase(Locale.ROOT);
        if (!Set.of("sdk", "oauth").contains(mode)) {
            throw new IllegalStateException("Cognito refresh mode must be sdk or oauth");
        }
        this.oauthRefresh = "oauth".equals(mode);
    }

    public URI tokenEndpoint() {
        return tokenEndpoint;
    }

    public String clientId() {
        return clientId;
    }

    public boolean useOAuthRefresh() {
        return oauthRefresh;
    }

    public void requireAllowedRedirectUri(String redirectUri) {
        if (!allowedRedirectUris.contains(redirectUri)) {
            throw new CognitoOAuthException(HttpStatus.BAD_REQUEST, "OAUTH_REDIRECT_URI_INVALID",
                    "Redirect URI is not allowed");
        }
    }

    private static boolean validCallback(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && ("https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost())));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
