package com.kw.readwith.dto.progress;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveProgressRequestDTO {

    private Long bookId;

    private Integer chapterIdx;

    private Integer eventIdx;       // null = 챕터 끝

    private String  cfi;            // epubcfi(...)  ← nullable

}