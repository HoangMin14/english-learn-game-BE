package com.aws.alphabetuniverse.admin.service;

import com.aws.alphabetuniverse.admin.dto.AdminUserPageResponse;
import com.aws.alphabetuniverse.admin.dto.AdminUserStatusResponse;

public interface AdminUserService {

    AdminUserPageResponse getUsers(
            int page,
            int size,
            String keyword,
            String role,
            String status
    );

    // API PATCH khóa / mở khóa User.
    AdminUserStatusResponse updateUserStatus(
            Long userId,
            String status,
            Long adminUserId
    );
}