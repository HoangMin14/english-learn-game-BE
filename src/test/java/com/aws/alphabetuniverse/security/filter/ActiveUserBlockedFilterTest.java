package com.aws.alphabetuniverse.security.filter;

import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveUserBlockedFilterTest {

    @Test
    void anExistingTokenCannotReachBusinessApisAfterTheLocalUserIsBlocked() throws Exception {
        var users = mock(UserRepository.class);
        when(users.findByCognitoSub("sub-7"))
                .thenReturn(Optional.of(User.builder().id(7L).status("BLOCKED").build()));
        var jwt = Jwt.withTokenValue("previous-access-token").header("alg", "RS256").subject("sub-7").build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);

        try {
            var request = new MockHttpServletRequest("GET", "/api/users/me");
            request.setServletPath("/api/users/me");
            var response = new MockHttpServletResponse();
            var chain = mock(FilterChain.class);

            new ActiveUserFilter(users).doFilter(request, response, chain);

            assertEquals(403, response.getStatus());
            var body = JsonMapper.builder().build().readTree(response.getContentAsString());
            assertEquals(AccountBlockedException.CODE, body.get("code").asText());
            assertEquals(AccountBlockedException.MESSAGE, body.get("message").asText());
            verifyNoInteractions(chain);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
