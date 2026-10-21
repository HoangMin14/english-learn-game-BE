
package com.aws.alphabetuniverse.vocabulary.service.impl;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.dto.IpaLookupResponse;
import com.aws.alphabetuniverse.vocabulary.service.IpaLookupService;

import lombok.RequiredArgsConstructor;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IpaLookupServiceImpl implements IpaLookupService {

    private final UserRepository userRepository;
    private final RestClient.Builder restClientBuilder;

    // Kiểm tra quyền Admin
    private void requireAdmin(Jwt jwt) {
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        User user = userRepository.findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        if (user.getRole() == null
                || !user.getRole().equalsIgnoreCase("admin")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin access required"
            );
        }
    }

    @Override
    public IpaLookupResponse lookupIpa(String word, Jwt jwt) {
        requireAdmin(jwt);

        if (word == null || word.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Word is required"
            );
        }

        String normalizedWord = word.trim().toLowerCase(Locale.ROOT);

        if (normalizedWord.length() > 100
                || !normalizedWord.matches(
                "[a-z]+(?:[-'][a-z]+)*")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid English word"
            );
        }

        RestClient client = restClientBuilder
                .baseUrl("https://api.dictionaryapi.dev/api/v2")
                .build();

        List<Map<String, Object>> entries;

        try {
            entries = client.get()
                    .uri("/entries/en/{word}", normalizedWord)
                    .retrieve()
                    .body(new ParameterizedTypeReference<
                            List<Map<String, Object>>>() {});

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "IPA not found for word: " + normalizedWord
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Dictionary API returned an error"
            );

        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot connect to dictionary API"
            );

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot read dictionary API response"
            );
        }

        if (entries == null || entries.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "IPA not found"
            );
        }

        // Ưu tiên trường phonetic cấp cao nhất
        for (Map<String, Object> entry : entries) {
            Object value = entry.get("phonetic");

            if (value instanceof String text && !text.isBlank()) {
                return new IpaLookupResponse(
                        normalizedWord,
                        normalizeIpa(text)
                );
            }
        }

        // Nếu không có phonetic, tìm trong phonetics[].text
        for (Map<String, Object> entry : entries) {
            Object value = entry.get("phonetics");

            if (value instanceof List<?> phonetics) {
                for (Object item : phonetics) {
                    if (item instanceof Map<?, ?> phonetic) {
                        Object text = phonetic.get("text");

                        if (text instanceof String ipa
                                && !ipa.isBlank()) {
                            return new IpaLookupResponse(
                                    normalizedWord,
                                    normalizeIpa(ipa)
                            );
                        }
                    }
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No phonetic transcription available"
        );
    }

    private String normalizeIpa(String value) {
        String ipa = value.trim();

        if ((ipa.startsWith("/") && ipa.endsWith("/"))
                || (ipa.startsWith("[") && ipa.endsWith("]"))) {
            return ipa;
        }

        return "/" + ipa + "/";
    }
}
