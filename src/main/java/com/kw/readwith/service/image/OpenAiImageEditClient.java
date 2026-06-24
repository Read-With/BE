package com.kw.readwith.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.config.CharacterImageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiImageEditClient {

    private static final String IMAGE_EDIT_URL = "https://api.openai.com/v1/images/edits";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CharacterImageProperties imageProperties;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public GeneratedCharacterImage generate(byte[] referenceImage, String prompt) {
        if (referenceImage == null || referenceImage.length == 0) {
            throw new IllegalArgumentException("Reference image is required.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }

        String model = resolveEditModel();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("prompt", prompt);
        body.add("image[]", new ByteArrayResource(referenceImage) {
            @Override
            public String getFilename() {
                return "reference.png";
            }
        });

        String size = imageProperties.getWidth() + "x" + imageProperties.getHeight();
        body.add("size", size);

        String quality = normalize(imageProperties.getQuality());
        if (quality != null) {
            body.add("quality", quality);
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(IMAGE_EDIT_URL, HttpMethod.POST, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                throw new IllegalStateException("OpenAI image edit response has no data.");
            }

            String b64Json = dataNode.get(0).path("b64_json").asText(null);
            if (b64Json == null || b64Json.isBlank()) {
                throw new IllegalStateException("OpenAI image edit response has no b64_json.");
            }

            String requestId = response.getHeaders().getFirst("x-request-id");
            return new GeneratedCharacterImage(
                    Base64.getDecoder().decode(b64Json.replaceAll("\\s+", "")),
                    model,
                    prompt,
                    sha256(prompt),
                    requestId
            );
        } catch (Exception e) {
            log.error("Failed to parse OpenAI image edit response.", e);
            throw new IllegalStateException("Failed to parse OpenAI image edit response.", e);
        }
    }

    private String resolveEditModel() {
        String editModel = normalize(imageProperties.getEditModel());
        if (editModel != null) {
            return editModel;
        }
        String model = normalize(imageProperties.getModel());
        return model != null ? model : "gpt-image-1";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
