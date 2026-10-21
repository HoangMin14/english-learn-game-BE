
package com.aws.alphabetuniverse.vocabulary.service.impl;

import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.dto.AdminVocabularyResponse;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;
import com.aws.alphabetuniverse.vocabulary.service.AdminVocabularyService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminVocabularyServiceImpl
        implements AdminVocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    // 1. Kiểm tra quyền Admin
    private void requireAdmin(Jwt jwt) {

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        if (user.getRole() == null
                || !user.getRole().equalsIgnoreCase("admin")) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin access required"
            );
        }
    }

    // 2. Chuyển Entity thành Response DTO
    private AdminVocabularyResponse toResponse(
            Vocabulary vocabulary
    ) {

        return AdminVocabularyResponse.builder()
                .id(vocabulary.getId())
                .topicId(vocabulary.getTopic().getId())
                .topicName(vocabulary.getTopic().getName())
                .word(vocabulary.getWord())
                .meaningVi(vocabulary.getMeaningVi())
                .ipa(vocabulary.getIpa())
                .audioUrl(vocabulary.getAudioUrl())
                .active(vocabulary.getActive())
                .orderNo(vocabulary.getOrderNo())
                .build();
    }

    // 3. Lấy danh sách từ vựng theo Topic
    @Override
    @Transactional(readOnly = true)
    public List<AdminVocabularyResponse> getVocabulariesByTopic(
            Long topicId,
            Jwt jwt
    ) {

        // Chỉ Admin được xem danh sách quản trị
        requireAdmin(jwt);

        // Dùng findById để Admin vẫn quản lý được Topic đã ẩn
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // Lấy tất cả từ thuộc Topic, kể cả từ đã bị ẩn
        List<Vocabulary> vocabularies =
                vocabularyRepository
                        .findByTopic_IdOrderByOrderNoAscIdAsc(
                                topic.getId()
                        );

        return vocabularies.stream()
                .map(this::toResponse)
                .toList();
    }
}
