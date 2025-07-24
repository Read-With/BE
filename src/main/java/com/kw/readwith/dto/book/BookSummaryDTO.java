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
    private boolean isFavorite;
    private boolean isDefault;
    private LocalDateTime updatedAt;
} 