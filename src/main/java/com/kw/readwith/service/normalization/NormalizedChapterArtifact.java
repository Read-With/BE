package com.kw.readwith.service.normalization;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NormalizedChapterArtifact {

    private final int chapterIndex;
    private final String title;
    private final String spineHref;
    private final List<String> sourceDocHrefs;
    private final List<String> blockSourceDocHrefs;
    private final List<Integer> paragraphStarts;
    private final List<Integer> paragraphLengths;
    private final int totalCodePoints;
    private final int startPos;
    private final int endPos;
    private final String rawText;
    private final String normalizedXhtml;
}
