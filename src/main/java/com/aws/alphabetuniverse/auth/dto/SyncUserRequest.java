package com.aws.alphabetuniverse.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncUserRequest {
    private String cognitoSub;


    private String email;


    private String displayName;
}
