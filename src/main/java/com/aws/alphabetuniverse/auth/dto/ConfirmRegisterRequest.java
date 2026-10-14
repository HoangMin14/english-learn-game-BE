package com.aws.alphabetuniverse.auth.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRegisterRequest {
    private String email;
    private String code;
}
