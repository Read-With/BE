package com.kw.readwith.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.dto.admin.CharacterDTO;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.dto.book.ChapterPovSummaryDTO;
import com.kw.readwith.dto.bookmark.BookmarkResponseDTO;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import com.kw.readwith.dto.manifest.BookManifestDTO;
import com.kw.readwith.dto.manifest.CharacterManifestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("character upload DTO accepts camelCase and legacy aliases for isMainCharacter")
    void characterDtoAcceptsMainCharacterKeys() throws Exception {
        CharacterDTO camelCase = objectMapper.readValue("""
                {
                  "characterId": "1",
                  "commonName": "Victor",
                  "isMainCharacter": true,
                  "descriptions": {
                    "ko": "wizard"
                  },
                  "portraitPrompt": "prompt"
                }
                """, CharacterDTO.class);

        CharacterDTO snakeCase = objectMapper.readValue("""
                {
                  "characterId": "1",
                  "commonName": "Victor",
                  "main_character": true,
                  "descriptions": {
                    "ko": "wizard"
                  },
                  "portraitPrompt": "prompt"
                }
                """, CharacterDTO.class);

        CharacterDTO plainCamel = objectMapper.readValue("""
                {
                  "characterId": "1",
                  "commonName": "Victor",
                  "mainCharacter": true,
                  "descriptions": {
                    "ko": "wizard"
                  },
                  "portraitPrompt": "prompt"
                }
                """, CharacterDTO.class);

        assertThat(camelCase.isMainCharacter()).isTrue();
        assertThat(snakeCase.isMainCharacter()).isTrue();
        assertThat(plainCamel.isMainCharacter()).isTrue();
    }

    @Test
    @DisplayName("response DTOs serialize boolean fields with stable camelCase keys")
    void responseDtosSerializeStableBooleanKeys() throws Exception {
        BookSummaryDTO bookSummary = BookSummaryDTO.builder()
                .id(1L)
                .title("Book")
                .author("Author")
                .normalizationStatus("READY")
                .analysisStatus("NONE")
                .ruleVersion("v2")
                .locatorVersion("v2")
                .normalizationRunId("run")
                .normalizationVersionStatus("CURRENT")
                .needsRenormalization(false)
                .isFavorite(true)
                .isDefault(true)
                .summary(false)
                .build();
        BookDetailDTO bookDetail = BookDetailDTO.builder()
                .id(1L)
                .title("Book")
                .author("Author")
                .language("en")
                .isDefault(true)
                .normalizationStatus("READY")
                .analysisStatus("NONE")
                .ruleVersion("v2")
                .locatorVersion("v2")
                .normalizationRunId("run")
                .normalizationVersionStatus("CURRENT")
                .needsRenormalization(false)
                .isFavorite(true)
                .summary(false)
                .build();
        BookManifestDTO bookManifest = BookManifestDTO.builder()
                .id(1L)
                .title("Book")
                .author("Author")
                .language("en")
                .isDefault(true)
                .summary(false)
                .normalizationStatus("READY")
                .analysisStatus("NONE")
                .ruleVersion("v2")
                .locatorVersion("v2")
                .normalizationRunId("run")
                .normalizationVersionStatus("CURRENT")
                .needsRenormalization(false)
                .build();
        CharacterManifestDTO characterManifest = CharacterManifestDTO.builder()
                .id(1L)
                .name("Victor")
                .isMainCharacter(true)
                .build();
        ChapterPovSummaryDTO chapterPovSummary = new ChapterPovSummaryDTO(1L, "Victor", "summary", true);
        GraphNodeDTO graphNode = GraphNodeDTO.builder()
                .id(1L)
                .label("Victor")
                .isMainCharacter(true)
                .portraitPrompt("prompt")
                .build();
        BookmarkResponseDTO bookmark = BookmarkResponseDTO.builder()
                .id(1L)
                .bookId(1L)
                .isRangeBookmark(true)
                .build();

        JsonNode bookSummaryJson = objectMapper.readTree(objectMapper.writeValueAsBytes(bookSummary));
        JsonNode bookDetailJson = objectMapper.readTree(objectMapper.writeValueAsBytes(bookDetail));
        JsonNode bookManifestJson = objectMapper.readTree(objectMapper.writeValueAsBytes(bookManifest));
        JsonNode characterManifestJson = objectMapper.readTree(objectMapper.writeValueAsBytes(characterManifest));
        JsonNode chapterPovSummaryJson = objectMapper.readTree(objectMapper.writeValueAsBytes(chapterPovSummary));
        JsonNode graphNodeJson = objectMapper.readTree(objectMapper.writeValueAsBytes(graphNode));
        JsonNode bookmarkJson = objectMapper.readTree(objectMapper.writeValueAsBytes(bookmark));

        assertThat(bookSummaryJson.has("isFavorite")).isTrue();
        assertThat(bookSummaryJson.has("favorite")).isFalse();
        assertThat(bookSummaryJson.has("isDefault")).isTrue();
        assertThat(bookSummaryJson.has("default")).isFalse();

        assertThat(bookDetailJson.has("isFavorite")).isTrue();
        assertThat(bookDetailJson.has("favorite")).isFalse();
        assertThat(bookDetailJson.has("isDefault")).isTrue();
        assertThat(bookDetailJson.has("default")).isFalse();

        assertThat(bookManifestJson.has("isDefault")).isTrue();
        assertThat(bookManifestJson.has("default")).isFalse();

        assertThat(characterManifestJson.has("isMainCharacter")).isTrue();
        assertThat(characterManifestJson.has("mainCharacter")).isFalse();
        assertThat(characterManifestJson.has("main_character")).isFalse();

        assertThat(chapterPovSummaryJson.has("isMainCharacter")).isTrue();
        assertThat(chapterPovSummaryJson.has("mainCharacter")).isFalse();

        assertThat(graphNodeJson.has("isMainCharacter")).isTrue();
        assertThat(graphNodeJson.has("main_character")).isFalse();

        assertThat(bookmarkJson.has("isRangeBookmark")).isTrue();
        assertThat(bookmarkJson.has("rangeBookmark")).isFalse();
    }
}
