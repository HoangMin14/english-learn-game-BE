
package com.aws.alphabetuniverse.vocabulary.service.impl;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.dto.IpaLookupResponse;
import com.aws.alphabetuniverse.vocabulary.service.IpaLookupService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class IpaLookupServiceImpl implements IpaLookupService {

    private static final Logger log =
            LoggerFactory.getLogger(IpaLookupServiceImpl.class);

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://www.dictionaryapi.com")
            .build();

    @Value("${merriam-webster.api-key:}")
    private String apiKey;

    @Override
    @Transactional(readOnly = true)
    public IpaLookupResponse lookupIpa(String word, Jwt jwt) {

        // 1. Kiểm tra quyền Admin.
        requireAdmin(jwt);

        // 2. Kiểm tra từ đầu vào.
        if (word == null || word.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Word is required"
            );
        }

        String normalizedWord =
                word.trim().toLowerCase(Locale.ROOT);

        if (!normalizedWord.matches("[a-z]+(?:[-'][a-z]+)*")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid English word"
            );
        }

        // 3. Kiểm tra API key.
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Merriam-Webster API key is not configured"
            );
        }

        // 4. Gọi Merriam-Webster và đọc response dưới dạng String.
        // Cách này tránh lỗi không có HttpMessageConverter
        // khi server trả Content-Type: text/plain.
        String rawResponse;

        try {
            rawResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(
                                    "/api/v3/references/learners/json/{word}"
                            )
                            .queryParam("key", apiKey.trim())
                            .build(normalizedWord)
                    )
                    .retrieve()
                    .body(String.class);

        } catch (RestClientResponseException ex) {

            int status = ex.getStatusCode().value();

            log.warn(
                    "Merriam-Webster HTTP {}: {}",
                    status,
                    safePreview(ex.getResponseBodyAsString())
            );

            if (status == 429) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Merriam-Webster API rate limit reached"
                );
            }

            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Merriam-Webster rejected the API key "
                                + "or dictionary access"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Merriam-Webster API returned HTTP " + status
            );

        } catch (ResourceAccessException ex) {

            log.error(
                    "Cannot connect to Merriam-Webster: {}",
                    ex.getClass().getSimpleName()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot connect to Merriam-Webster API"
            );

        } catch (RestClientException ex) {

            log.error(
                    "Merriam-Webster request failed",
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Merriam-Webster request failed"
            );
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Merriam-Webster returned an empty response"
            );
        }

        // 5. Phân tích JSON và trích xuất IPA.
        String ipa;

        try {
            ipa = extractIpa(rawResponse);

        } catch (JsonProcessingException ex) {

            log.warn(
                    "Merriam-Webster returned a non-JSON or "
                            + "invalid JSON response: {}",
                    safePreview(rawResponse)
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Invalid response from Merriam-Webster; "
                            + "check backend console"
            );
        }

        if (ipa == null || ipa.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "IPA was not found for word: " + normalizedWord
            );
        }

        // 6. Trả kết quả.
        return new IpaLookupResponse(
                normalizedWord,
                formatIpa(ipa)
        );
    }

    // Chỉ Admin được tra cứu IPA.
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

    // Đọc cấu trúc:
    // hwi -> prs -> ipa
    private String extractIpa(String rawResponse)
            throws JsonProcessingException {

        JsonNode root = objectMapper.readTree(rawResponse);

        if (root == null || !root.isArray()) {
            return null;
        }

        for (JsonNode entry : root) {

            // API có thể trả danh sách gợi ý chính tả
            // dưới dạng chuỗi thay vì mục từ điển.
            JsonNode pronunciations =
                    entry.path("hwi").path("prs");

            if (!pronunciations.isArray()) {
                continue;
            }

            for (JsonNode pronunciation : pronunciations) {

                JsonNode ipaNode = pronunciation.path("ipa");

                if (ipaNode.isTextual()
                        && !ipaNode.asText().isBlank()) {

                    return ipaNode.asText().trim();
                }
            }
        }

        return null;
    }

    // Lọc API key trước khi ghi nội dung phản hồi vào log.
    private String safePreview(String value) {

        if (value == null || value.isBlank()) {
            return "<empty>";
        }

        String result = value;

        if (apiKey != null && !apiKey.isBlank()) {
            result = result.replace(apiKey, "[REDACTED]");
        }

        result = result.replaceAll("[\\r\\n\\t]+", " ");

        int maxLength = 250;

        if (result.length() > maxLength) {
            result = result.substring(0, maxLength) + "...";
        }

        return result;
    }

    private String formatIpa(String ipa) {

        String result = ipa.trim();

        if (!result.startsWith("/")) {
            result = "/" + result;
        }

        if (!result.endsWith("/")) {
            result = result + "/";
        }

        return result;
    }
}
