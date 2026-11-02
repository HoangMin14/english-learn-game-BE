package com.aws.alphabetuniverse.user.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;

public final class DisplayNameNormalizer {

    private static final int MAX_CODE_POINTS = 100;

    private DisplayNameNormalizer() {
    }

    public static String normalizeRequired(String displayName) {
        String normalized = normalizeFromCognito(displayName);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Display name must not exceed 100 characters");
        }
        return normalized;
    }

    public static String normalizeFromCognito(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = Normalizer.normalize(displayName, Normalizer.Form.NFC).strip();
        // Preserve legacy names during Sync; enforce the limit when creating or changing a name.
        return normalized.isBlank() ? null : normalized;
    }
}
