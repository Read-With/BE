package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BookMetadataDTO {
    
    /**
     * 책의 최대 챕터 수
     */
    private Integer maxChapter;
    
    /**
     * 각 챕터별 글자수 목록 (챕터 순서대로)
     */
    private List<Integer> chapterCharacterCounts;
    
    /**
     * 전체 책 글자수
     */
    private Integer totalCharacterCount;
}
