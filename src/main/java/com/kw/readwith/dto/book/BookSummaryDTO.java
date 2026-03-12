package com.kw.readwith.dto.book;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BookSummaryDTO {
    private Long id;
    private String title;
    private String author;
    private String coverImgUrl;
    private String epubPath;
    private String normalizationStatus;
    private String ruleVersion;
    private String locatorVersion;
    private String normalizedArtifactPath;
    private boolean isFavorite;
    private boolean isDefault;
    private boolean summary;
    private LocalDateTime updatedAt;
} 
