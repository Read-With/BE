package com.kw.readwith.service.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OpenAiImageBatchClient")
class OpenAiImageBatchClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenAiImageBatchClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        objectMapper = new ObjectMapper();
        client = new OpenAiImageBatchClient(restTemplate, objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    @Test
    @DisplayName("builds GPT Image 2 edit lines with one shared reference URL")
    void buildImageEditInput_usesSharedCanonicalReference() throws Exception {
        String referenceUrl = "https://cdn.readwith.store/character-images/20/reference/slot-1.png";

        byte[] input = client.buildImageEditInput(List.of(
                new OpenAiImageBatchClient.OpenAiBatchImageEditRequest(
                        "character-image-asset-10",
                        "gpt-image-2",
                        referenceUrl,
                        "series lock: Holmes",
                        "1024x1024",
                        "medium"
                ),
                new OpenAiImageBatchClient.OpenAiBatchImageEditRequest(
                        "character-image-asset-11",
                        "gpt-image-2",
                        referenceUrl,
                        "series lock: Watson",
                        "1024x1024",
                        "medium"
                )
        ));

        String[] lines = new String(input, StandardCharsets.UTF_8).trim().split("\\R");
        assertThat(lines).hasSize(2);
        for (String line : lines) {
            var root = objectMapper.readTree(line);
            assertThat(root.path("url").asText()).isEqualTo("/v1/images/edits");
            assertThat(root.path("body").path("model").asText()).isEqualTo("gpt-image-2");
            assertThat(root.path("body").path("images").get(0).path("image_url").asText())
                    .isEqualTo(referenceUrl);
            assertThat(root.path("body").has("input_fidelity")).isFalse();
        }
    }

    @Test
    @DisplayName("uploads JSONL and creates an image edit Batch")
    void submit_createsImageEditBatch() {
        server.expect(requestTo("https://api.openai.com/v1/files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"file-input\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/batches"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":\"batch-123\",\"status\":\"validating\"}",
                        MediaType.APPLICATION_JSON
                ));

        OpenAiImageBatchClient.OpenAiBatchSubmission result = client.submit(
                "{\"custom_id\":\"character-image-asset-1\"}\n".getBytes(StandardCharsets.UTF_8),
                "fanout.jsonl",
                "run-1",
                Map.of("book_id", "20")
        );

        assertThat(result.inputFileId()).isEqualTo("file-input");
        assertThat(result.batchId()).isEqualTo("batch-123");
        assertThat(result.status()).isEqualTo("validating");
        server.verify();
    }

    @Test
    @DisplayName("streams successful and failed image results by custom id")
    void streamResults_matchesCustomIds() {
        String encodedImage = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String output = "{\"custom_id\":\"character-image-asset-10\",\"response\":{\"status_code\":200,\"request_id\":\"req-1\",\"body\":{\"data\":[{\"b64_json\":\""
                + encodedImage
                + "\"}]}},\"error\":null}\n"
                + "{\"custom_id\":\"character-image-asset-11\",\"response\":{\"status_code\":429,\"request_id\":\"req-2\",\"body\":{\"error\":{\"code\":\"rate_limit\",\"message\":\"slow down\"}}},\"error\":null}\n";
        server.expect(requestTo("https://api.openai.com/v1/files/file-output/content"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(output, new MediaType("application", "jsonl")));

        List<OpenAiImageBatchClient.OpenAiBatchImageResult> results = new ArrayList<>();
        client.streamResults("file-output", results::add);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).customId()).isEqualTo("character-image-asset-10");
        assertThat(results.get(0).imageData()).containsExactly(1, 2, 3);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).customId()).isEqualTo("character-image-asset-11");
        assertThat(results.get(1).errorCode()).isEqualTo("rate_limit");
        assertThat(results.get(1).isSuccess()).isFalse();
        server.verify();
    }
}
