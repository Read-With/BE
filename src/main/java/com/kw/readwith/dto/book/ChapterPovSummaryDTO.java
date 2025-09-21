package com.kw.readwith.dto.book;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterPovSummaryDTO {
    private Long characterId;
    private String characterName;
    private String summaryText;
    private boolean isMainCharacter;
}
