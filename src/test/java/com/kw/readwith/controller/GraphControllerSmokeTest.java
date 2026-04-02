package com.kw.readwith.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.User;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "cloud.aws.credentials.accessKey=test",
        "cloud.aws.credentials.secretKey=test",
        "cloud.aws.region.static=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "cloud.aws.path.original=original",
        "cloud.aws.path.metadata=metadata",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "spring.ai.openai.api-key=test"
})
class GraphControllerSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
        accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("legacy and locator graph queries both return the same seeded event")
    void graphEndpointsRespondForLegacyAndLocatorQueries() throws Exception {
        JsonNode fineLegacy = readResult(
                mockMvc.perform(get("/api/v2/graph/fine")
                                .param("bookId", "1")
                                .param("chapterIdx", "1")
                                .param("eventIdx", "3")
                                .header("Authorization", "Bearer " + accessToken)
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode fineLocator = readResult(
                mockMvc.perform(get("/api/v2/graph/fine")
                                .param("bookId", "1")
                                .param("chapterIndex", "1")
                                .param("blockIndex", "0")
                                .param("offset", "2600")
                                .header("Authorization", "Bearer " + accessToken)
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode macroLocator = readResult(
                mockMvc.perform(get("/api/v2/graph/macro")
                                .param("bookId", "1")
                                .param("chapterIndex", "1")
                                .param("blockIndex", "0")
                                .param("offset", "2600")
                                .header("Authorization", "Bearer " + accessToken)
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(fineLegacy.path("event").path("eventId").asText()).isEqualTo("ch1-e3");
        assertThat(fineLocator.path("event").path("eventId").asText()).isEqualTo("ch1-e3");
        assertThat(fineLocator.path("characters")).isNotEmpty();
        assertThat(fineLocator.path("characters").get(0).path("id").asLong()).isPositive();
        assertThat(macroLocator.path("userCurrentChapter").asInt()).isEqualTo(1);
        assertThat(macroLocator.path("characters")).isNotEmpty();
    }

    private JsonNode readResult(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("isSuccess").asBoolean()).isTrue();
        return root.path("result");
    }
}
