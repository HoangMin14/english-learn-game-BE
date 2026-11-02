package com.aws.alphabetuniverse.security.filter;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    private static final Set<String> SKIPPED_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/confirm-register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/logout",
            "/api/auth/sync",
            "/api/auth/refresh",
            "/api/auth/resend-confirmation"
    );

    public ActiveUserFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Không kiểm tra các API Auth công khai và Auth Sync.
    // Auth Sync tự kiểm tra trạng thái trong UserSyncService.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return !path.startsWith("/api/")
                || SKIPPED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Lấy Authentication sau khi JWT đã được xác thực.
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        // Không xử lý nếu không phải JWT Authentication.
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {

            filterChain.doFilter(request, response);
            return;
        }

        // 2. Lấy Cognito Sub.
        String cognitoSub = jwtAuth.getToken().getSubject();

        if (cognitoSub == null || cognitoSub.isBlank()) {

            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid authentication"
            );

            return;
        }

        // 3. Tìm User local.
        User user = userRepository
                .findByCognitoSub(cognitoSub)
                .orElse(null);

        if (user == null) {

            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "User has not been synchronized"
            );

            return;
        }

        // 4. Kiểm tra trạng thái.
        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {

            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Your account is blocked or inactive"
            );

            return;
        }

        // 5. User ACTIVE được tiếp tục gọi API.
        filterChain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        response.getWriter().write(
                "{\"message\":\"" + message + "\"}"
        );
    }
}