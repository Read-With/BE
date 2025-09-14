package com.kw.readwith.dto.manifest;

import lombok.*;

/**
 * 챕터별 글자수 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterLengthDTO {
    
    /**
     * 챕터 인덱스
     */
    private Integer chapterIdx;
    
    /**
     * 해당 챕터의 글자수
     */
    private Integer length;
}
