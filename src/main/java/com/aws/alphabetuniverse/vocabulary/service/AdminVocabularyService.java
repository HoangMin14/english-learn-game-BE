package com.aws.alphabetuniverse.vocabulary.service;

import com.aws.alphabetuniverse.vocabulary.dto.AdminVocabularyResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface AdminVocabularyService {

    public List<AdminVocabularyResponse> getVocabulariesByTopic(Long topicId, Jwt jwt);
}