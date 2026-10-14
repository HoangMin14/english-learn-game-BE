package com.aws.alphabetuniverse.user.controller;

import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication){
        return userService.getCurrentUser(authentication);
    }
}
