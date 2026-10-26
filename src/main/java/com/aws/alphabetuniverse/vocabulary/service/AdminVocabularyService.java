package com.aws.alphabetuniverse.vocabulary.service;

import com.aws.alphabetuniverse.vocabulary.dto.AdminVocabularyResponse;
import com.aws.alphabetuniverse.vocabulary.dto.AudioBackfillResponse;
import com.aws.alphabetuniverse.vocabulary.dto.CreateVocabularyRequest;
import com.aws.alphabetuniverse.vocabulary.dto.UpdateVocabularyRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface AdminVocabularyService {

    public List<AdminVocabularyResponse> getVocabulariesByTopic(Long topicId, Jwt jwt);
    public AdminVocabularyResponse createVocabulary(
            Long topicId,
            CreateVocabularyRequest request,
            Jwt jwt
    );
    public AdminVocabularyResponse updateVocabulary(
            Long topicId,
            Long vocabularyId,
            UpdateVocabularyRequest request,
            Jwt jwt
    );

    public void deleteVocabulary(
            Long topicId,
            Long vocabularyId,
            Jwt jwt
    );

    public AudioBackfillResponse generateMissingAudio(
            Long topicId,
            Jwt jwt
    );

}