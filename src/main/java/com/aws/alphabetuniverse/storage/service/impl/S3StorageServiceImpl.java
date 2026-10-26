package com.aws.alphabetuniverse.storage.service.impl;

import com.aws.alphabetuniverse.storage.service.S3StorageService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
public class S3StorageServiceImpl implements S3StorageService {

    private static final String IMAGE_PREFIX = "topic-images/";
    private static final String AUDIO_PREFIX = "vocabulary-audio/";

    private static final long MAX_IMAGE_SIZE = 2L * 1024 * 1024;
    private static final long MAX_AUDIO_SIZE = 5L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3StorageServiceImpl(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    // =====================================================
    // 1. UPLOAD ẢNH TOPIC
    // =====================================================

    @Override
    public String uploadTopicImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Topic image is required"
            );
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image size must not exceed 2 MB"
            );
        }

        String contentType = file.getContentType();
        String extension = getImageExtension(contentType);

        String key = IMAGE_PREFIX + UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType.toLowerCase(Locale.ROOT))
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );

            return key;

        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read topic image",
                    exception
            );
        }
    }

    // =====================================================
    // 2. TẠO URL ẢNH TOPIC
    // =====================================================

    @Override
    public String generatePresignedGetUrl(String key) {

        if (key == null || key.isBlank()) {
            return null;
        }

        if (!key.startsWith(IMAGE_PREFIX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic image key"
            );
        }

        return generatePresignedUrl(key);
    }

    // =====================================================
    // 3. UPLOAD ÂM THANH TỪ VỰNG
    // =====================================================


    @Override
    public String uploadVocabularyAudio(
            Long topicId,
            String word,
            byte[] audioBytes
    ) {
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic ID"
            );
        }

        if (word == null || word.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "English word is required"
            );
        }

        if (audioBytes == null || audioBytes.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Audio data is required"
            );
        }

        if (audioBytes.length > MAX_AUDIO_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Audio size must not exceed 5 MB"
            );
        }

        // Chuẩn hóa tên file từ từ tiếng Anh
        String fileName = word.trim()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9-]", "");

        if (fileName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid English word for audio filename"
            );
        }

        // Tạo S3 object key theo Topic và từ vựng
        String key = AUDIO_PREFIX
                + "topic-" + topicId + "/"
                + fileName + ".mp3";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("audio/mpeg")
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(audioBytes)
        );

        // Trả về object key để lưu vào DB
        return key;
    }


    // =====================================================
    // 4. TẠO URL NGHE ÂM THANH TỪ VỰNG
    // =====================================================

    @Override
    public String generatePresignedAudioGetUrl(String key) {

        if (key == null || key.isBlank()) {
            return null;
        }

        if (!key.startsWith(AUDIO_PREFIX)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid vocabulary audio key"
            );
        }

        return generatePresignedUrl(key);
    }

    // =====================================================
    // 5. HÀM DÙNG CHUNG TẠO PRESIGNED URL
    // =====================================================

    private String generatePresignedUrl(String key) {

        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();
    }

    // =====================================================
    // 6. KIỂM TRA ĐỊNH DẠNG ẢNH
    // =====================================================

    private String getImageExtension(String contentType) {

        if (contentType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image content type is required"
            );
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, PNG and WEBP images are allowed"
            );
        };
    }
}