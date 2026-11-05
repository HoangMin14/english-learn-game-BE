package com.aws.alphabetuniverse.admin.service.impl;

import com.aws.alphabetuniverse.admin.service.AdminUserService;
import com.aws.alphabetuniverse.admin.controller.AdminUserController;
import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.auth.service.CognitoUserAdminService;
import com.aws.alphabetuniverse.common.exception.CognitoUserStatusSyncException;
import com.aws.alphabetuniverse.common.exception.GlobalExceptionHandler;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserStatusServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CognitoUserAdminService cognito = mock(CognitoUserAdminService.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(users, cognito);
    private User learner;

    @BeforeEach
    void setUp() {
        learner = User.builder().id(7L).cognitoSub("sub-7").email("user@example.com")
                .role("learner").status("ACTIVE")
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(learner));
        when(users.saveAndFlush(learner)).thenReturn(learner);
    }

    @Test
    void blocksLocallyBeforeDisablingCognitoUnderTheSameRowLock() {
        var response = service.updateUserStatus(7L, " blocked ", 1L);

        var order = inOrder(users, cognito);
        order.verify(users).findByIdForUpdate(7L);
        order.verify(users).saveAndFlush(learner);
        order.verify(cognito).disableUser("sub-7");
        assertEquals("BLOCKED", response.status());
        assertEquals("learner", response.role());
        assertEquals("sub-7", learner.getCognitoSub());
    }

    @Test
    void enablesCognitoBeforeMakingTheLocalUserActive() {
        learner.setStatus("BLOCKED");
        doAnswer(call -> {
            assertEquals("BLOCKED", learner.getStatus());
            return null;
        }).when(cognito).enableUser("sub-7");

        var response = service.updateUserStatus(7L, "ACTIVE", 1L);

        var order = inOrder(cognito, users);
        order.verify(cognito).enableUser("sub-7");
        order.verify(users).saveAndFlush(learner);
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void retryingAnAlreadyBlockedUserStillDisablesCognitoWithoutChangingUpdatedAt() {
        learner.setStatus("BLOCKED");
        var previousTime = learner.getUpdatedAt();

        service.updateUserStatus(7L, "BLOCKED", 1L);

        verify(cognito).disableUser("sub-7");
        verify(users, never()).saveAndFlush(any());
        assertEquals(previousTime, learner.getUpdatedAt());
    }

    @Test
    void retryingAnAlreadyActiveUserStillEnablesCognito() {
        var previousTime = learner.getUpdatedAt();

        service.updateUserStatus(7L, "ACTIVE", 1L);

        verify(cognito).enableUser("sub-7");
        verify(users, never()).saveAndFlush(any());
        assertEquals(previousTime, learner.getUpdatedAt());
    }

    @Test
    void springCommitsTheLocalBlockEvenWhenCognitoSynchronizationFails() {
        doThrow(upstreamFailure()).when(cognito).disableUser("sub-7");
        var transactions = new RecordingTransactions();
        var transactionalService = transactionalProxy(transactions);

        var error = assertThrows(CognitoUserStatusSyncException.class,
                () -> transactionalService.updateUserStatus(7L, "BLOCKED", 1L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals("COGNITO_CLIENT_ERROR", error.getCode());
        assertTrue(error.getReason().contains("BLOCKED"));
        assertEquals("BLOCKED", learner.getStatus());
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        assertFalse(transactions.readOnly);
        verify(users).saveAndFlush(learner);
    }

    @Test
    void aFailedEnableDoesNotActivateAPreviouslyBlockedUser() {
        learner.setStatus("BLOCKED");
        var previousTime = learner.getUpdatedAt();
        doThrow(upstreamFailure()).when(cognito).enableUser("sub-7");

        assertThrows(CognitoUserStatusSyncException.class,
                () -> service.updateUserStatus(7L, "ACTIVE", 1L));

        assertEquals("BLOCKED", learner.getStatus());
        assertEquals(previousTime, learner.getUpdatedAt());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void httpErrorPreservesTheSyncCauseAndCommitsTheLocalBlock() throws Exception {
        doThrow(new CognitoUserStatusSyncException(HttpStatus.BAD_GATEWAY, "COGNITO_ACCESS_DENIED",
                "Kiểm tra quyền IAM của backend.", null)).when(cognito).disableUser("sub-7");
        var transactions = new RecordingTransactions();
        var adminAccess = mock(AdminAccessService.class);
        var jwt = Jwt.withTokenValue("test-admin-token").header("alg", "RS256").subject("admin-sub").build();
        when(adminAccess.requireActiveAdmin(jwt)).thenReturn(User.builder().id(1L).build());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);

        try {
            var controller = new AdminUserController(transactionalProxy(transactions), adminAccess);
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .setControllerAdvice(new GlobalExceptionHandler()).build();

            mvc.perform(patch("/api/admin/users/7/status").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"BLOCKED\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("COGNITO_ACCESS_DENIED"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("IAM")));
            assertEquals("BLOCKED", learner.getStatus());
            assertEquals(1, transactions.commits);
            assertEquals(0, transactions.rollbacks);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void databaseFailureStillRollsBackAndDoesNotCallCognito() {
        when(users.saveAndFlush(learner)).thenThrow(new DataAccessResourceFailureException("DB unavailable"));
        var transactions = new RecordingTransactions();

        assertThrows(DataAccessResourceFailureException.class,
                () -> transactionalProxy(transactions).updateUserStatus(7L, "BLOCKED", 1L));

        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        verifyNoInteractions(cognito);
    }

    @Test
    void doesNotReturnSuccessWhenSavingAfterCognitoEnableFails() {
        learner.setStatus("BLOCKED");
        when(users.saveAndFlush(learner)).thenThrow(new DataAccessResourceFailureException("DB unavailable"));
        var transactions = new RecordingTransactions();

        assertThrows(DataAccessResourceFailureException.class,
                () -> transactionalProxy(transactions).updateUserStatus(7L, "ACTIVE", 1L));

        verify(cognito).enableUser("sub-7");
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    @Test
    void adminCannotBlockTheirOwnAccount() {
        learner.setRole("admin");

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateUserStatus(7L, "BLOCKED", 7L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(cognito);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void adminCannotBlockAnotherAdmin() {
        learner.setRole("admin");

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateUserStatus(7L, "BLOCKED", 1L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(cognito);
    }

    @Test
    void missingTargetReturns404WithoutCallingCognito() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateUserStatus(7L, "BLOCKED", 1L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(cognito);
    }

    @Test
    void invalidInputIsRejectedBeforeAnyCloudOperation() {
        for (String status : new String[]{null, "", "INACTIVE"}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.updateUserStatus(7L, status, 1L)).getStatusCode());
        }
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.updateUserStatus(0L, "BLOCKED", 1L)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.updateUserStatus(7L, "BLOCKED", null)).getStatusCode());
        verifyNoInteractions(cognito);
        verify(users, never()).findByIdForUpdate(any());
    }

    private CognitoUserStatusSyncException upstreamFailure() {
        return new CognitoUserStatusSyncException(HttpStatus.SERVICE_UNAVAILABLE,
                "COGNITO_CLIENT_ERROR", "AWS timeout", null);
    }

    private AdminUserService transactionalProxy(RecordingTransactions manager) {
        var factory = new ProxyFactory(service);
        factory.setInterfaces(AdminUserService.class);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (AdminUserService) factory.getProxy();
    }

    // Exercise Spring's real commit/rollback rules without connecting to a database.
    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        boolean readOnly;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            readOnly = definition.isReadOnly();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
