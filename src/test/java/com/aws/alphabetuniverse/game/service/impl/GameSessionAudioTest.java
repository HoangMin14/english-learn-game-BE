package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.CreateGameSessionRequest;
import com.aws.alphabetuniverse.game.dto.EffectiveGameConfig;
import com.aws.alphabetuniverse.game.dto.GameQuestionResponse;
import com.aws.alphabetuniverse.game.entity.GameQuestionSnapshot;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import com.aws.alphabetuniverse.game.enums.GameMode;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.repository.SessionAttemptRepository;
import com.aws.alphabetuniverse.game.service.CheckpointService;
import com.aws.alphabetuniverse.game.service.GameConfigService;
import com.aws.alphabetuniverse.game.service.GameUserAuthorizationService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class GameSessionAudioTest {

    private static final String APPLE_KEY = "vocabulary-audio/topic-1/apple-original.mp3";
    private static final String MILK_KEY = "vocabulary-audio/topic-1/milk-original.mp3";
    private static final String APPLE_URL = "https://media.example.test/apple.mp3?signature=fresh";
    private static final String MILK_URL = "https://media.example.test/milk.mp3?signature=fresh";

    private final UserRepository users = mock(UserRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final UserTopicLearningProgressRepository learningProgress =
            mock(UserTopicLearningProgressRepository.class);
    private final GameSessionRepository sessions = mock(GameSessionRepository.class);
    private final GameConfigService configs = mock(GameConfigService.class);
    private final CheckpointService checkpoints = mock(CheckpointService.class);
    private final SessionAttemptRepository attempts = mock(SessionAttemptRepository.class);
    private final GameUserAuthorizationService authorization = mock(GameUserAuthorizationService.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final GameSessionServiceImpl service = new GameSessionServiceImpl(
            users, topics, vocabularies, learningProgress, sessions, configs,
            checkpoints, attempts, authorization, new GameSessionProgressResolverServiceImpl(), storage
    );

    private final Jwt jwt = Jwt.withTokenValue("local-test-token")
            .header("alg", "none").subject("sub-7").build();
    private User user;
    private Topic topic;
    private Vocabulary apple;
    private Vocabulary milk;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).role("learner").status("ACTIVE").build();
        topic = Topic.builder().id(1L).name("Food").active(true).build();
        apple = vocabulary(17L, "apple", "Táo", APPLE_KEY, 1);
        milk = vocabulary(18L, "milk", "Sữa", MILK_KEY, 2);
        when(storage.generatePresignedAudioGetUrl(APPLE_KEY)).thenReturn(APPLE_URL);
        when(storage.generatePresignedAudioGetUrl(MILK_KEY)).thenReturn(MILK_URL);
    }

    @Test
    void createsQuestionsWithMatchingAudioAndStoresOnlyObjectKeys() {
        prepareCreate();

        var response = service.createSession(1L, new CreateGameSessionRequest(GameMode.FREE_PLAY), jwt);

        var questions = response.questions().stream()
                .collect(Collectors.toMap(GameQuestionResponse::vocabularyId, question -> question));
        assertEquals(APPLE_URL, questions.get(17L).audioUrl());
        assertEquals(MILK_URL, questions.get(18L).audioUrl());
        var saved = ArgumentCaptor.forClass(GameSession.class);
        verify(sessions).saveAndFlush(saved.capture());
        var snapshots = saved.getValue().getQuestionSnapshot().stream()
                .collect(Collectors.toMap(GameQuestionSnapshot::vocabularyId, snapshot -> snapshot));
        assertEquals(APPLE_KEY, snapshots.get(17L).audioKey());
        assertEquals(MILK_KEY, snapshots.get(18L).audioKey());
        var json = JsonMapper.builder().build();
        var storedJson = json.readTree(json.writeValueAsString(snapshots.get(17L)));
        assertEquals(APPLE_KEY, storedJson.get("audioKey").asText());
        assertFalse(storedJson.has("audioUrl"));
        var questionJson = json.readTree(json.writeValueAsString(questions.get(17L)));
        assertEquals(APPLE_URL, questionJson.get("audioUrl").asText());
        assertFalse(questionJson.has("word"));
        assertFalse(questionJson.has("audioKey"));
    }

    @Test
    void createsPlayableSessionEvenWhenAudioIsMissingOrStoredAsAnUnsupportedUrl() {
        apple.setAudioUrl(null);
        milk.setAudioUrl("https://media.example.test/expired.mp3?signature=old");
        prepareCreate();

        var response = service.createSession(1L, new CreateGameSessionRequest(GameMode.FREE_PLAY), jwt);

        assertTrue(response.questions().stream().allMatch(question -> question.audioUrl() == null));
        var saved = ArgumentCaptor.forClass(GameSession.class);
        verify(sessions).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getQuestionSnapshot().stream()
                .allMatch(snapshot -> snapshot.audioKey() == null));
        verify(storage, never()).generatePresignedAudioGetUrl(any());
    }

    @Test
    void resumesFromStoredAudioWithoutReadingCurrentVocabularyOrChangingProgress() {
        var session = activeSession(APPLE_KEY, MILK_KEY);
        var attempt = new SessionAttempt();
        attempt.setId(1L);
        attempt.setSession(session);
        attempt.setVocabulary(apple);
        attempt.setIsCorrect(true);
        prepareResume(session, List.of(attempt));
        apple.setWord("banana");
        apple.setAudioUrl("vocabulary-audio/topic-1/banana-new.mp3");

        var response = service.getActiveSession(1L, jwt).orElseThrow();

        assertEquals(APPLE_URL, response.questions().get(0).audioUrl());
        assertEquals(MILK_URL, response.questions().get(1).audioUrl());
        assertEquals("COMPLETED", response.questions().get(0).status());
        assertEquals(1, response.questions().get(0).attemptsUsed());
        assertTrue(response.questions().get(0).correctlyAnswered());
        assertEquals(18L, response.currentVocabularyId());
        assertEquals(1, response.resolvedQuestions());
        assertEquals(3, response.remainingAttempts());
        verifyNoInteractions(vocabularies);
        verify(sessions, never()).save(any());
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void signsFreshUrlsOnEveryResumeWithoutPersistingThem() {
        var session = activeSession(APPLE_KEY, MILK_KEY);
        prepareResume(session, List.of());
        when(storage.generatePresignedAudioGetUrl(APPLE_KEY))
                .thenReturn(APPLE_URL, "https://media.example.test/apple.mp3?signature=renewed");

        var first = service.getActiveSession(1L, jwt).orElseThrow();
        var second = service.getActiveSession(1L, jwt).orElseThrow();

        assertNotEquals(first.questions().get(0).audioUrl(), second.questions().get(0).audioUrl());
        assertEquals(APPLE_KEY, session.getQuestionSnapshot().get(0).audioKey());
        verify(storage, times(2)).generatePresignedAudioGetUrl(APPLE_KEY);
        verify(sessions, never()).save(any());
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void loadsLegacyAudioInOneBatchOnlyWhenWordAndTopicStillMatch() {
        var session = activeSession(null, null);
        prepareResume(session, List.of());
        apple.setWord(" APPLE ");
        milk.setWord("bread");
        when(vocabularies.findAllById(anyList())).thenReturn(List.of(apple, milk));

        var response = service.getActiveSession(1L, jwt).orElseThrow();

        assertEquals(APPLE_URL, response.questions().get(0).audioUrl());
        assertNull(response.questions().get(1).audioUrl());
        verify(vocabularies).findAllById(List.of(17L, 18L));
        verify(storage, never()).generatePresignedAudioGetUrl(MILK_KEY);
        assertNull(session.getQuestionSnapshot().get(0).audioKey());
        verify(vocabularies, never()).save(any());
        verify(sessions, never()).save(any());
    }

    @Test
    void doesNotUseLegacyAudioFromAVocabularyMovedToAnotherTopic() {
        var session = activeSession(null, MILK_KEY);
        prepareResume(session, List.of());
        apple.setTopic(Topic.builder().id(2L).build());
        when(vocabularies.findAllById(List.of(17L))).thenReturn(List.of(apple));

        var response = service.getActiveSession(1L, jwt).orElseThrow();

        assertNull(response.questions().get(0).audioUrl());
        assertEquals(MILK_URL, response.questions().get(1).audioUrl());
    }

    @Test
    void legacyMissingVocabularyOrInvalidAudioDoesNotPreventResume() {
        var session = activeSession(null, null);
        prepareResume(session, List.of());
        milk.setAudioUrl("s3://bucket/milk.mp3");
        when(vocabularies.findAllById(anyList())).thenReturn(List.of(milk));

        var response = service.getActiveSession(1L, jwt).orElseThrow();

        assertEquals(2, response.questions().size());
        assertTrue(response.questions().stream().allMatch(question -> question.audioUrl() == null));
        verify(storage, never()).generatePresignedAudioGetUrl(any());
    }

    @Test
    void unsupportedSnapshotAudioDoesNotPreventResumeOrExposeAnOldUrl() {
        var session = activeSession("https://media.example.test/expired.mp3", MILK_KEY);
        prepareResume(session, List.of());

        var response = service.getActiveSession(1L, jwt).orElseThrow();

        assertNull(response.questions().get(0).audioUrl());
        assertEquals(MILK_URL, response.questions().get(1).audioUrl());
        verifyNoInteractions(vocabularies);
    }

    @Test
    void oldSnapshotJsonWithoutAudioStillLoadsWithBothJacksonVersions() throws Exception {
        String oldJson = """
                {"vocabularyId":17,"word":"apple","meaningVi":"Táo",
                 "scrambledLetters":["P","A","L","P","E"],"topicPosition":1}
                """;

        var jackson3 = JsonMapper.builder().build().readValue(oldJson, GameQuestionSnapshot.class);
        var jackson2 = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(oldJson, GameQuestionSnapshot.class);

        assertNull(jackson3.audioKey());
        assertNull(jackson2.audioKey());
        assertEquals("apple", jackson3.word());
        assertEquals(jackson3, jackson2);
    }

    @Test
    void newSnapshotJsonRoundTripsWithItsAudioKey() throws Exception {
        var original = snapshot(apple, APPLE_KEY);
        var jackson3 = JsonMapper.builder().build();
        var jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();

        assertEquals(original, jackson3.readValue(jackson3.writeValueAsString(original), GameQuestionSnapshot.class));
        assertEquals(original, jackson2.readValue(jackson2.writeValueAsString(original), GameQuestionSnapshot.class));
    }

    private void prepareCreate() {
        when(authorization.findAuthenticatedUser(jwt)).thenReturn(user);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(topics.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(topic));
        when(vocabularies.findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(1L))
                .thenReturn(List.of(apple, milk));
        when(configs.getEffectiveConfigs(1L)).thenReturn(new EffectiveGameConfig(3, 5, true, true));
        when(sessions.saveAndFlush(any(GameSession.class))).thenAnswer(call -> {
            GameSession session = call.getArgument(0);
            session.setId(70L);
            return session;
        });
    }

    private void prepareResume(GameSession session, List<SessionAttempt> savedAttempts) {
        when(authorization.requireActiveGameUser(jwt)).thenReturn(user);
        when(topics.findById(1L)).thenReturn(Optional.of(topic));
        when(sessions.findByUser_IdAndTopic_IdAndStatus(7L, 1L, "IN_PROGRESS"))
                .thenReturn(Optional.of(session));
        when(attempts.findBySession_IdOrderByIdAsc(70L)).thenReturn(savedAttempts);
    }

    private GameSession activeSession(String appleKey, String milkKey) {
        var session = new GameSession();
        session.setId(70L);
        session.setUser(user);
        session.setTopic(topic);
        session.setMode("FREE_PLAY");
        session.setStatus("IN_PROGRESS");
        session.setStartedAt(Instant.parse("2026-11-03T00:00:00Z"));
        session.setMaxAttemptsPerWord(3);
        session.setQuestionVocabIds(List.of(17L, 18L));
        session.setQuestionSnapshot(List.of(snapshot(apple, appleKey), snapshot(milk, milkKey)));
        return session;
    }

    private GameQuestionSnapshot snapshot(Vocabulary vocabulary, String key) {
        return new GameQuestionSnapshot(vocabulary.getId(), vocabulary.getWord(), vocabulary.getMeaningVi(),
                List.of("P", "A", "L", "P", "E"), vocabulary.getOrderNo(), key);
    }

    private Vocabulary vocabulary(Long id, String word, String meaning, String key, int position) {
        return Vocabulary.builder().id(id).topic(topic).word(word).meaningVi(meaning)
                .audioUrl(key).orderNo(position).active(true).build();
    }
}
