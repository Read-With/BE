package com.kw.readwith.service.normalization;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NormalizationMetaChapter {

    private final int chapterIndex;
    private final String title;
    private final String spineHref;
    private final int paragraphCount;
    private final List<Integer> paragraphStarts;
    private final List<Integer> paragraphLengths;
    private final int totalCodePoints;
    private final int startPos;
    private final int endPos;
}
