package com.aws.alphabetuniverse.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String displayName;
    private String role;
    private String status;
}
