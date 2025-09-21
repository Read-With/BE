package com.kw.readwith.dto.book;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPovSummaryResponseDTO {
    private Long bookId;
    private Integer chapterIdx;
    private String chapterTitle;
    private List<ChapterPovSummaryDTO> povSummaries;
}
