package com.kw.readwith.service.normalization;

import java.util.List;

record SplitUnit(
        String unitId,
        String title,
        SplitUnitRole role,
        String sourceDocHref,
        String startAnchorId,
        String endAnchorId,
        List<String> tocNodePath,
        int depth,
        List<SplitBlock> blocks,
        List<Integer> paragraphStarts,
        List<Integer> paragraphLengths,
        int totalCodePoints
) {

    String rawText() {
        StringBuilder builder = new StringBuilder();
        for (SplitBlock block : blocks) {
            builder.append(block.text());
        }
        return builder.toString();
    }

    String nearestAncestorTitle() {
        if (tocNodePath == null || tocNodePath.size() < 2) {
            return null;
        }
        return tocNodePath.get(tocNodePath.size() - 2);
    }
}
