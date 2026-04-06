package com.kw.readwith.service.normalization;

enum CanonicalMergeReason {
    KEEP_AS_IS,
    PROMOTE_SCENE_TO_ACT,
    MERGE_SHORT_SIBLINGS,
    GROUP_CANTOS,
    GROUP_SMALL_UNITS,
    PROMOTE_TO_PARENT
}
