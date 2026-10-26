package com.aws.alphabetuniverse.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3StorageService {

    // Upload ảnh Topic
    String uploadTopicImage(MultipartFile file);

    // Tạo URL tạm thời để xem ảnh Topic
    String generatePresignedGetUrl(String key);

    // Upload MP3 từ vựng
    String uploadVocabularyAudio(
            Long topicId,
            String word,
            byte[] audioBytes
    );

    // Tạo URL tạm thời để nghe MP3
    String generatePresignedAudioGetUrl(String key);
}