package com.aws.alphabetuniverse.learning.service.impl;

import com.aws.alphabetuniverse.game.service.CheckpointService;
import com.aws.alphabetuniverse.learning.repository.UserTopicLearningProgressRepository;
import com.aws.alphabetuniverse.storage.service.S3StorageService;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearningAudioTest {

    private static final String KEY = "vocabulary-audio/topic-1/apple.mp3";
    private final UserRepository users = mock(UserRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final UserTopicLearningProgressRepository progress = mock(UserTopicLearningProgressRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final CheckpointService checkpoints = mock(CheckpointService.class);
    private final LearningServiceImpl service = new LearningServiceImpl(
            users, topics, vocabularies, progress, storage, checkpoints
    );
    private final Jwt jwt = Jwt.withTokenValue("local-test-token")
            .header("alg", "none").subject("sub-7").build();
    private Vocabulary apple;

    @BeforeEach
    void setUp() {
        var user = User.builder().id(7L).role("learner").status("ACTIVE").build();
        var topic = Topic.builder().id(1L).name("Food").active(true).build();
        apple = Vocabulary.builder().id(17L).topic(topic).word("apple").meaningVi("Táo")
                .audioUrl(KEY).orderNo(1).active(true).build();
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(topics.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(topic));
        when(vocabularies.findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(1L))
                .thenReturn(List.of(apple));
        when(progress.findByUser_IdAndTopic_Id(7L, 1L)).thenReturn(Optional.empty());
    }

    @Test
    void learningReturnsAudioForTheEnglishWordWithoutChangingTheStoredKey() {
        when(storage.generatePresignedAudioGetUrl(KEY))
                .thenReturn("https://media.example.test/apple.mp3?signature=fresh");

        var item = service.openTopicLearning(1L, jwt).getVocabularies().get(0);

        assertEquals("apple", item.getWord());
        assertEquals("Táo", item.getMeaningVi());
        assertEquals("https://media.example.test/apple.mp3?signature=fresh", item.getAudioUrl());
        assertEquals(KEY, apple.getAudioUrl());
        verify(vocabularies, never()).save(any());
    }

    @Test
    void reopeningLearningGeneratesAFreshAudioUrl() {
        when(storage.generatePresignedAudioGetUrl(KEY))
                .thenReturn("https://media.example.test/apple.mp3?signature=first",
                        "https://media.example.test/apple.mp3?signature=second");

        var first = service.openTopicLearning(1L, jwt).getVocabularies().get(0).getAudioUrl();
        var second = service.openTopicLearning(1L, jwt).getVocabularies().get(0).getAudioUrl();

        assertNotEquals(first, second);
        verify(storage, times(2)).generatePresignedAudioGetUrl(KEY);
    }

    @Test
    void missingOrUnsupportedAudioDoesNotPreventLearning() {
        for (String value : new String[]{null, "", "s3://bucket/apple.mp3",
                "https://media.example.test/expired.mp3"}) {
            apple.setAudioUrl(value);
            assertNull(service.openTopicLearning(1L, jwt).getVocabularies().get(0).getAudioUrl());
        }
        verifyNoInteractions(storage);
    }
}
