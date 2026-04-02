package com.kw.readwith.dto.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.dto.common.LocatorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("legacy locator field is accepted as startLocator")
    void saveProgressRequestAcceptsLegacyLocatorField() throws Exception {
        String json = """
                {
                  "bookId": 1,
                  "locator": {
                    "chapterIndex": 2,
                    "blockIndex": 3,
                    "offset": 4
                  }
                }
                """;

        SaveProgressRequestDTO dto = objectMapper.readValue(json, SaveProgressRequestDTO.class);

        assertThat(dto.getBookId()).isEqualTo(1L);
        assertThat(dto.getStartLocator()).isEqualTo(dto.getLocator());
        assertThat(dto.getStartLocator().getChapterIndex()).isEqualTo(2);
        assertThat(dto.getStartLocator().getBlockIndex()).isEqualTo(3);
        assertThat(dto.getStartLocator().getOffset()).isEqualTo(4);
    }

    @Test
    @DisplayName("progress response exposes both startLocator and legacy locator")
    void progressResponseSerializesLegacyLocatorAlias() throws Exception {
        LocatorDTO locator = LocatorDTO.builder()
                .chapterIndex(2)
                .blockIndex(3)
                .offset(4)
                .build();

        ProgressResponseDTO response = ProgressResponseDTO.builder()
                .bookId(1L)
                .startLocator(locator)
                .startTxtOffset(120)
                .locatorVersion("locator-v1")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.path("startLocator").path("chapterIndex").asInt()).isEqualTo(2);
        assertThat(json.path("locator").path("chapterIndex").asInt()).isEqualTo(2);
        assertThat(json.path("startTxtOffset").asInt()).isEqualTo(120);
        assertThat(json.path("locatorVersion").asText()).isEqualTo("locator-v1");
    }
}
