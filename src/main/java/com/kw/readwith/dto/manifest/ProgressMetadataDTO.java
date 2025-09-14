package com.kw.readwith.dto.manifest;

import lombok.*;

import java.util.List;

/**
 * Progress API 메타데이터 DTO
 * Manifest API에서 Progress 계산에 필요한 메타데이터를 제공
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressMetadataDTO {
    
    /**
     * 최대 챕터 수
     */
    private Integer maxChapter;
    
    /**
     * 각 챕터별 글자수
     */
    private List<ChapterLengthDTO> chapterLengths;
    
    /**
     * 책 전체 글자수
     */
    private Integer totalLength;
}
