package com.kw.readwith.dto.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SummaryUploadDTO {

    private Integer chapterIndex;
    private String language;
    private List<SummaryItemDTO> items;
}
