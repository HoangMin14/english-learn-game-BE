package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.user.dto.CognitoUserProfile;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserSyncServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CognitoUserAttributeService cognito = mock(CognitoUserAttributeService.class);
    private final UserSyncServiceImpl service = new UserSyncServiceImpl(users, cognito);
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).cognitoSub("sub-7").email("user@example.com")
                .displayName("Old name").role("admin").status("ACTIVE")
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    }

    @Test
    void readsFreshCognitoProfileOnlyAfterLockingExistingUser() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "New name"));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.syncUser("sub-7", "access-token");

        var order = inOrder(users, cognito);
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        order.verify(cognito).getProfile("access-token");
        assertEquals("New name", result.getDisplayName());
        assertEquals("admin", result.getRole());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void rereadsProfileAfterFirstInsertAndLockInsteadOfUsingInitialName() {
        when(users.findByCognitoSubForUpdate("sub-7"))
                .thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token")).thenReturn(
                new CognitoUserProfile("user@example.com", "Initial name"),
                new CognitoUserProfile("user@example.com", "Current name"));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.syncUser("sub-7", "access-token");

        var order = inOrder(users, cognito);
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        order.verify(cognito).getProfile("access-token");
        order.verify(users).insertIfAbsent(eq("sub-7"), eq("user@example.com"), eq("Initial name"),
                eq("learner"), eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        order.verify(cognito).getProfile("access-token");
        assertEquals("Current name", result.getDisplayName());
    }

    @Test
    void blockedUserIsNotReactivatedOrSynced() {
        user.setStatus("BLOCKED");
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.syncUser("sub-7", "access-token"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("BLOCKED", user.getStatus());
        verifyNoInteractions(cognito);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmailProduces409BeforeChangingLocalUser() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("taken@example.com", "New name"));
        when(users.existsByEmailIgnoreCaseAndIdNot("taken@example.com", 7L)).thenReturn(true);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.syncUser("sub-7", "access-token"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Old name", user.getDisplayName());
        assertEquals("user@example.com", user.getEmail());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void blankCognitoNamePreservesNameAndTimestamp() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "  "));
        when(users.saveAndFlush(user)).thenReturn(user);
        LocalDateTime previousUpdate = user.getUpdatedAt();

        var result = service.syncUser("sub-7", "access-token");

        assertEquals("Old name", result.getDisplayName());
        assertEquals(previousUpdate, user.getUpdatedAt());
    }

    @Test
    void legacyNameLongerThanNewLimitDoesNotPreventSync() {
        String legacyName = "x".repeat(101);
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", legacyName));
        when(users.saveAndFlush(user)).thenReturn(user);

        assertEquals(legacyName, service.syncUser("sub-7", "access-token").getDisplayName());
    }
}
