
package com.aws.alphabetuniverse.topic.service.impl;

import com.aws.alphabetuniverse.storage.service.S3StorageService;
import com.aws.alphabetuniverse.topic.dto.TopicRequest;
import com.aws.alphabetuniverse.topic.dto.TopicResponse;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.topic.service.TopicService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;

    // =========================
    // 1. CHECK ADMIN
    // =========================

    private void requireAdmin(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        String cognitoSub = jwt.getClaimAsString("sub");

        if (cognitoSub == null || cognitoSub.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity"
            );
        }

        User user = userRepository.findByCognitoSub(cognitoSub)
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

    // =========================
    // 2. CONVERT ENTITY TO DTO
    // =========================

    private TopicResponse toResponse(Topic topic) {

        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .iconUrl(topic.getIconUrl())
                .imageUrl(
                        topic.getImageKey() == null
                                || topic.getImageKey().isBlank()
                                ? null
                                : s3StorageService.generatePresignedGetUrl(
                                topic.getImageKey()
                        )
                )
                .orderNo(topic.getOrderNo())
                .isActive(topic.getActive())
                .build();
    }

    // =========================
    // 3. GET ALL ACTIVE TOPICS
    // =========================

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getAllTopics() {

        return topicRepository
                .findByActiveTrueOrderByOrderNoAscIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================
    // 4. GET TOPIC BY ID
    // =========================

    @Override
    @Transactional(readOnly = true)
    public TopicResponse getTopicById(Long topicId) {

        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        return toResponse(topic);
    }

    // =========================
    // 5. CREATE TOPIC - ADMIN
    // =========================

    @Override
    @Transactional
    public TopicResponse createTopic(
            TopicRequest request,
            Jwt jwt
    ) {

        // Chỉ Admin được tạo Topic
        requireAdmin(jwt);

        if (request == null
                || request.getName() == null
                || request.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Topic name is required"
            );
        }

        String name = request.getName().trim();

        // Kiểm tra tên Topic trùng
        if (topicRepository.existsByNameIgnoreCase(name)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Topic name already exists"
            );
        }

        // Khi tạo mới, ảnh là bắt buộc
        MultipartFile image = request.getImage();

        if (image == null || image.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Topic image is required"
            );
        }

        // Upload ảnh lên S3, nhận về object key
        String imageKey =
                s3StorageService.uploadTopicImage(image);

        // Tự động xác định thứ tự Topic mới
        Integer maxOrderNo = topicRepository.findMaxOrderNo();

        int nextOrderNo =
                (maxOrderNo == null ? 0 : maxOrderNo) + 1;

        String description = request.getDescription();

        if (description != null) {
            description = description.trim();

            if (description.isEmpty()) {
                description = null;
            }
        }

        // Lưu thông tin Topic vào PostgreSQL
        Topic topic = Topic.builder()
                .name(name)
                .description(description)
                .imageKey(imageKey)
                .orderNo(nextOrderNo)
                .active(true)
                .build();

        Topic savedTopic = topicRepository.saveAndFlush(topic);

        // Trả DTO có URL xem ảnh
        return toResponse(savedTopic);
    }

    // =========================
    // 6. UPDATE TOPIC - ADMIN
    // =========================

    @Override
    @Transactional
    public TopicResponse updateTopic(
            Long topicId,
            TopicRequest request,
            Jwt jwt
    ) {
        // 1. Chỉ Admin được cập nhật
        requireAdmin(jwt);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Update data is required"
            );
        }

        // 2. Tìm Topic hiện tại
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // 3. Xác định trường nào thực sự được cập nhật
        String name = request.getName();
        String description = request.getDescription();
        MultipartFile image = request.getImage();

        boolean updateName = name != null && !name.isBlank();
        boolean updateDescription =
                description != null && !description.isBlank();
        boolean updateImage = image != null && !image.isEmpty();

        // 4. Phải có ít nhất một trường cần cập nhật
        if (!updateName && !updateDescription && !updateImage) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one field must be provided for update"
            );
        }

        // 5. Chỉ kiểm tra trùng tên nếu Admin nhập tên mới
        if (updateName) {
            name = name.trim();

            if (topicRepository.existsByNameIgnoreCaseAndIdNot(
                    name, topicId
            )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Topic name already exists"
                );
            }

            topic.setName(name);
        }

        // 6. Chỉ cập nhật mô tả nếu Admin nhập mô tả mới
        if (updateDescription) {
            topic.setDescription(description.trim());
        }

        // 7. Chỉ upload ảnh khi Admin chọn ảnh mới
        if (updateImage) {
            String newImageKey =
                    s3StorageService.uploadTopicImage(image);

            topic.setImageKey(newImageKey);
        }

        // 8. Lưu thay đổi
        Topic updatedTopic = topicRepository.saveAndFlush(topic);

        return toResponse(updatedTopic);
    }

    @Override
    public void deleteTopic(Long topicId, Jwt jwt) {
        // 1. Chỉ Admin được phép xóa Topic
        requireAdmin(jwt);

        // 2. Tìm Topic theo ID
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // 3. Xóa mềm: nếu đã ẩn thì không cần cập nhật lại
        if (Boolean.TRUE.equals(topic.getActive())) {
            topic.setActive(false);
            topicRepository.save(topic);
        }
    }

    @Override
    public List<TopicResponse> getAllTopicsForAdmin(Jwt jwt) {
        // Chỉ Admin được xem toàn bộ Topic
        requireAdmin(jwt);

        // Lấy tất cả Topic, kể cả Topic đã bị ẩn
        return topicRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("orderNo"),
                                org.springframework.data.domain.Sort.Order.asc("id")
                        )
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TopicResponse restoreTopic(Long topicId, Jwt jwt) {
        // 1. Chỉ Admin được khôi phục Topic
        requireAdmin(jwt);

        // 2. Tìm Topic, kể cả Topic đã bị ẩn
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Topic not found"
                        )
                );

        // 3. Khôi phục Topic
        if (!Boolean.TRUE.equals(topic.getActive())) {
            topic.setActive(true);
            topic = topicRepository.saveAndFlush(topic);
        }

        // 4. Trả về thông tin Topic
        return toResponse(topic);
    }

}
