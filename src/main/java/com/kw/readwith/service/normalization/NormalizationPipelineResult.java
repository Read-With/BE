package com.kw.readwith.service.normalization;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NormalizationPipelineResult {

    private final String combinedXhtml;
    private final String metaJson;
    private final String validationReportJson;
    private final List<NormalizedChapterArtifact> chapters;
}
