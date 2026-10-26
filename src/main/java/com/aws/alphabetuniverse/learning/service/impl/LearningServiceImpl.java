
package com.aws.alphabetuniverse.learning.service.impl;

import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.learning.dto.LearningProgressRequest;
import com.aws.alphabetuniverse.learning.dto.LearningProgressResponse;
import com.aws.alphabetuniverse.learning.dto.LearningResponse;
import com.aws.alphabetuniverse.learning.dto.LearningVocabularyItem;
import com.aws.alphabetuniverse.learning.entity.UserTopicLearningProgress;
import com.aws.alphabetuniverse.learning.repository.UserTopicLearningProgressRepository;
import com.aws.alphabetuniverse.learning.service.LearningService;
import com.aws.alphabetuniverse.storage.service.S3StorageService;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;
import com.aws.alphabetuniverse.game.service.CheckpointService;

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
    private final S3StorageService s3StorageService;
    private final CheckpointService checkpointService;
    private static final int WORDS_PER_CHECKPOINT = 5;

    // ==========================================
    // 1. MỞ MÀN HÌNH HỌC TỪ VỰNG
    // ==========================================
    @Override
    @Transactional
    public LearningResponse openTopicLearning(
            Long topicId,
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

        // 2. Tìm user từ Cognito sub
        User authenticatedUser = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        User user = userRepository
                .findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        // 3. Kiểm tra Topic
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));

        // 4. Lấy toàn bộ từ vựng đang hoạt động theo thứ tự
        List<Vocabulary> vocabularies =
                vocabularyRepository
                        .findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(
                                topicId
                        );

        int totalVocabularies = vocabularies.size();

        // Topic không có từ vựng đang hoạt động.
        if (vocabularies.isEmpty()) {
            return buildResponse(
                    topic,
                    List.of(),
                    0,
                    vocabularies,
                    "REVIEW",
                    null,
                    null,
                    true
            );
        }

        // 5. Lấy tiến độ hiện tại
        Optional<UserTopicLearningProgress> progressOptional =
                progressRepository.findByUser_IdAndTopic_Id(
                        user.getId(),
                        topicId
                );

        // 6. Người mới: chỉ trả tối đa 5 từ đầu tiên
        if (progressOptional.isEmpty()) {
            int unlockedPosition = Math.min(
                    WORDS_PER_CHECKPOINT,
                    totalVocabularies
            );

            List<LearningVocabularyItem> items =
                    vocabularies.stream()
                            .limit(unlockedPosition)
                            .map(this::toLearningItem)
                            .toList();

            Vocabulary firstVocabulary = vocabularies.isEmpty()
                    ? null
                    : vocabularies.get(0);

            return buildResponse(
                    topic,
                    items,
                    totalVocabularies,
                    vocabularies,
                    "NEW",
                    null,
                    firstVocabulary,
                    false
            );
        }

        UserTopicLearningProgress progress = progressOptional.get();

        Vocabulary furthestVocabulary = progress.getFurthestVocab();

        // 7. Kiểm tra tiến độ đã lưu
        if (furthestVocabulary == null
                || furthestVocabulary.getTopic() == null
                || !topicId.equals(
                furthestVocabulary.getTopic().getId()
        )
                || furthestVocabulary.getOrderNo() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Learning progress is invalid"
            );
        }

        // Topic không còn từ hoạt động
        if (vocabularies.isEmpty()) {
            return buildResponse(
                    topic,
                    List.of(),
                    0,
                    vocabularies,
                    "REVIEW",
                    null,
                    null,
                    true
            );
        }

        // 8. Xác định vị trí từ đã học xa nhất
        int furthestIndex = indexOfVocabulary(
                vocabularies,
                furthestVocabulary.getId()
        );

        if (furthestIndex < 0) {
            furthestVocabulary =
                    findNearestActiveVocabularyAtOrBeforeOrderNo(
                            vocabularies,
                            furthestVocabulary.getOrderNo()
                    );

            progress.setFurthestVocab(furthestVocabulary);
            progress.setUpdatedAt(Instant.now());

            progressRepository.save(progress);

            furthestIndex = indexOfVocabulary(
                    vocabularies,
                    furthestVocabulary.getId()
            );
        }

        if (furthestIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to repair learning progress"
            );
        }

        int furthestPosition = furthestIndex + 1;

        // 9. Xác định số từ được phép học
        int unlockedPosition = calculateUnlockedPosition(
                user.getId(),
                topicId,
                furthestPosition,
                totalVocabularies,
                vocabularies
        );

        List<LearningVocabularyItem> items =
                vocabularies.stream()
                        .limit(unlockedPosition)
                        .map(this::toLearningItem)
                        .toList();

        // 10. Kiểm tra CHECKPOINT chưa vượt qua
        Integer pendingCheckpointEnd =
                findFirstUnpassedCheckpointEnd(
                        user.getId(),
                        topicId,
                        furthestPosition,
                        totalVocabularies,
                        vocabularies
                );

        if (pendingCheckpointEnd != null) {
            return buildResponse(
                    topic,
                    items,
                    totalVocabularies,
                    vocabularies,
                    "CHECKPOINT_REQUIRED",
                    furthestPosition,
                    null,
                    false
            );
        }

        // 11. Còn từ chưa học: tiếp tục học từ kế tiếp
        if (furthestPosition < totalVocabularies) {
            Vocabulary nextVocabulary =
                    vocabularies.get(furthestPosition);

            return buildResponse(
                    topic,
                    items,
                    totalVocabularies,
                    vocabularies,
                    "LEARNING",
                    furthestPosition,
                    nextVocabulary,
                    false
            );
        }

        // 12. Đã học hết và không còn CHECKPOINT chờ vượt qua
        return buildResponse(
                topic,
                items,
                totalVocabularies,
                vocabularies,
                "REVIEW",
                furthestPosition,
                vocabularies.get(0),
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
        // 1. Kiểm tra JWT Cognito
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // 2. Kiểm tra request
        if (request == null || request.getVocabId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vocabulary ID is required"
            );
        }

        // 3. Tìm user từ Cognito sub
        User authenticatedUser = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        User user = userRepository
                .findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        // 4. Kiểm tra Topic đang hoạt động
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));

        // 5. Tìm từ vựng được yêu cầu
        Vocabulary requestedVocabulary = vocabularyRepository
                .findById(request.getVocabId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));

        // 6. Kiểm tra từ vựng đang hoạt động
        if (!Boolean.TRUE.equals(requestedVocabulary.getActive())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vocabulary is inactive"
            );
        }

        // 7. Kiểm tra từ vựng thuộc đúng Topic
        if (requestedVocabulary.getTopic() == null
                || !topicId.equals(
                requestedVocabulary.getTopic().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vocabulary does not belong to this topic"
            );
        }

        if (requestedVocabulary.getOrderNo() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vocabulary order is missing"
            );
        }

        // 8. Lấy danh sách từ vựng đang hoạt động theo thứ tự
        List<Vocabulary> activeVocabularies =
                vocabularyRepository
                        .findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(
                                topicId
                        );

        if (activeVocabularies.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Topic has no active vocabularies"
            );
        }

        // 9. Tìm tiến độ hiện tại của user trong Topic
        UserTopicLearningProgress progress =
                progressRepository.findByUser_IdAndTopic_Id(
                        user.getId(),
                        topicId
                ).orElse(null);

        Instant now = Instant.now();

        // 10. Chưa có tiến độ: bắt buộc bắt đầu từ từ đầu tiên
        if (progress == null) {
            Vocabulary firstVocabulary = activeVocabularies.get(0);

            if (!firstVocabulary.getId().equals(
                    requestedVocabulary.getId()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Learning must start from the first active vocabulary"
                );
            }

            progress = UserTopicLearningProgress.builder()
                    .user(user)
                    .topic(topic)
                    .furthestVocab(firstVocabulary)
                    .updatedAt(now)
                    .build();


        } else {
            Vocabulary currentFurthest = progress.getFurthestVocab();

            if (currentFurthest == null
                    || currentFurthest.getTopic() == null
                    || !topicId.equals(currentFurthest.getTopic().getId())
                    || currentFurthest.getOrderNo() == null) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existing learning progress is invalid"
                );
            }

            int currentIndex = indexOfVocabulary(
                    activeVocabularies,
                    currentFurthest.getId()
            );

// Từ đang lưu tiến độ đã bị xóa mềm.
            if (currentIndex < 0) {
                currentFurthest =
                        findNearestActiveVocabularyAtOrBeforeOrderNo(
                                activeVocabularies,
                                currentFurthest.getOrderNo()
                        );

                progress.setFurthestVocab(currentFurthest);

                currentIndex = indexOfVocabulary(
                        activeVocabularies,
                        currentFurthest.getId()
                );
            }

            int requestedIndex = indexOfVocabulary(
                    activeVocabularies,
                    requestedVocabulary.getId()
            );

            if (currentIndex < 0 || requestedIndex < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unable to resolve active learning progress"
                );
            }

            // Chỉ được tiến đúng một từ.
            if (requestedIndex > currentIndex) {

                if (requestedIndex != currentIndex + 1) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "You can only advance to the next active vocabulary"
                    );
                }


                int requestedPosition = requestedIndex + 1;

                // Mọi nhóm đứng trước từ đang yêu cầu đều phải được vượt qua.
                for (int batchStart = 1;
                     batchStart < requestedPosition;
                     batchStart += WORDS_PER_CHECKPOINT) {

                    int batchEnd = Math.min(
                            batchStart + WORDS_PER_CHECKPOINT - 1,
                            activeVocabularies.size()
                    );

                    // Không bắt buộc vượt qua nhóm đang học dở.
                    if (batchEnd >= requestedPosition) {
                        break;
                    }

                    boolean checkpointWon = checkpointService.hasWonCheckpoint(
                            user.getId(),
                            topicId,
                            batchStart,
                            batchEnd,
                            activeVocabularies
                    );

                    if (!checkpointWon) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "You must win checkpoint "
                                        + batchStart + "-" + batchEnd
                                        + " before learning the next vocabulary"
                        );
                    }
                }

                progress.setFurthestVocab(requestedVocabulary);

            }

            // Không giảm tiến độ khi xem lại từ cũ.
            progress.setUpdatedAt(now);
        }


        // 14. Lưu tiến độ vào PostgreSQL
        UserTopicLearningProgress savedProgress =
                progressRepository.save(progress);

        Vocabulary furthestVocabulary =
                savedProgress.getFurthestVocab();

        int savedFurthestIndex = indexOfVocabulary(
                activeVocabularies,
                furthestVocabulary.getId()
        );

        if (savedFurthestIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Saved learning progress does not match active vocabularies"
            );
        }
        // 15. Trả kết quả
        return LearningProgressResponse.builder()
                .topicId(topic.getId())
                .furthestVocabId(furthestVocabulary.getId())
                .furthestPosition(savedFurthestIndex + 1)
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
        String storedAudioKey = vocabulary.getAudioUrl();
        String playableAudioUrl = null;

        // Chỉ tạo URL nghe khi database lưu S3 object key hợp lệ.
        if (storedAudioKey != null
                && storedAudioKey.startsWith("vocabulary-audio/")) {

            playableAudioUrl =
                    s3StorageService.generatePresignedAudioGetUrl(
                            storedAudioKey
                    );
        }

        return LearningVocabularyItem.builder()
                .id(vocabulary.getId())
                .word(vocabulary.getWord())
                .meaningVi(vocabulary.getMeaningVi())
                .ipa(vocabulary.getIpa())
                .audioUrl(playableAudioUrl)
                .orderNo(vocabulary.getOrderNo())
                .build();
    }


    // ==========================================
    // 3. TẠO RESPONSE
    // ==========================================



    private LearningResponse buildResponse(
            Topic topic,
            List<LearningVocabularyItem> items,
            int totalVocabularies,
            List<Vocabulary> activeVocabularies,
            String mode,
            Integer furthestPosition,
            Vocabulary startVocabulary,
            boolean completed
    ) {
        Integer startPosition = null;

        if (startVocabulary != null) {
            int startIndex = indexOfVocabulary(
                    activeVocabularies,
                    startVocabulary.getId()
            );

            if (startIndex < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Start vocabulary does not match active vocabularies"
                );
            }

            startPosition = startIndex + 1;
        }

        return LearningResponse.builder()
                .topicId(topic.getId())
                .topicName(topic.getName())
                .mode(mode)
                .totalVocabularies(totalVocabularies)
                .furthestPosition(furthestPosition)
                .startPosition(startPosition)
                .startVocabId(
                        startVocabulary == null
                                ? null
                                : startVocabulary.getId()
                )
                .completed(completed)
                .vocabularies(items)
                .build();
    }




    private int indexOfVocabulary(
            List<Vocabulary> vocabularies,
            Long vocabularyId
    ) {
        for (int i = 0; i < vocabularies.size(); i++) {
            if (vocabularies.get(i).getId().equals(vocabularyId)) {
                return i;
            }
        }

        return -1;
    }

    private int calculateUnlockedPosition(
            Long userId,
            Long topicId,
            int furthestPosition,
            int totalVocabularies,
            List<Vocabulary> activeVocabularies
    ) {
        if (totalVocabularies == 0) {
            return 0;
        }

        int unlockedPosition = Math.min(
                WORDS_PER_CHECKPOINT,
                totalVocabularies
        );

        for (int start = 1;
             start <= totalVocabularies;
             start += WORDS_PER_CHECKPOINT) {

            int end = Math.min(
                    start + WORDS_PER_CHECKPOINT - 1,
                    totalVocabularies
            );

            if (furthestPosition < end) {
                break;
            }

            if (!checkpointService.hasWonCheckpoint(
                    userId,
                    topicId,
                    start,
                    end,
                    activeVocabularies
            )) {
                break;
            }

            unlockedPosition = Math.min(
                    end + WORDS_PER_CHECKPOINT,
                    totalVocabularies
            );
        }

        return unlockedPosition;
    }

    private Integer findFirstUnpassedCheckpointEnd(
            Long userId,
            Long topicId,
            int furthestPosition,
            int totalVocabularies,
            List<Vocabulary> activeVocabularies
    ) {
        for (int start = 1;
             start <= totalVocabularies;
             start += WORDS_PER_CHECKPOINT) {

            int end = Math.min(
                    start + WORDS_PER_CHECKPOINT - 1,
                    totalVocabularies
            );

            if (furthestPosition < end) {
                break;
            }

            if (!checkpointService.hasWonCheckpoint(
                    userId,
                    topicId,
                    start,
                    end,
                    activeVocabularies
            )) {
                return end;
            }
        }

        return null;
    }

    private Vocabulary findNearestActiveVocabularyAtOrBeforeOrderNo(
            List<Vocabulary> vocabularies,
            Integer targetOrderNo
    ) {
        if (vocabularies == null || vocabularies.isEmpty()) {
            return null;
        }

        // Nếu không có orderNo hợp lệ, lấy từ đầu tiên đang hoạt động.
        if (targetOrderNo == null) {
            return vocabularies.get(0);
        }

        Vocabulary nearestVocabulary = null;

        for (Vocabulary vocabulary : vocabularies) {
            Integer orderNo = vocabulary.getOrderNo();

            if (orderNo == null || orderNo > targetOrderNo) {
                continue;
            }

            if (nearestVocabulary == null
                    || orderNo >= nearestVocabulary.getOrderNo()) {
                nearestVocabulary = vocabulary;
            }
        }

        // Nếu không có từ nào ở vị trí trước hoặc bằng vị trí cũ,
        // bắt đầu lại từ từ hoạt động đầu tiên.
        return nearestVocabulary != null
                ? nearestVocabulary
                : vocabularies.get(0);
    }

}
