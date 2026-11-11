package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import com.aws.alphabetuniverse.common.exception.EmailAlreadyExistsException;
import com.aws.alphabetuniverse.user.dto.CognitoUserProfile;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
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
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
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
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), user.getCreatedAt());
        verify(cognito, times(1)).getProfile("access-token");
        verify(users, never()).findByCognitoSub(anyString());
    }

    @Test
    void concurrentInsertWinnerForcesFreshProfileAfterLockInsteadOfUsingInitialName() {
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
        verify(cognito, times(2)).getProfile("access-token");
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

        var error = assertThrows(EmailAlreadyExistsException.class,
                () -> service.syncUser("sub-7", "access-token"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(EmailAlreadyExistsException.MESSAGE, error.getReason());
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

    @Test
    void firstUserWithoutProviderNameGetsEmailFallbackButExistingNameIsPreserved() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", null));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.syncUser("sub-7", "access-token");

        verify(users).insertIfAbsent(eq("sub-7"), eq("user@example.com"), eq("user"), eq("learner"),
                eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
        assertEquals("Old name", result.getDisplayName());
        assertEquals("admin", result.getRole());
    }

    @Test
    void fallbackNameIsBoundedByCodePointsForFirstInsert() {
        String email = "x".repeat(110) + "@example.com";
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token")).thenReturn(new CognitoUserProfile(email, " "));
        when(users.saveAndFlush(user)).thenReturn(user);

        service.syncUser("sub-7", "access-token");

        verify(users).insertIfAbsent(eq("sub-7"), eq(email), eq("x".repeat(100)), eq("learner"),
                eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void successfulFirstInsertReusesInitialProfileAndReturnsLearnerWithoutSecondAwsRead() {
        user.setRole("learner");
        user.setDisplayName("New name");
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("  user@example.com  ", "  New name  "));
        insertReturns(1);
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.syncUser("sub-7", "access-token");

        assertEquals("learner", result.getRole());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("New name", result.getDisplayName());
        var order = inOrder(users, cognito);
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        order.verify(cognito).getProfile("access-token");
        order.verify(users).findByEmailIgnoreCase("user@example.com");
        order.verify(users).insertIfAbsent(eq("sub-7"), eq("user@example.com"), eq("New name"),
                eq("learner"), eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        verify(cognito, times(1)).getProfile("access-token");
        verify(users, never()).findByCognitoSub(anyString());
    }

    @Test
    void firstSyncRejectsEmailOfAnotherSubBeforeInsertAndDoesNotLinkAccounts() {
        User owner = User.builder().id(9L).cognitoSub("password-sub").email("user@example.com")
                .displayName("Password user").role("learner").status("BLOCKED").build();
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty());
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile(" user@example.com ", "Google user"));
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(owner));

        assertThrows(EmailAlreadyExistsException.class, () -> service.syncUser("sub-7", "access-token"));

        assertEquals("password-sub", owner.getCognitoSub());
        assertEquals("BLOCKED", owner.getStatus());
        verify(users, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
        verify(users, never()).saveAndFlush(any());
        verify(users, times(1)).findByCognitoSubForUpdate("sub-7");
    }

    @Test
    void sameSubFoundByEmailUsesFreshLockedEntityAfterInsertClearsPersistenceContext() {
        User previouslyReadOwner = User.builder().id(7L).cognitoSub("sub-7")
                .email("user@example.com").displayName("Stale name").status("ACTIVE").build();
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(previouslyReadOwner));
        when(cognito.getProfile("access-token")).thenReturn(
                new CognitoUserProfile("user@example.com", "Before lock"),
                new CognitoUserProfile("user@example.com", "After lock"));
        when(users.saveAndFlush(user)).thenReturn(user);

        var result = service.syncUser("sub-7", "access-token");

        assertEquals("After lock", result.getDisplayName());
        assertEquals("Stale name", previouslyReadOwner.getDisplayName());
        assertEquals("admin", result.getRole());
        verify(cognito, times(2)).getProfile("access-token");
        verify(users).saveAndFlush(user);
    }

    @Test
    void concurrentInsertThatIsBlockedBeforeLockIsRejectedWithoutAnotherProfileRead() {
        user.setStatus("BLOCKED");
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "Before block"));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.syncUser("sub-7", "access-token")).getStatusCode());

        verify(cognito, times(1)).getProfile("access-token");
        verify(users, never()).saveAndFlush(any());
        assertEquals("BLOCKED", user.getStatus());
    }

    @Test
    void missingUserAfterInsertIsAnInternalErrorAndDoesNotReadProfileAgain() {
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "Name"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, assertThrows(ResponseStatusException.class,
                () -> service.syncUser("sub-7", "access-token")).getStatusCode());
        verify(cognito, times(1)).getProfile("access-token");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void insertUniqueFailureEscapesForRollbackWithoutQueryingAnAbortedTransaction() {
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "Name"));
        var failure = new DataIntegrityViolationException("simulated-email-unique-race");
        when(users.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.syncUser("sub-7", "access-token")));
        verify(users, times(1)).findByCognitoSubForUpdate("sub-7");
        verify(users, never()).saveAndFlush(any());
        verify(cognito, times(1)).getProfile("access-token");
    }

    @Test
    void updateUniqueRaceEscapesForRollbackInsteadOfReturningSuccess() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("new@example.com", "New name"));
        var failure = new DataIntegrityViolationException("simulated-email-update-race");
        when(users.saveAndFlush(user)).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.syncUser("sub-7", "access-token")));
        verify(cognito, times(1)).getProfile("access-token");
        verify(users, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingEmailNeverReachesInsert(String email) {
        when(cognito.getProfile("access-token")).thenReturn(new CognitoUserProfile(email, null));

        var error = assertThrows(CognitoOAuthException.class,
                () -> service.syncUser("sub-7", "access-token"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("COGNITO_OAUTH_INVALID_RESPONSE", error.getCode());
        verify(users, never()).findByEmailIgnoreCase(any());
        verify(users, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void firstInsertWithoutNameKeepsEmailFallbackAndOnlyReadsProfileOnce() {
        user.setRole("learner");
        user.setDisplayName("user");
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token")).thenReturn(new CognitoUserProfile("user@example.com", null));
        insertReturns(1);
        when(users.saveAndFlush(user)).thenReturn(user);

        assertEquals("user", service.syncUser("sub-7", "access-token").getDisplayName());
        verify(users).insertIfAbsent(eq("sub-7"), eq("user@example.com"), eq("user"), eq("learner"),
                eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(cognito, times(1)).getProfile("access-token");
    }

    @Test
    void unusableEmailLocalPartFallsBackToLearnerInsteadOfInsertingNullName() {
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty(), Optional.of(user));
        when(cognito.getProfile("access-token")).thenReturn(new CognitoUserProfile("@example.com", "  "));
        when(users.saveAndFlush(user)).thenReturn(user);

        service.syncUser("sub-7", "access-token");

        verify(users).insertIfAbsent(eq("sub-7"), eq("@example.com"), eq("Learner"), eq("learner"),
                eq("ACTIVE"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"INACTIVE", "UNKNOWN"})
    void nonActiveStatusReturnsStableCodeWithoutReadingAws(String status) {
        user.setStatus(status);
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));

        var error = assertThrows(CognitoOAuthException.class,
                () -> service.syncUser("sub-7", "access-token"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("ACCOUNT_INACTIVE", error.getCode());
        verifyNoInteractions(cognito);
        verify(users, never()).saveAndFlush(any());
    }

    private void insertReturns(int count) {
        when(users.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(count);
    }

    @Test
    void uniqueFailureRollsBackReadCommittedTransactionBeforeEscapingServiceProxy() {
        when(cognito.getProfile("access-token"))
                .thenReturn(new CognitoUserProfile("user@example.com", "Name"));
        when(users.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique race"));
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var proxy = (UserSyncService) factory.getProxy();

        assertThrows(DataIntegrityViolationException.class, () -> proxy.syncUser("sub-7", "access-token"));

        verify(transactions).getTransaction(argThat((TransactionDefinition definition) ->
                definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED));
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
        verify(users, times(1)).findByCognitoSubForUpdate("sub-7");
    }
}
