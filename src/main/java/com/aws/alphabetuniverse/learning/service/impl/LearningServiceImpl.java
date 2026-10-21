
package com.aws.alphabetuniverse.learning.service.impl;

import com.aws.alphabetuniverse.learning.dto.LearningProgressRequest;
import com.aws.alphabetuniverse.learning.dto.LearningProgressResponse;
import com.aws.alphabetuniverse.learning.dto.LearningResponse;
import com.aws.alphabetuniverse.learning.dto.LearningVocabularyItem;
import com.aws.alphabetuniverse.learning.entity.UserTopicLearningProgress;
import com.aws.alphabetuniverse.learning.repository.UserTopicLearningProgressRepository;
import com.aws.alphabetuniverse.learning.service.LearningService;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final UserTopicLearningProgressRepository progressRepository;

    // ==========================================
    // 1. MỞ MÀN HÌNH HỌC TỪ VỰNG
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public LearningResponse openTopicLearning(
            Long topicId,
            Jwt jwt
    ) {

        // Bước 1: Kiểm tra JWT
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // Bước 2: Tìm user từ Cognito sub
        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        // Bước 3: Kiểm tra Topic tồn tại và đang hoạt động
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // Bước 4: Lấy danh sách từ đang hoạt động,
        // sắp xếp theo thứ tự học
        List<Vocabulary> vocabularies =
                vocabularyRepository
                        .findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(
                                topicId
                        );

        // Chuyển Entity sang DTO
        List<LearningVocabularyItem> items =
                vocabularies.stream()
                        .map(this::toLearningItem)
                        .toList();

        // Bước 5: Tìm tiến độ của user trong Topic
        Optional<UserTopicLearningProgress> progressOptional =
                progressRepository.findByUser_IdAndTopic_Id(
                        user.getId(),
                        topicId
                );

        // ==========================================
        // TRƯỜNG HỢP 1: NGƯỜI MỚI HỌC
        // ==========================================

        if (progressOptional.isEmpty()) {

            Vocabulary firstVocabulary = vocabularies.isEmpty()
                    ? null
                    : vocabularies.get(0);

            return buildResponse(
                    topic,
                    items,
                    "NEW",
                    null,
                    firstVocabulary,
                    false
            );
        }

        // ==========================================
        // BƯỚC 6: LẤY TIẾN ĐỘ ĐÃ LƯU
        // ==========================================

        UserTopicLearningProgress progress = progressOptional.get();

        Vocabulary furthestVocabulary = progress.getFurthestVocab();

        if (furthestVocabulary == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Learning progress is invalid"
            );
        }

        // Đảm bảo từ đã lưu tiến độ thuộc đúng Topic
        if (furthestVocabulary.getTopic() == null
                || !topicId.equals(
                furthestVocabulary.getTopic().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Learning progress does not match topic"
            );
        }

        Integer furthestOrderNo =
                furthestVocabulary.getOrderNo();

        // ==========================================
        // TRƯỜNG HỢP 2: TOPIC KHÔNG CÒN TỪ HOẠT ĐỘNG
        // ==========================================

        if (vocabularies.isEmpty()) {

            return buildResponse(
                    topic,
                    items,
                    "REVIEW",
                    furthestOrderNo,
                    null,
                    true
            );
        }

        // ==========================================
        // BƯỚC 7: TÌM TỪ TIẾP THEO
        // ==========================================

        Optional<Vocabulary> nextVocabulary =
                vocabularyRepository
                        .findFirstByTopic_IdAndActiveTrueAndOrderNoGreaterThanOrderByOrderNoAscIdAsc(
                                topicId,
                                furthestOrderNo
                        );

        // ==========================================
        // TRƯỜNG HỢP 2: NGƯỜI DÙNG ĐANG HỌC DỞ
        // ==========================================

        if (nextVocabulary.isPresent()) {

            return buildResponse(
                    topic,
                    items,
                    "LEARNING",
                    furthestOrderNo,
                    nextVocabulary.get(),
                    false
            );
        }

        // ==========================================
        // TRƯỜNG HỢP 3: ĐÃ HỌC HẾT TOPIC
        // ==========================================

        // Cho phép ôn tập tự do, bắt đầu lại từ từ đầu tiên
        Vocabulary firstVocabulary = vocabularies.get(0);

        return buildResponse(
                topic,
                items,
                "REVIEW",
                furthestOrderNo,
                firstVocabulary,
                true
        );
    }


    @Override
    @Transactional
    public LearningProgressResponse saveProgress(
            Long topicId,
            LearningProgressRequest request,
            Jwt jwt
    ) {

        // 1. Kiểm tra JWT
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        if (request == null || request.getVocabId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vocabulary ID is required"
            );
        }

        // 2. Xác định người dùng từ Cognito
        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        // 3. Kiểm tra Topic đang hoạt động
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // 4. Tìm từ mà Frontend gửi lên
        Vocabulary requestedVocabulary =
                vocabularyRepository.findById(request.getVocabId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Vocabulary not found"
                                )
                        );

        // Không cho lưu tiến độ vào từ đã bị ẩn
        if (!Boolean.TRUE.equals(requestedVocabulary.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vocabulary is inactive"
            );
        }

        // Không cho gửi từ thuộc Topic khác
        if (requestedVocabulary.getTopic() == null
                || !topicId.equals(
                requestedVocabulary.getTopic().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vocabulary does not belong to this topic"
            );
        }

        // 5. Tìm tiến độ của user trong Topic
        UserTopicLearningProgress progress =
                progressRepository
                        .findByUser_IdAndTopic_Id(
                                user.getId(),
                                topicId
                        )
                        .orElse(null);

        Instant now = Instant.now();

        // 6. Chưa có tiến độ: tạo bản ghi đầu tiên
        if (progress == null) {

            progress = UserTopicLearningProgress.builder()
                    .user(user)
                    .topic(topic)
                    .furthestVocab(requestedVocabulary)
                    .updatedAt(now)
                    .build();

        } else {

            // 7. Đã có tiến độ: giữ vị trí xa nhất
            Vocabulary currentFurthest =
                    progress.getFurthestVocab();

            if (currentFurthest == null) {

                progress.setFurthestVocab(requestedVocabulary);

            } else {

                // Kiểm tra tính nhất quán của tiến độ cũ
                if (currentFurthest.getTopic() == null
                        || !topicId.equals(
                        currentFurthest.getTopic().getId()
                )) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Existing learning progress is invalid"
                    );
                }

                int orderComparison = Integer.compare(
                        requestedVocabulary.getOrderNo(),
                        currentFurthest.getOrderNo()
                );

                // So sánh orderNo trước, ID sau nếu trùng orderNo
                boolean isFurther =
                        orderComparison > 0
                                || (orderComparison == 0
                                && requestedVocabulary.getId()
                                > currentFurthest.getId());

                // Chỉ cập nhật khi đi tới vị trí xa hơn
                if (isFurther) {
                    progress.setFurthestVocab(requestedVocabulary);
                }
            }

            // Cập nhật thời điểm hoạt động, không giảm tiến độ
            progress.setUpdatedAt(now);
        }

        // 8. Lưu PostgreSQL
        UserTopicLearningProgress savedProgress =
                progressRepository.save(progress);

        Vocabulary furthestVocabulary =
                savedProgress.getFurthestVocab();

        // 9. Trả kết quả
        return LearningProgressResponse.builder()
                .topicId(topic.getId())
                .furthestVocabId(furthestVocabulary.getId())
                .furthestPosition(furthestVocabulary.getOrderNo())
                .updatedAt(savedProgress.getUpdatedAt())
                .message("Learning progress saved successfully")
                .build();
    }


    // ==========================================
    // 2. CHUYỂN VOCABULARY ENTITY SANG DTO
    // ==========================================

    private LearningVocabularyItem toLearningItem(
            Vocabulary vocabulary
    ) {

        return LearningVocabularyItem.builder()
                .id(vocabulary.getId())
                .word(vocabulary.getWord())
                .meaningVi(vocabulary.getMeaningVi())
                .ipa(vocabulary.getIpa())
                .audioUrl(vocabulary.getAudioUrl())
                .orderNo(vocabulary.getOrderNo())
                .build();
    }

    // ==========================================
    // 3. TẠO RESPONSE
    // ==========================================

    private LearningResponse buildResponse(
            Topic topic,
            List<LearningVocabularyItem> items,
            String mode,
            Integer furthestPosition,
            Vocabulary startVocabulary,
            boolean completed
    ) {

        return LearningResponse.builder()
                .topicId(topic.getId())
                .topicName(topic.getName())
                .mode(mode)
                .totalVocabularies(items.size())
                .furthestPosition(furthestPosition)
                .startPosition(
                        startVocabulary == null
                                ? null
                                : startVocabulary.getOrderNo()
                )
                .startVocabId(
                        startVocabulary == null
                                ? null
                                : startVocabulary.getId()
                )
                .completed(completed)
                .vocabularies(items)
                .build();
    }
}
