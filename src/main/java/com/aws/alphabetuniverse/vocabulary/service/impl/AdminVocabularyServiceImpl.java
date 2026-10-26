package com.aws.alphabetuniverse.vocabulary.service.impl;

import com.aws.alphabetuniverse.storage.service.S3StorageService;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.dto.*;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;
import com.aws.alphabetuniverse.vocabulary.service.AdminVocabularyService;
import com.aws.alphabetuniverse.vocabulary.service.IpaLookupService;
import com.aws.alphabetuniverse.vocabulary.service.PollyAudioService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AdminVocabularyServiceImpl implements AdminVocabularyService {


    private final VocabularyRepository vocabularyRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    private final IpaLookupService ipaLookupService;
    private final PollyAudioService pollyAudioService;
    private final S3StorageService s3StorageService;
    private static final Logger audioBackfillLog = LoggerFactory.getLogger(AdminVocabularyServiceImpl.class);

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

    // 2. Chuyển S3 object key thành URL nghe được
    private String resolveAudioUrl(String storedAudio) {

        if (storedAudio == null || storedAudio.isBlank()) {
            return null;
        }

        if (storedAudio.startsWith("vocabulary-audio/")) {
            return s3StorageService
                    .generatePresignedAudioGetUrl(storedAudio);
        }

        // Hỗ trợ bản ghi cũ đang lưu URL HTTP/HTTPS
        if (storedAudio.startsWith("https://")
                || storedAudio.startsWith("http://")) {
            return storedAudio;
        }

        // Ví dụ: s3://audio/apple.mp3 không phải URL phát trực tiếp
        return null;
    }

    // 3. Chuyển Entity thành Response
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
                .audioUrl(resolveAudioUrl(vocabulary.getAudioUrl()))
                .active(vocabulary.getActive())
                .orderNo(vocabulary.getOrderNo())
                .build();
    }

    // 4. GET: Lấy danh sách từ vựng theo Topic
    @Override
    @Transactional(readOnly = true)
    public List<AdminVocabularyResponse> getVocabulariesByTopic(
            Long topicId,
            Jwt jwt
    ) {

        requireAdmin(jwt);

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        List<Vocabulary> vocabularies =
                vocabularyRepository
                        .findByTopic_IdOrderByOrderNoAscIdAsc(
                                topic.getId()
                        );

        return vocabularies.stream()
                .map(this::toResponse)
                .toList();
    }

    // 5. POST: Tạo từ vựng, tự tra IPA và tạo audio
    @Override
    @Transactional
    public AdminVocabularyResponse createVocabulary(
            Long topicId,
            CreateVocabularyRequest request,
            Jwt jwt
    ) {

        // Bước 1: Kiểm tra quyền
        requireAdmin(jwt);

        // Bước 2: Kiểm tra dữ liệu
        if (request == null
                || request.word() == null
                || request.word().isBlank()
                || request.meaningVi() == null
                || request.meaningVi().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Word and Vietnamese meaning are required"
            );
        }

        String word = request.word()
                .trim()
                .toLowerCase(Locale.ROOT);

        String meaningVi = request.meaningVi().trim();

        if (meaningVi.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vietnamese meaning must not exceed 255 characters"
            );
        }

        if (!word.matches("[a-z]+(?:[-'][a-z]+)*")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid English word"
            );
        }

        // Bước 3: Kiểm tra Topic tồn tại
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // Bước 4: Kiểm tra từ trùng
        if (vocabularyRepository
                .existsByTopic_IdAndWordIgnoreCase(topicId, word)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vocabulary already exists in this topic"
            );
        }

        // Bước 5: Tự động lấy IPA
        IpaLookupResponse ipaResponse =
                ipaLookupService.lookupIpa(word, jwt);

        if (ipaResponse == null
                || ipaResponse.ipa() == null
                || ipaResponse.ipa().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not retrieve IPA"
            );
        }

        // Bước 6: Amazon Polly tạo MP3
        byte[] audioBytes =
                pollyAudioService.generateAudio(word);

        // Bước 7: Upload MP3 lên S3
        // DB lưu object key, không lưu presigned URL
        String audioKey =
                s3StorageService.uploadVocabularyAudio(
                        topicId,
                        request.word(),
                        audioBytes
                );

        // Bước 8: Tự sinh thứ tự từ vựng
        Integer maxOrderNo =
                vocabularyRepository.findMaxOrderNoByTopicId(topicId);

        int nextOrderNo =
                (maxOrderNo == null ? 0 : maxOrderNo) + 1;

        // Bước 9: Tạo Entity
        Vocabulary vocabulary = Vocabulary.builder()
                .topic(topic)
                .word(word)
                .meaningVi(meaningVi)
                .ipa(ipaResponse.ipa())
                .audioUrl(audioKey)
                .active(true)
                .orderNo(nextOrderNo)
                .build();

        // Bước 10: Lưu PostgreSQL
        Vocabulary saved =
                vocabularyRepository.saveAndFlush(vocabulary);

        // Bước 11: Trả kết quả cho Admin
        return toResponse(saved);
    }



    @Override
    @Transactional
    public AdminVocabularyResponse updateVocabulary(
            Long topicId,
            Long vocabularyId,
            UpdateVocabularyRequest request,
            Jwt jwt
    ) {
        // 1. Kiểm tra quyền Admin.
        requireAdmin(jwt);

        // 2. Kiểm tra request.
        if (request == null
                || (request.word() == null
                && request.meaningVi() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one field must be provided"
            );
        }

        // 3. Tìm từ vựng hiện tại.
        Vocabulary vocabulary = vocabularyRepository
                .findById(vocabularyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));

        // 4. Kiểm tra từ vựng thuộc đúng Topic.
        if (vocabulary.getTopic() == null
                || !topicId.equals(vocabulary.getTopic().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vocabulary not found in this topic"
            );
        }

        // 5. Mặc định giữ nguyên dữ liệu hiện tại.
        String normalizedWord = vocabulary.getWord();
        String normalizedMeaning = vocabulary.getMeaningVi();

        boolean wordProvided = request.word() != null;
        boolean meaningProvided = request.meaningVi() != null;

        // 6. Chỉ xử lý từ tiếng Anh nếu Admin gửi trường word.
        if (wordProvided) {
            if (request.word().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "English word must not be blank"
                );
            }

            normalizedWord = request.word()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (!normalizedWord.matches("[a-z]+(?:[-'][a-z]+)*")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Please enter a valid English word"
                );
            }

            // Kiểm tra trùng từ trong cùng Topic.
            boolean duplicate = vocabularyRepository
                    .existsByTopic_IdAndWordIgnoreCaseAndIdNot(
                            topicId,
                            normalizedWord,
                            vocabularyId
                    );

            if (duplicate) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This word already exists in the topic"
                );
            }
        }

        // 7. Chỉ xử lý nghĩa tiếng Việt nếu Admin gửi trường này.
        if (meaningProvided) {
            if (request.meaningVi().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vietnamese meaning must not be blank"
                );
            }

            normalizedMeaning = request.meaningVi().trim();
        }

        // 8. Chỉ sinh lại IPA và audio khi từ tiếng Anh thay đổi.
        String oldWord = vocabulary.getWord();

        boolean wordChanged = wordProvided
                && (oldWord == null
                || !normalizedWord.equalsIgnoreCase(oldWord.trim()));

        if (wordChanged) {
            // 8.1. Tra IPA bằng Merriam-Webster.
            IpaLookupResponse ipaResponse =
                    ipaLookupService.lookupIpa(normalizedWord, jwt);

            if (ipaResponse == null
                    || ipaResponse.ipa() == null
                    || ipaResponse.ipa().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Could not retrieve IPA for the new word"
                );
            }

            // 8.2. Tạo audio bằng Amazon Polly.
            byte[] audioBytes =
                    pollyAudioService.generateAudio(normalizedWord);

            // 8.3. Upload audio mới lên S3.
            String audioKey =
                    s3StorageService.uploadVocabularyAudio(
                            topicId,
                            normalizedWord,
                            audioBytes
                    );

            // 8.4. Cập nhật IPA và S3 object key.
            vocabulary.setIpa(ipaResponse.ipa());
            vocabulary.setAudioUrl(audioKey);
        }

        // 9. Chỉ cập nhật các trường được gửi lên.
        if (wordProvided) {
            vocabulary.setWord(normalizedWord);
        }

        if (meaningProvided) {
            vocabulary.setMeaningVi(normalizedMeaning);
        }

        // Giữ nguyên Topic, active và orderNo.
        Vocabulary saved = vocabularyRepository.save(vocabulary);

        // 10. Sinh presigned URL mới từ S3 object key.
        String storedAudioKey = saved.getAudioUrl();
        String playableAudioUrl = null;

        if (storedAudioKey != null
                && storedAudioKey.startsWith("vocabulary-audio/")) {

            playableAudioUrl =
                    s3StorageService.generatePresignedAudioGetUrl(
                            storedAudioKey
                    );

        } else if (storedAudioKey != null
                && (storedAudioKey.startsWith("https://")
                || storedAudioKey.startsWith("http://"))) {

            // Tương thích dữ liệu cũ đang lưu URL đầy đủ.
            playableAudioUrl = storedAudioKey;
        }

        // 11. Trả response.
        return AdminVocabularyResponse.builder()
                .id(saved.getId())
                .topicId(saved.getTopic().getId())
                .topicName(saved.getTopic().getName())
                .word(saved.getWord())
                .meaningVi(saved.getMeaningVi())
                .ipa(saved.getIpa())
                .audioUrl(playableAudioUrl)
                .active(saved.getActive())
                .orderNo(saved.getOrderNo())
                .build();
    }


    @Override
    @Transactional
    public void deleteVocabulary(Long topicId, Long vocabularyId, Jwt jwt) {
        // 1. Kiểm tra quyền Admin.
        requireAdmin(jwt);

        // 2. Tìm từ vựng cần xóa.
        Vocabulary vocabulary = vocabularyRepository
                .findById(vocabularyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));

        // 3. Kiểm tra từ vựng có thuộc Topic được yêu cầu không.
        if (vocabulary.getTopic() == null
                || !topicId.equals(vocabulary.getTopic().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Vocabulary not found in this topic"
            );
        }

        // 4. Xóa mềm: chỉ chuyển active thành false.
        vocabulary.setActive(false);

        // 5. Lưu thay đổi vào database.
        vocabularyRepository.save(vocabulary);
    }


    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AudioBackfillResponse generateMissingAudio(
            Long topicId,
            Jwt jwt
    ) {
        // 1. Chỉ Admin được thực hiện.
        requireAdmin(jwt);

        // 2. Kiểm tra Topic tồn tại.
        topicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));

        // 3. Lấy từ vựng của Topic.
        List<Vocabulary> vocabularies =
                vocabularyRepository
                        .findAllByTopic_IdOrderByOrderNoAsc(topicId);

        // 4. Chỉ xử lý từ đang hoạt động và chưa có audio.

        List<Vocabulary> missingAudio = vocabularies.stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .filter(v -> {
                    String audioUrl = v.getAudioUrl();

                    return audioUrl == null
                            || audioUrl.isBlank()
                            || !audioUrl.startsWith("vocabulary-audio/");
                })
                .toList();


        int generated = 0;
        List<Long> failedIds = new ArrayList<>();

        // 5. Polly -> S3 -> PostgreSQL.
        for (Vocabulary vocabulary : missingAudio) {
            try {
                if (vocabulary.getWord() == null
                        || vocabulary.getWord().isBlank()) {
                    throw new IllegalStateException(
                            "Vocabulary word is blank"
                    );
                }

                String word = vocabulary.getWord()
                        .trim()
                        .toLowerCase(Locale.ROOT);

                // Tạo MP3 bằng Amazon Polly.
                byte[] audioBytes =
                        pollyAudioService.generateAudio(word);

                // Upload S3 và nhận object key.
                // Dùng đúng overload 3 tham số mà API
                // tạo/sửa từ vựng hiện tại đang sử dụng.
                String audioKey =
                        s3StorageService.uploadVocabularyAudio(
                                topicId,
                                word,
                                audioBytes
                        );

                if (audioKey == null || audioKey.isBlank()) {
                    throw new IllegalStateException(
                            "S3 audio object key is empty"
                    );
                }

                // DB chỉ lưu object key, không lưu presigned URL.
                vocabulary.setAudioUrl(audioKey);
                vocabularyRepository.save(vocabulary);

                generated++;

            } catch (Exception ex) {
                audioBackfillLog.warn(
                        "Failed to generate audio for vocabularyId={}",
                        vocabulary.getId(),
                        ex
                );

                failedIds.add(vocabulary.getId());
            }
        }

        // 6. Trả kết quả xử lý.
        return new AudioBackfillResponse(
                topicId,
                missingAudio.size(),
                generated,
                failedIds.size(),
                List.copyOf(failedIds)
        );
    }



}