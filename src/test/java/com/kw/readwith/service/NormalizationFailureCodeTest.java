package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.enums.NormalizationFailureCode;
import com.kw.readwith.service.normalization.NormalizationPipelineService;
import com.kw.readwith.service.normalization.NormalizationProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationFailureCodeTest {

    private final NormalizationPipelineService normalizationPipelineService =
            new NormalizationPipelineService(new ObjectMapper().findAndRegisterModules());

    @Test
    @DisplayName("invalid epub archive produces invalid archive failure code")
    void invalidArchiveProducesFailureCode() throws IOException {
        Path invalidFile = Files.createTempFile("invalid-epub-", ".epub");
        Files.writeString(invalidFile, "not a zip", StandardCharsets.UTF_8);

        try {
            assertThatThrownBy(() -> normalizationPipelineService.normalize(
                    invalidFile,
                    1L,
                    "run-1",
                    "rule-v1",
                    "locator-v2"
            ))
                    .isInstanceOf(NormalizationProcessingException.class)
                    .satisfies(exception -> {
                        NormalizationProcessingException normalizationException = (NormalizationProcessingException) exception;
                        assertThat(normalizationException.getFailureCode()).isEqualTo(NormalizationFailureCode.INVALID_EPUB_ARCHIVE);
                        assertThat(normalizationException.getStep()).isEqualTo("open_epub");
                    });
        } finally {
            Files.deleteIfExists(invalidFile);
        }
    }
}
