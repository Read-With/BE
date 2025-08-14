package com.kw.readwith.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterPovSummaryDTO {
    private Long id;
    private Long chapterId;
    private Long characterId;
    private String summaryText;
}