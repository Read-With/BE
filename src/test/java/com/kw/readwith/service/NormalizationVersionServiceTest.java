package com.kw.readwith.service;

import com.kw.readwith.config.EpubNormalizationProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.NormalizationVersionStatus;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationVersionServiceTest {

    private final EpubNormalizationProperties properties = new EpubNormalizationProperties();
    private final NormalizationVersionService normalizationVersionService = new NormalizationVersionService(properties);

    @Test
    @DisplayName("ready book with matching rule and locator versions is current")
    void resolveStatusCurrent() {
        properties.setRuleVersion("rule-v2");
        properties.setLocatorVersion("locator-v3");

        Book book = Book.builder()
                .title("Book")
                .author("Author")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .ruleVersion("rule-v2")
                .locatorVersion("locator-v3")
                .normalizationRunId("run-1")
                .normalizedArtifactPath("books/1/normalizations/run-1")
                .build();

        assertThat(normalizationVersionService.resolveStatus(book)).isEqualTo(NormalizationVersionStatus.CURRENT);
        assertThat(normalizationVersionService.needsRenormalization(book)).isFalse();
    }

    @Test
    @DisplayName("ready book with different versions is outdated")
    void resolveStatusOutdated() {
        properties.setRuleVersion("rule-v2");
        properties.setLocatorVersion("locator-v3");

        Book book = Book.builder()
                .title("Book")
                .author("Author")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .ruleVersion("rule-v1")
                .locatorVersion("locator-v2")
                .normalizationRunId("run-0")
                .normalizedArtifactPath("books/1/normalizations/run-0")
                .build();

        assertThat(normalizationVersionService.resolveStatus(book)).isEqualTo(NormalizationVersionStatus.OUTDATED);
        assertThat(normalizationVersionService.needsRenormalization(book)).isTrue();
    }

    @Test
    @DisplayName("book without active normalization is not ready and failure does not remove existing ready artifact")
    void bookFailureKeepsExistingActiveArtifact() {
        Book readyBook = Book.builder()
                .title("Book")
                .author("Author")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .ruleVersion("rule-v1")
                .locatorVersion("locator-v2")
                .normalizationRunId("run-1")
                .normalizedArtifactPath("books/1/normalizations/run-1")
                .build();

        readyBook.markNormalizationFailed();

        assertThat(readyBook.getNormalizationStatus()).isEqualTo(NormalizationStatus.READY);

        Book initialBook = Book.builder()
                .title("Initial")
                .author("Author")
                .language("en")
                .build();
        initialBook.markNormalizationQueued();
        initialBook.markNormalizationFailed();

        assertThat(initialBook.getNormalizationStatus()).isEqualTo(NormalizationStatus.FAILED);
    }
}
