package com.kw.readwith.service.normalization;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class SplitUnitExtractor {

    private static final Set<String> BLOCK_TAGS = Set.of("p", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6");
    private static final Pattern HEADING_SPLIT = Pattern.compile(
            "^(?i)(chapter\\b.*|book\\b.*|part\\b.*|act\\b.*|scene\\b.*|canto\\b.*|letter\\b.*|[IVXLCDM]+)$"
    );

    private final SplitUnitClassifier classifier;

    SplitUnitExtractor(SplitUnitClassifier classifier) {
        this.classifier = classifier;
    }

    List<SplitUnit> extract(TocTree tocTree, List<String> spineHrefs, Map<String, Document> documentsByHref) {
        int[] sequence = {1};
        if (tocTree != null && !tocTree.leafNodes().isEmpty()) {
            List<SplitUnit> tocUnits = extractFromToc(tocTree, spineHrefs, documentsByHref, sequence);
            if (!tocUnits.isEmpty()) {
                return tocUnits;
            }
        }
        return extractHeuristically(spineHrefs, documentsByHref, sequence);
    }

    private List<SplitUnit> extractFromToc(TocTree tocTree, List<String> spineHrefs, Map<String, Document> documentsByHref, int[] sequence) {
        Map<String, List<TocNode>> leavesByDoc = new LinkedHashMap<>();
        for (TocNode leaf : tocTree.leafNodes()) {
            if (!leaf.hasHref()) {
                continue;
            }
            leavesByDoc.computeIfAbsent(leaf.sourceDocHref(), ignored -> new ArrayList<>()).add(leaf);
        }

        LinkedHashSet<String> orderedDocs = new LinkedHashSet<>(spineHrefs);
        orderedDocs.addAll(leavesByDoc.keySet());

        List<SplitUnit> units = new ArrayList<>();
        for (String docHref : orderedDocs) {
            Document document = documentsByHref.get(docHref);
            if (document == null || document.body() == null) {
                continue;
            }

            List<TocNode> docLeaves = leavesByDoc.get(docHref);
            if (docLeaves == null || docLeaves.isEmpty()) {
                units.addAll(extractHeuristicUnitsForDocument(docHref, document, TocSource.SPINE, sequence));
                continue;
            }

            List<DocumentBlock> blocks = collectBlocks(document.body(), docLeaves.stream()
                    .map(TocNode::fragmentId)
                    .filter(fragment -> fragment != null && !fragment.isBlank())
                    .toList());
            if (blocks.isEmpty()) {
                continue;
            }

            int previousEnd = 0;
            for (int i = 0; i < docLeaves.size(); i++) {
                TocNode leaf = docLeaves.get(i);
                int startIndex = resolveStartIndex(leaf, blocks, previousEnd);
                int endIndex = i + 1 < docLeaves.size()
                        ? resolveStartIndex(docLeaves.get(i + 1), blocks, blocks.size())
                        : blocks.size();
                if (endIndex <= startIndex) {
                    continue;
                }

                SplitUnit unit = buildSplitUnit(
                        "unit-" + sequence[0]++,
                        leaf.title(),
                        leaf.titlePath(),
                        leaf.depth(),
                        docHref,
                        leaf.fragmentId(),
                        i + 1 < docLeaves.size() ? docLeaves.get(i + 1).fragmentId() : null,
                        blocks.subList(startIndex, endIndex)
                );
                units.add(unit);
                previousEnd = endIndex;
            }
        }
        return units;
    }

    private List<SplitUnit> extractHeuristically(List<String> spineHrefs, Map<String, Document> documentsByHref, int[] sequence) {
        List<SplitUnit> units = new ArrayList<>();
        for (String docHref : spineHrefs) {
            Document document = documentsByHref.get(docHref);
            if (document == null || document.body() == null) {
                continue;
            }
            units.addAll(extractHeuristicUnitsForDocument(docHref, document, TocSource.HEURISTIC, sequence));
        }
        return units;
    }

    private List<SplitUnit> extractHeuristicUnitsForDocument(String docHref, Document document, TocSource source, int[] sequence) {
        List<DocumentBlock> blocks = collectBlocks(document.body(), List.of());
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<Integer> splitIndexes = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            DocumentBlock block = blocks.get(i);
            if (block.tagName() != null
                    && block.tagName().matches("h[1-3]")
                    && HEADING_SPLIT.matcher(block.text()).matches()) {
                splitIndexes.add(i);
            }
        }

        List<SplitUnit> units = new ArrayList<>();
        if (splitIndexes.size() >= 2) {
            for (int i = 0; i < splitIndexes.size(); i++) {
                int start = splitIndexes.get(i);
                int end = i + 1 < splitIndexes.size() ? splitIndexes.get(i + 1) : blocks.size();
                units.add(buildSplitUnit(
                        "unit-" + sequence[0]++,
                        blocks.get(start).text(),
                        List.of(blocks.get(start).text()),
                        1,
                        docHref,
                        null,
                        null,
                        blocks.subList(start, end)
                ));
            }
            return units;
        }

        String title = resolveDocumentTitle(document);
        return List.of(buildSplitUnit(
                "unit-" + sequence[0]++,
                title,
                List.of(title),
                1,
                docHref,
                null,
                null,
                blocks
        ));
    }

    private SplitUnit buildSplitUnit(
            String unitId,
            String title,
            List<String> tocNodePath,
            int depth,
            String docHref,
            String startAnchorId,
            String endAnchorId,
            List<DocumentBlock> blocks
    ) {
        List<SplitBlock> splitBlocks = new ArrayList<>();
        List<Integer> paragraphStarts = new ArrayList<>();
        List<Integer> paragraphLengths = new ArrayList<>();
        int totalCodePoints = 0;

        for (DocumentBlock block : blocks) {
            splitBlocks.add(new SplitBlock(docHref, unitId, block.text()));
            paragraphStarts.add(totalCodePoints);
            int paragraphLength = block.text().codePointCount(0, block.text().length());
            paragraphLengths.add(paragraphLength);
            totalCodePoints += paragraphLength;
        }

        String resolvedTitle = title == null || title.isBlank() ? "Untitled Section" : title;
        return new SplitUnit(
                unitId,
                resolvedTitle,
                classifier.classify(resolvedTitle, tocNodePath),
                docHref,
                startAnchorId,
                endAnchorId,
                List.copyOf(tocNodePath),
                depth,
                List.copyOf(splitBlocks),
                List.copyOf(paragraphStarts),
                List.copyOf(paragraphLengths),
                totalCodePoints
        );
    }

    private int resolveStartIndex(TocNode leaf, List<DocumentBlock> blocks, int defaultIndex) {
        if (leaf.fragmentId() == null || leaf.fragmentId().isBlank()) {
            return defaultIndex == blocks.size() ? 0 : defaultIndex;
        }
        Integer index = blocks.stream()
                .filter(block -> block.anchorMappings().containsKey(leaf.fragmentId()))
                .map(DocumentBlock::index)
                .findFirst()
                .orElse(null);
        return index == null ? Math.min(defaultIndex, blocks.size()) : index;
    }

    private List<DocumentBlock> collectBlocks(Element body, List<String> targetFragments) {
        Set<String> remainingFragments = new LinkedHashSet<>(targetFragments);
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> pendingFragments = new ArrayList<>();

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (!(node instanceof Element element)) {
                    return;
                }

                List<String> matchedFragments = matchedFragments(element, remainingFragments);
                if (isBlockElement(element)) {
                    String text = normalizeWhitespace(element.text());
                    if (!text.isBlank()) {
                        int blockIndex = blocks.size();
                        Map<String, Integer> anchorMappings = new LinkedHashMap<>();
                        for (String pendingFragment : pendingFragments) {
                            anchorMappings.put(pendingFragment, blockIndex);
                            remainingFragments.remove(pendingFragment);
                        }
                        pendingFragments.clear();
                        for (String matchedFragment : matchedFragments) {
                            anchorMappings.put(matchedFragment, blockIndex);
                            remainingFragments.remove(matchedFragment);
                        }
                        blocks.add(new DocumentBlock(blockIndex, element.tagName(), text, anchorMappings));
                    }
                    return;
                }

                if (!matchedFragments.isEmpty()) {
                    pendingFragments.addAll(matchedFragments);
                }
            }

            @Override
            public void tail(Node node, int depth) {
            }
        }, body);

        if (!blocks.isEmpty()) {
            return blocks;
        }

        String bodyText = normalizeWhitespace(body.text());
        if (bodyText.isBlank()) {
            return List.of();
        }

        Map<String, Integer> anchorMappings = new LinkedHashMap<>();
        for (String fragment : targetFragments) {
            anchorMappings.put(fragment, 0);
        }
        return List.of(new DocumentBlock(0, "p", bodyText, anchorMappings));
    }

    private List<String> matchedFragments(Element element, Set<String> remainingFragments) {
        List<String> matched = new ArrayList<>();
        String id = element.id();
        if (id != null && !id.isBlank() && remainingFragments.contains(id)) {
            matched.add(id);
        }
        String name = element.attr("name");
        if (name != null && !name.isBlank() && remainingFragments.contains(name) && !matched.contains(name)) {
            matched.add(name);
        }
        return matched;
    }

    private String resolveDocumentTitle(Document document) {
        for (String selector : List.of("h1", "h2", "title")) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = normalizeWhitespace(element.text());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "Untitled Chapter";
    }

    private boolean isBlockElement(Element element) {
        return BLOCK_TAGS.contains(element.tagName().toLowerCase(Locale.ROOT));
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private record DocumentBlock(
            int index,
            String tagName,
            String text,
            Map<String, Integer> anchorMappings
    ) {
    }
}
