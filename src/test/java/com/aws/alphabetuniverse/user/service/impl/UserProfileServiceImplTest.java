package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CognitoUserAttributeService cognito = mock(CognitoUserAttributeService.class);
    private final UserProfileServiceImpl service = new UserProfileServiceImpl(users, cognito);
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).cognitoSub("sub-7").email("user@example.com")
                .displayName("Old name").role("learner").status("ACTIVE")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 0, 0)).build();
    }

    @Test
    void locksUserThenUpdatesCognitoBeforeSavingNormalizedName() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);

        var response = service.updateMyProfile("sub-7", "access-token", "  Cafe\u0301  ");

        var order = inOrder(users, cognito);
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        order.verify(cognito).updateDisplayName("access-token", "Café");
        order.verify(users).saveAndFlush(user);
        assertEquals("Café", response.getDisplayName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("learner", response.getRole());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), user.getCreatedAt());
        assertEquals("sub-7", user.getCognitoSub());
    }

    @Test
    void reconcilesCognitoEvenWhenLocalNameAlreadyMatches() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        LocalDateTime previousUpdate = user.getUpdatedAt();

        service.updateMyProfile("sub-7", "access-token", "Old name");

        verify(cognito).updateDisplayName("access-token", "Old name");
        verify(users, never()).saveAndFlush(any());
        assertEquals(previousUpdate, user.getUpdatedAt());
    }

    @Test
    void rejectsBlockedUserBeforeCallingCognito() {
        user.setStatus("BLOCKED");
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateMyProfile("sub-7", "access-token", "New name"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(cognito);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnsynchronizedUser() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateMyProfile("sub-7", "access-token", "New name"));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(cognito);
    }

    @Test
    void doesNotChangeLocalProfileWhenCognitoFails() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(cognito).updateDisplayName("access-token", "New name");

        assertThrows(ResponseStatusException.class,
                () -> service.updateMyProfile("sub-7", "access-token", "New name"));

        assertEquals("Old name", user.getDisplayName());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void propagatesDatabaseFailureAfterCognitoSuccess() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.updateMyProfile("sub-7", "access-token", "New name"));

        verify(cognito).updateDisplayName("access-token", "New name");
    }

    @Test
    void validatesNameBeforeCallingDatabaseOrCognito() {
        for (String name : new String[]{null, " \t\n", "x".repeat(101)}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.updateMyProfile("sub-7", "access-token", name));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verifyNoInteractions(users, cognito);
    }

    @Test
    void countsUnicodeCodePointsAfterNormalization() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
        String name = "\uD83D\uDE00".repeat(100);

        assertEquals(name, service.updateMyProfile("sub-7", "access-token", name).getDisplayName());
    }
}
