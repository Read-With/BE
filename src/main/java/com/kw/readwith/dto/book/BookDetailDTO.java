package com.kw.readwith.dto.book;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookDetailDTO {
    private Long id;
    private String title;
    private String author;
    private String language;
    private boolean isDefault;
    private String coverImgUrl;
    private String epubPath;
    private String normalizationStatus;
    private String ruleVersion;
    private String locatorVersion;
    private String normalizedArtifactPath;
    private boolean isFavorite;
    private boolean summary;
} 
