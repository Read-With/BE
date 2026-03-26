package com.kw.readwith.service.normalization;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NormalizationMetaDocument {

    private final Long bookId;
    private final String runId;
    private final String ruleVersion;
    private final String locatorVersion;
    private final LocalDateTime generatedAt;
    private final int totalCodePoints;
    private final List<NormalizationMetaChapter> chapters;
}
