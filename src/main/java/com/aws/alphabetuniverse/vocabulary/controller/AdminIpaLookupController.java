
package com.aws.alphabetuniverse.vocabulary.controller;

import com.aws.alphabetuniverse.vocabulary.dto.IpaLookupResponse;
import com.aws.alphabetuniverse.vocabulary.service.IpaLookupService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vocabularies")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class AdminIpaLookupController {

    private final IpaLookupService ipaLookupService;

    @GetMapping("/ipa")
    public IpaLookupResponse lookupIpa(
            @RequestParam String word,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ipaLookupService.lookupIpa(word, jwt);
    }
}
