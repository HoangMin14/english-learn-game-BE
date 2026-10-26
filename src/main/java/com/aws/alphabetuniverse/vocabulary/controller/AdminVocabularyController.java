
package com.aws.alphabetuniverse.vocabulary.controller;

import com.aws.alphabetuniverse.vocabulary.dto.AdminVocabularyResponse;
import com.aws.alphabetuniverse.vocabulary.dto.AudioBackfillResponse;
import com.aws.alphabetuniverse.vocabulary.dto.CreateVocabularyRequest;
import com.aws.alphabetuniverse.vocabulary.dto.UpdateVocabularyRequest;
import com.aws.alphabetuniverse.vocabulary.service.AdminVocabularyService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
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

    @PostMapping
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<AdminVocabularyResponse> createVocabulary(
            @PathVariable Long topicId,
            @Valid @RequestBody CreateVocabularyRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AdminVocabularyResponse response =
                adminVocabularyService.createVocabulary(
                        topicId,
                        request,
                        jwt
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{vocabularyId}")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<AdminVocabularyResponse> updateVocabulary(
            @PathVariable Long topicId,
            @PathVariable Long vocabularyId,
            @Valid @RequestBody UpdateVocabularyRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                adminVocabularyService.updateVocabulary(
                        topicId,
                        vocabularyId,
                        request,
                        jwt
                )
        );
    }


    @DeleteMapping("/{vocabularyId}")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> deleteVocabulary(
            @PathVariable Long topicId,
            @PathVariable Long vocabularyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminVocabularyService.deleteVocabulary(
                topicId,
                vocabularyId,
                jwt
        );

        return ResponseEntity.noContent().build();
    }


    @PostMapping("/generate-missing-audio")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<AudioBackfillResponse> generateMissingAudio(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                adminVocabularyService.generateMissingAudio(
                        topicId,
                        jwt
                )
        );
    }


}
