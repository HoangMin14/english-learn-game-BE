package com.aws.alphabetuniverse.admin.service.impl;

import com.aws.alphabetuniverse.admin.dto.AdminUserPageResponse;
import com.aws.alphabetuniverse.admin.dto.AdminUserResponse;
import com.aws.alphabetuniverse.admin.dto.AdminUserStatusResponse;
import com.aws.alphabetuniverse.admin.service.AdminUserService;
import com.aws.alphabetuniverse.auth.service.CognitoUserAdminService;
import com.aws.alphabetuniverse.common.exception.CognitoUserStatusSyncException;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.data.jpa.domain.Specification;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final CognitoUserAdminService cognitoUserAdminService;

    private static final Set<String> VALID_ROLES =
            Set.of("admin", "learner");

    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "BLOCKED");


    // ==========================================
    // API: GET ADMIN USERS
    // ==========================================

    @Override
    public AdminUserPageResponse getUsers(
            int page,
            int size,
            String keyword,
            String role,
            String status
    ) {

        // 1. Validate pagination

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }

        // Tránh offset vượt giới hạn Integer

        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page is too large"
            );
        }


        // 2. Normalize filters

        keyword = normalizeKeyword(keyword);

        String normalizedRole = normalizeRole(role);

        String normalizedStatus = normalizeStatus(status);


        // 3. Build dynamic Specification

        final String searchKeyword = keyword;

        Specification<User> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    // Search email OR displayName

                    if (searchKeyword != null) {

                        String pattern =
                                "%"
                                        + escapeLike(
                                        searchKeyword.toLowerCase(Locale.ROOT)
                                )
                                        + "%";

                        Predicate emailPredicate =
                                cb.like(
                                        cb.lower(root.<String>get("email")),
                                        pattern,
                                        '\\'
                                );

                        Predicate displayNamePredicate =
                                cb.like(
                                        cb.lower(root.<String>get("displayName")),
                                        pattern,
                                        '\\'
                                );

                        predicates.add(
                                cb.or(
                                        emailPredicate,
                                        displayNamePredicate
                                )
                        );
                    }


                    // Filter role

                    if (normalizedRole != null) {

                        predicates.add(
                                cb.equal(
                                        cb.lower(root.<String>get("role")),
                                        normalizedRole
                                )
                        );
                    }


                    // Filter status

                    if (normalizedStatus != null) {

                        predicates.add(
                                cb.equal(
                                        cb.upper(root.<String>get("status")),
                                        normalizedStatus
                                )
                        );
                    }


                    // AND all filter groups

                    return cb.and(
                            predicates.toArray(new Predicate[0])
                    );
                };


        // 4. Create pageable

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );


        // 5. Query database

        Page<User> userPage =
                userRepository.findAll(
                        specification,
                        pageable
                );


        // 6. Convert Entity to DTO

        List<AdminUserResponse> content =
                userPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList();


        // 7. Return pagination response

        return new AdminUserPageResponse(
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    @Override
    @Transactional(noRollbackFor = CognitoUserStatusSyncException.class)
    public AdminUserStatusResponse updateUserStatus(
            Long userId,
            String status,
            Long adminUserId
    ) {

        // 1. Kiểm tra User ID.
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid userId"
            );
        }

        // 2. Kiểm tra Admin ID.
        // ID này được Controller lấy từ Admin đã xác thực.
        if (adminUserId == null || adminUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin authentication is required"
            );
        }

        // 3. Chuẩn hóa trạng thái yêu cầu.
        String normalizedStatus = normalizeRequestedStatus(status);

        // 4. Lấy và khóa bản ghi User.
        User targetUser = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        // 5. Không cho Admin tự khóa chính mình.
        // Kiểm tra trước role để trường hợp này trả 409.
        if (targetUser.getId().equals(adminUserId)
                && "BLOCKED".equals(normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Admin cannot block their own account"
            );
        }

        // 6. Chỉ cho phép quản lý Learner.
        String targetRole = targetUser.getRole() == null
                ? ""
                : targetUser.getRole()
                  .trim()
                  .toLowerCase(Locale.ROOT);

        if (targetRole.startsWith("role_")) {
            targetRole = targetRole.substring(5);
        }

        if (!"learner".equals(targetRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin can only manage learner accounts"
            );
        }

        if ("BLOCKED".equals(normalizedStatus)) {
            // Commit the local block even when AWS fails. The exception below is
            // deliberately excluded from rollback; database errors still roll back.
            updateLocalStatus(targetUser, normalizedStatus);
            try {
                cognitoUserAdminService.disableUser(targetUser.getCognitoSub());
            } catch (CognitoUserStatusSyncException ex) {
                throw new CognitoUserStatusSyncException(ex.getStatusCode(), ex.getCode(),
                        "Đã khóa tài khoản trên hệ thống, nhưng chưa xác nhận khóa trên Cognito. "
                                + ex.getReason()
                                + " Sau khi xử lý nguyên nhân, gửi lại yêu cầu BLOCKED để đồng bộ.", ex);
            }
        } else {
            // Enable Cognito first. On failure a previously blocked local account
            // stays blocked, and Login / protected APIs continue to reject it.
            cognitoUserAdminService.enableUser(targetUser.getCognitoSub());
            updateLocalStatus(targetUser, normalizedStatus);
        }

        // Repeat the AWS operation even when local status already matches, so an
        // Admin retry can reconcile a previous timeout or failed AWS request.
        return toStatusResponse(targetUser);
    }

    private void updateLocalStatus(User user, String status) {
        if (!status.equalsIgnoreCase(String.valueOf(user.getStatus()))) {
            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            userRepository.saveAndFlush(user);
        }
    }


    // ==========================================
    // HELPER METHODS
    // ==========================================

    private String normalizeKeyword(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }


    private String normalizeRole(String role) {

        if (role == null || role.isBlank()) {
            return null;
        }

        String normalized =
                role.trim().toLowerCase(Locale.ROOT);

        if (!VALID_ROLES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role. Allowed: admin, learner"
            );
        }

        return normalized;
    }


    private String normalizeStatus(String status) {

        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized =
                status.trim().toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status. Allowed: ACTIVE, BLOCKED"
            );
        }

        return normalized;
    }


    // Escape ký tự đặc biệt trong LIKE

    private String escapeLike(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }


    // Convert Entity to DTO

    private AdminUserResponse toResponse(User user) {

        return new AdminUserResponse(

                user.getId(),

                user.getEmail(),

                user.getDisplayName(),

                user.getRole() == null
                        ? null
                        : String.valueOf(user.getRole())
                          .toLowerCase(Locale.ROOT),

                user.getStatus() == null
                        ? null
                        : String.valueOf(user.getStatus())
                          .toUpperCase(Locale.ROOT),

                user.getCreatedAt()
        );
    }

    private String normalizeRequestedStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required"
            );
        }

        String normalized = status
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be ACTIVE or BLOCKED"
            );
        }

        return normalized;
    }

    private AdminUserStatusResponse toStatusResponse(User user) {

        return new AdminUserStatusResponse(

                user.getId(),

                user.getEmail(),

                user.getDisplayName(),

                user.getRole() == null
                        ? null
                        : user.getRole().toLowerCase(Locale.ROOT),

                user.getStatus() == null
                        ? null
                        : user.getStatus().toUpperCase(Locale.ROOT),

                user.getUpdatedAt()
        );
    }
}
