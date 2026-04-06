package com.kw.readwith.service.normalization;

import java.util.List;

record TocNode(
        String nodeId,
        String title,
        String href,
        String sourceDocHref,
        String fragmentId,
        int depth,
        String parentNodeId,
        List<TocNode> children,
        String landmarkType,
        TocSource tocSource,
        List<String> titlePath
) {

    boolean hasHref() {
        return href != null && !href.isBlank() && sourceDocHref != null && !sourceDocHref.isBlank();
    }
}
