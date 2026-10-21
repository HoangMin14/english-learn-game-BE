
package com.aws.alphabetuniverse.vocabulary.controller;

import com.aws.alphabetuniverse.vocabulary.dto.AdminVocabularyResponse;
import com.aws.alphabetuniverse.vocabulary.service.AdminVocabularyService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/topics/{topicId}/vocabularies")
@Tag(name = "Admin-Vocabulary")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final AdminVocabularyService adminVocabularyService;

    @GetMapping
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<List<AdminVocabularyResponse>>
    getVocabulariesByTopic(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        List<AdminVocabularyResponse> response = adminVocabularyService.getVocabulariesByTopic(topicId, jwt);

        return ResponseEntity.ok(response);
    }
}
