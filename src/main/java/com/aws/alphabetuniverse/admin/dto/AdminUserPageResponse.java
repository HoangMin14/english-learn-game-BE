package com.aws.alphabetuniverse.admin.dto;

import java.util.List;

public record AdminUserPageResponse(

        List<AdminUserResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages

) {
}