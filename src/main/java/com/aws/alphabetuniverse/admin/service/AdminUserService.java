package com.aws.alphabetuniverse.admin.service;

import com.aws.alphabetuniverse.admin.dto.AdminUserPageResponse;

public interface AdminUserService {

    AdminUserPageResponse getUsers(
            int page,
            int size,
            String keyword,
            String role,
            String status
    );
}