package com.kw.readwith.service.image;

import org.springframework.stereotype.Service;

@Service
public class CharacterImageQaService {

    public CharacterImageQaResult review(String imageUrl, boolean referenceSeed) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return CharacterImageQaResult.failed("{\"passed\":false,\"reason\":\"EMPTY_IMAGE_URL\"}", "EMPTY_IMAGE_URL");
        }

        String role = referenceSeed ? "REFERENCE_SEED" : "DERIVED_CHARACTER";
        String resultJson = "{\"passed\":true,\"mode\":\"BASIC_URL_CHECK\",\"role\":\"" + role + "\"}";
        return CharacterImageQaResult.passed(resultJson);
    }

    public record CharacterImageQaResult(boolean passed, String resultJson, String failureCode) {
        public static CharacterImageQaResult passed(String resultJson) {
            return new CharacterImageQaResult(true, resultJson, null);
        }

        public static CharacterImageQaResult failed(String resultJson, String failureCode) {
            return new CharacterImageQaResult(false, resultJson, failureCode);
        }
    }
}
