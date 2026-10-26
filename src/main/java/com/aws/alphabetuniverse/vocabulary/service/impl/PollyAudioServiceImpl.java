package com.aws.alphabetuniverse.vocabulary.service.impl;

import com.aws.alphabetuniverse.vocabulary.service.PollyAudioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class PollyAudioServiceImpl implements PollyAudioService {

    private final PollyClient pollyClient;

    @Override
    public byte[] generateAudio(String word) {

        if (word == null || word.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Word is required"
            );
        }

        SynthesizeSpeechRequest request =
                SynthesizeSpeechRequest.builder()
                        .text(word.trim())
                        .voiceId(VoiceId.JOANNA)
                        .engine(Engine.STANDARD)
                        .outputFormat(OutputFormat.MP3)
                        .build();

        try (
                ResponseInputStream<SynthesizeSpeechResponse> response =
                        pollyClient.synthesizeSpeech(request)
        ) {
            byte[] audioBytes = response.readAllBytes();

            if (audioBytes.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Amazon Polly returned empty audio"
                );
            }

            return audioBytes;

        } catch (IOException ex) {
            log.error("Failed to read generated audio", ex);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to read generated audio"
            );

        } catch (PollyException ex) {

            String errorCode = ex.awsErrorDetails() != null
                    ? ex.awsErrorDetails().errorCode()
                    : "UNKNOWN";

            String errorMessage = ex.awsErrorDetails() != null
                    ? ex.awsErrorDetails().errorMessage()
                    : ex.getMessage();

            log.error(
                    "Polly service error: HTTP status={}, errorCode={}, message={}",
                    ex.statusCode(),
                    errorCode,
                    errorMessage,
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Amazon Polly could not generate audio. Check backend logs.",
                    ex
            );

        } catch (SdkException ex) {

            log.error(
                    "AWS SDK error while calling Polly: exception={}, message={}",
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Amazon Polly request failed. Check backend logs.",
                    ex
            );
        }
    }
}