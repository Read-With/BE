package com.kw.readwith.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocatorDTO {

    private Integer chapterIndex;
    private Integer blockIndex;
    private Integer offset;
}
