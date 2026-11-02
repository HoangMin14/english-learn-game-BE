package com.aws.alphabetuniverse.admin.dto;

import java.time.LocalDateTime;

public record AdminUserStatusResponse(

        Long id,

        String email,

        String displayName,

        String role,

        String status,

        LocalDateTime updatedAt

) {
}