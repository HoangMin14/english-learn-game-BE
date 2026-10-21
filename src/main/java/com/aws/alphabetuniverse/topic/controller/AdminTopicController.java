
package com.aws.alphabetuniverse.topic.controller;

import com.aws.alphabetuniverse.topic.dto.TopicRequest;
import com.aws.alphabetuniverse.topic.dto.TopicResponse;
import com.aws.alphabetuniverse.topic.service.TopicService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.beans.PropertyEditorSupport;
import java.util.List;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/topics")
@Tag(name = "admin-topic")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class AdminTopicController {

    private final TopicService topicService;

    // Admin tạo Topic kèm ảnh
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TopicResponse> createTopic(
            @Valid @ModelAttribute TopicRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        TopicResponse response =
                topicService.createTopic(request, jwt);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Admin cập nhật Topic và có thể thay ảnh
    @PutMapping(
            value = "/{topicId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<TopicResponse> updateTopic(
            @PathVariable Long topicId,
            @Valid @ModelAttribute TopicRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        TopicResponse response =
                topicService.updateTopic(topicId, request, jwt);

        return ResponseEntity.ok(response);
    }


    @InitBinder
    public void initBinder(WebDataBinder binder) {

        binder.registerCustomEditor(
                MultipartFile.class,
                "image",
                new PropertyEditorSupport() {

                    @Override
                    public void setAsText(String text) {
                        // Swagger gửi image="" thì coi như không chọn ảnh
                        if (text == null || text.isBlank()) {
                            setValue(null);
                            return;
                        }

                        throw new IllegalArgumentException(
                                "Image must be uploaded as a file"
                        );
                    }
                }
        );
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        topicService.deleteTopic(topicId, jwt);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<TopicResponse>> getAllTopicsForAdmin(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                topicService.getAllTopicsForAdmin(jwt)
        );
    }

    @PatchMapping("/{topicId}/restore")
    public ResponseEntity<TopicResponse> restoreTopic(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        TopicResponse response =
                topicService.restoreTopic(topicId, jwt);

        return ResponseEntity.ok(response);
    }

}
