package com.kw.readwith.service.image;

import lombok.RequiredArgsConstructor;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class OpenAiImageBatchClient {

    private static final String FILES_URL = "https://api.openai.com/v1/files";
    private static final String BATCHES_URL = "https://api.openai.com/v1/batches";
    private static final String IMAGE_EDIT_ENDPOINT = "/v1/images/edits";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public byte[] buildImageEditInput(List<OpenAiBatchImageEditRequest> requests) {
        StringBuilder jsonl = new StringBuilder();
        for (OpenAiBatchImageEditRequest request : requests) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.model());
            body.put("images", List.of(Map.of("image_url", request.referenceImageUrl())));
            body.put("prompt", request.prompt());
            body.put("size", request.size());
            if (request.quality() != null && !request.quality().isBlank()) {
                body.put("quality", request.quality());
            }
            body.put("output_format", "png");
            body.put("n", 1);

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("custom_id", request.customId());
            line.put("method", "POST");
            line.put("url", IMAGE_EDIT_ENDPOINT);
            line.put("body", body);
            try {
                jsonl.append(objectMapper.writeValueAsString(line)).append('\n');
            } catch (JacksonException e) {
                throw new IllegalStateException("Failed to serialize OpenAI Batch input.", e);
            }
        }
        return jsonl.toString().getBytes(StandardCharsets.UTF_8);
    }

    public OpenAiBatchSubmission submit(byte[] inputJsonl,
                                        String fileName,
                                        String idempotencyKey,
                                        Map<String, String> metadata) {
        requireApiKey();
        if (inputJsonl == null || inputJsonl.length == 0) {
            throw new IllegalArgumentException("OpenAI Batch input JSONL is required.");
        }

        String inputFileId = uploadInputFile(inputJsonl, fileName);
        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", idempotencyKey);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("input_file_id", inputFileId);
        request.put("endpoint", IMAGE_EDIT_ENDPOINT);
        request.put("completion_window", "24h");
        request.put("metadata", metadata);

        ResponseEntity<String> response = restTemplate.exchange(
                BATCHES_URL,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );
        JsonNode root = parseBody(response.getBody(), "OpenAI Batch creation");
        String batchId = requiredText(root, "id", "OpenAI Batch creation response has no id.");
        String status = requiredText(root, "status", "OpenAI Batch creation response has no status.");
        return new OpenAiBatchSubmission(inputFileId, batchId, normalizeStatus(status));
    }

    public OpenAiBatchStatus retrieve(String batchId) {
        requireApiKey();
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("OpenAI Batch id is required.");
        }

        ResponseEntity<String> response = restTemplate.exchange(
                BATCHES_URL + "/" + batchId,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                String.class
        );
        JsonNode root = parseBody(response.getBody(), "OpenAI Batch retrieval");
        JsonNode requestCounts = root.path("request_counts");
        return new OpenAiBatchStatus(
                requiredText(root, "id", "OpenAI Batch response has no id."),
                normalizeStatus(requiredText(root, "status", "OpenAI Batch response has no status.")),
                optionalText(root, "output_file_id"),
                optionalText(root, "error_file_id"),
                requestCounts.path("total").asInt(0),
                requestCounts.path("completed").asInt(0),
                requestCounts.path("failed").asInt(0),
                nullableJson(root.path("errors"))
        );
    }

    public void streamResults(String fileId, Consumer<OpenAiBatchImageResult> consumer) {
        requireApiKey();
        if (fileId == null || fileId.isBlank()) {
            return;
        }

        restTemplate.execute(
                FILES_URL + "/" + fileId + "/content",
                HttpMethod.GET,
                request -> request.getHeaders().setBearerAuth(apiKey),
                response -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.isBlank()) {
                                consumer.accept(parseResult(line));
                            }
                        }
                    }
                    return null;
                }
        );
    }

    private String uploadInputFile(byte[] inputJsonl, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("purpose", "batch");
        body.add("file", new ByteArrayResource(inputJsonl) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        ResponseEntity<String> response = restTemplate.exchange(
                FILES_URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        JsonNode root = parseBody(response.getBody(), "OpenAI Batch file upload");
        return requiredText(root, "id", "OpenAI Batch file upload response has no id.");
    }

    private OpenAiBatchImageResult parseResult(String line) {
        JsonNode root = parseBody(line, "OpenAI Batch output line");
        String customId = requiredText(root, "custom_id", "OpenAI Batch output has no custom_id.");
        JsonNode topLevelError = root.path("error");
        if (!topLevelError.isMissingNode() && !topLevelError.isNull()) {
            return failedResult(customId, 0, null, topLevelError);
        }

        JsonNode response = root.path("response");
        int statusCode = response.path("status_code").asInt(0);
        String requestId = optionalText(response, "request_id");
        JsonNode responseBody = response.path("body");
        if (statusCode < 200 || statusCode >= 300) {
            return failedResult(customId, statusCode, requestId, responseBody.path("error"));
        }

        JsonNode data = responseBody.path("data");
        String b64Json = data.isArray() && !data.isEmpty()
                ? data.get(0).path("b64_json").asText(null)
                : null;
        if (b64Json == null || b64Json.isBlank()) {
            return new OpenAiBatchImageResult(
                    customId,
                    statusCode,
                    requestId,
                    null,
                    "MISSING_IMAGE_DATA",
                    "OpenAI Batch image response has no b64_json."
            );
        }

        try {
            return new OpenAiBatchImageResult(
                    customId,
                    statusCode,
                    requestId,
                    Base64.getDecoder().decode(b64Json.replaceAll("\\s+", "")),
                    null,
                    null
            );
        } catch (IllegalArgumentException e) {
            return new OpenAiBatchImageResult(
                    customId,
                    statusCode,
                    requestId,
                    null,
                    "INVALID_IMAGE_DATA",
                    "OpenAI Batch image response contains invalid base64 data."
            );
        }
    }

    private OpenAiBatchImageResult failedResult(String customId,
                                                int statusCode,
                                                String requestId,
                                                JsonNode error) {
        String code = error.path("code").asText("OPENAI_BATCH_REQUEST_FAILED");
        String message = error.path("message").asText("OpenAI Batch request failed.");
        return new OpenAiBatchImageResult(customId, statusCode, requestId, null, code, message);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode parseBody(String body, String operation) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(operation + " returned an empty response.");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(operation + " returned invalid JSON.", e);
        }
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private String nullableJson(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private String normalizeStatus(String status) {
        return status.toLowerCase(Locale.ROOT);
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }
    }

    public record OpenAiBatchSubmission(String inputFileId, String batchId, String status) {
    }

    public record OpenAiBatchImageEditRequest(
            String customId,
            String model,
            String referenceImageUrl,
            String prompt,
            String size,
            String quality
    ) {
    }

    public record OpenAiBatchStatus(
            String batchId,
            String status,
            String outputFileId,
            String errorFileId,
            int totalCount,
            int completedCount,
            int failedCount,
            String errorsJson
    ) {
        public boolean isTerminal() {
            return "completed".equals(status)
                    || "failed".equals(status)
                    || "expired".equals(status)
                    || "cancelled".equals(status);
        }
    }

    public record OpenAiBatchImageResult(
            String customId,
            int statusCode,
            String requestId,
            byte[] imageData,
            String errorCode,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return imageData != null && imageData.length > 0 && errorCode == null;
        }
    }
}
