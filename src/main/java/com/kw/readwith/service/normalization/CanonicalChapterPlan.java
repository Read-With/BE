package com.kw.readwith.service.normalization;

import java.util.List;

record CanonicalChapterPlan(
        int chapterIndex,
        String title,
        SplitUnitRole role,
        List<SplitUnit> sourceUnits,
        List<String> sourceDocHrefs,
        List<String> sourceTitles,
        CanonicalMergeReason mergedReason,
        int estimatedCodePoints
) {
}
