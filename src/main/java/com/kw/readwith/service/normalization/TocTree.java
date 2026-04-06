package com.kw.readwith.service.normalization;

import java.util.ArrayList;
import java.util.List;

record TocTree(
        TocSource source,
        List<TocNode> roots
) {

    List<TocNode> leafNodes() {
        List<TocNode> leaves = new ArrayList<>();
        for (TocNode root : roots) {
            collectLeaves(root, leaves);
        }
        return leaves;
    }

    private void collectLeaves(TocNode node, List<TocNode> leaves) {
        boolean hasHrefChild = node.children().stream().anyMatch(child -> child.hasHref() || !child.children().isEmpty());
        if (node.hasHref() && !hasHrefChild) {
            leaves.add(node);
            return;
        }

        for (TocNode child : node.children()) {
            collectLeaves(child, leaves);
        }

        if (node.hasHref() && node.children().isEmpty()) {
            leaves.add(node);
        }
    }
}
