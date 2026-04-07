package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.Book;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAdminDetailDTO {
    private Long id;
    private String title;
    private String author;
    private String language;
    private boolean isDefault;
    private boolean summary;
    private String coverImgUrl;
    private String summaryUrl;
    private String bookPrompt;
    private String epubPath;
    private String normalizationStatus;
    private String analysisStatus;
    private String ruleVersion;
    private String locatorVersion;
    private String normalizedArtifactPath;
    private String normalizationRunId;
    private Long uploadedById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookAdminDetailDTO from(Book book) {
        return BookAdminDetailDTO.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .language(book.getLanguage())
                .isDefault(book.isDefault())
                .summary(book.isSummary())
                .coverImgUrl(book.getCoverImgUrl())
                .summaryUrl(book.getSummaryUrl())
                .bookPrompt(book.getBookPrompt())
                .epubPath(book.getEpubPath())
                .normalizationStatus(book.getNormalizationStatus() != null ? book.getNormalizationStatus().name() : null)
                .analysisStatus(book.getAnalysisStatus() != null ? book.getAnalysisStatus().name() : null)
                .ruleVersion(book.getRuleVersion())
                .locatorVersion(book.getLocatorVersion())
                .normalizedArtifactPath(book.getNormalizedArtifactPath())
                .normalizationRunId(book.getNormalizationRunId())
                .uploadedById(book.getUploadedBy() != null ? book.getUploadedBy().getId() : null)
                .createdAt(book.getCreatedAt())
                .updatedAt(book.getUpdatedAt())
                .build();
    }
}
