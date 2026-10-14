package com.aws.alphabetuniverse.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cognito_sub", unique = true)
    private String cognitoSub;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    private String role;

    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private  LocalDateTime updatedAt;


}
