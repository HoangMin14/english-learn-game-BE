package com.aws.alphabetuniverse.storage.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3VocabularyAudioTest {

    @Test
    void regeneratingTheSameWordUsesANewKeyInsteadOfOverwritingSessionAudio() {
        var client = mock(S3Client.class);
        var presigner = mock(S3Presigner.class);
        var service = new S3StorageServiceImpl(client, presigner, "local-test-bucket");
        byte[] mp3 = new byte[]{73, 68, 51};

        String first = service.uploadVocabularyAudio(1L, " Apple ", mp3);
        String second = service.uploadVocabularyAudio(1L, "apple", mp3);

        assertNotEquals(first, second);
        assertTrue(first.matches("vocabulary-audio/topic-1/apple-[0-9a-f-]{36}\\.mp3"));
        assertTrue(second.matches("vocabulary-audio/topic-1/apple-[0-9a-f-]{36}\\.mp3"));
        var requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(requests.capture(), any(RequestBody.class));
        assertEquals(first, requests.getAllValues().get(0).key());
        assertEquals(second, requests.getAllValues().get(1).key());
        assertTrue(requests.getAllValues().stream()
                .allMatch(request -> "audio/mpeg".equals(request.contentType())));
        verifyNoInteractions(presigner);
    }

    @Test
    void oldAndNewAudioKeysCanBothBeSignedLocallyForFifteenMinutes() {
        // Static test credentials make presigning local; no AWS request is sent.
        try (var presigner = S3Presigner.builder().region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            var client = mock(S3Client.class);
            var service = new S3StorageServiceImpl(client, presigner, "local-test-bucket");

            for (String key : new String[]{"vocabulary-audio/topic-1/apple.mp3",
                    "vocabulary-audio/topic-1/apple-new-version.mp3"}) {
                String url = service.generatePresignedAudioGetUrl(key);
                assertTrue(url.contains("/" + key));
                assertTrue(url.contains("X-Amz-Expires=900"));
                assertTrue(url.contains("X-Amz-Signature="));
            }
            verifyNoInteractions(client);
        }
    }
}
