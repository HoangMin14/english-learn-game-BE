package com.aws.alphabetuniverse.admin.controller;

import com.aws.alphabetuniverse.admin.dto.AdminUserPageResponse;

import com.aws.alphabetuniverse.admin.dto.AdminUserStatusResponse;
import com.aws.alphabetuniverse.admin.dto.UpdateUserStatusRequest;
import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.admin.service.AdminUserService;

import com.aws.alphabetuniverse.user.entity.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin-user-management")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminUserController {

    private final AdminUserService adminUserService;

    private final AdminAccessService adminAccessService;


    // ==========================================
    // GET ADMIN USERS
    // ==========================================

    @GetMapping
    public AdminUserPageResponse getUsers(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            String role,

            @RequestParam(required = false)
            String status,

            @AuthenticationPrincipal Jwt jwt

    ) {

        // 1. Check Admin ACTIVE

        adminAccessService.requireActiveAdmin(jwt);


        // 2. Get users

        return adminUserService.getUsers(
                page,
                size,
                keyword,
                role,
                status
        );
    }

    // ==========================================
    // API 2: PATCH BLOCK / UNBLOCK USER
    // ==========================================

    @PatchMapping("/{userId}/status")
    public AdminUserStatusResponse updateUserStatus(

            @PathVariable Long userId,

            @Valid
            @RequestBody UpdateUserStatusRequest request,

            @AuthenticationPrincipal Jwt jwt

    ) {

        // 1. Xác thực Admin.
        // Lấy ID Admin từ DB, không lấy từ FE.
        User admin = adminAccessService.requireActiveAdmin(jwt);

        // 2. Cập nhật trạng thái User.
        return adminUserService.updateUserStatus(
                userId,
                request.status(),
                admin.getId()
        );
    }
}