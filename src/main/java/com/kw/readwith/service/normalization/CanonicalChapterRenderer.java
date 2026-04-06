package com.kw.readwith.service.normalization;

import java.util.ArrayList;
import java.util.List;

class CanonicalChapterRenderer {

    List<NormalizedChapterArtifact> render(List<CanonicalChapterPlan> plans) {
        List<NormalizedChapterArtifact> artifacts = new ArrayList<>();
        int cumulativeOffset = 0;

        for (CanonicalChapterPlan plan : plans) {
            List<SplitBlock> blocks = plan.sourceUnits().stream()
                    .flatMap(unit -> unit.blocks().stream())
                    .toList();
            if (blocks.isEmpty()) {
                continue;
            }

            List<Integer> paragraphStarts = new ArrayList<>();
            List<Integer> paragraphLengths = new ArrayList<>();
            List<String> blockSourceDocHrefs = new ArrayList<>();
            StringBuilder rawTextBuilder = new StringBuilder();
            int chapterOffset = 0;

            for (SplitBlock block : blocks) {
                paragraphStarts.add(chapterOffset);
                int paragraphLength = block.text().codePointCount(0, block.text().length());
                paragraphLengths.add(paragraphLength);
                blockSourceDocHrefs.add(block.sourceDocHref());
                rawTextBuilder.append(block.text());
                chapterOffset += paragraphLength;
            }

            int startPos = cumulativeOffset;
            int endPos = chapterOffset == 0 ? cumulativeOffset : cumulativeOffset + chapterOffset - 1;
            String representativeSpineHref = plan.sourceDocHrefs().isEmpty() ? null : plan.sourceDocHrefs().get(0);

            artifacts.add(NormalizedChapterArtifact.builder()
                    .chapterIndex(plan.chapterIndex())
                    .title(plan.title())
                    .spineHref(representativeSpineHref)
                    .sourceDocHrefs(plan.sourceDocHrefs())
                    .blockSourceDocHrefs(blockSourceDocHrefs)
                    .paragraphStarts(paragraphStarts)
                    .paragraphLengths(paragraphLengths)
                    .totalCodePoints(chapterOffset)
                    .startPos(startPos)
                    .endPos(endPos)
                    .rawText(rawTextBuilder.toString())
                    .normalizedXhtml(buildChapterXhtml(plan.chapterIndex(), plan.title(), representativeSpineHref, blocks))
                    .build());
            cumulativeOffset += chapterOffset;
        }

        return artifacts;
    }

    String buildCombinedXhtml(List<NormalizedChapterArtifact> chapters) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>");
        builder.append("<meta charset=\"UTF-8\"/>");
        builder.append("<title>ReadWith Normalized Reader</title>");
        builder.append("</head><body>");
        for (NormalizedChapterArtifact chapter : chapters) {
            builder.append(chapter.getNormalizedXhtml());
        }
        builder.append("</body></html>");
        return builder.toString();
    }

    private String buildChapterXhtml(
            int chapterIndex,
            String title,
            String representativeSpineHref,
            List<SplitBlock> blocks
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("<section data-chapter-index=\"").append(chapterIndex).append("\"");
        if (representativeSpineHref != null && !representativeSpineHref.isBlank()) {
            builder.append(" data-representative-spine-href=\"").append(escapeXml(representativeSpineHref)).append("\"");
        }
        builder.append(">");
        builder.append("<h2>").append(escapeXml(title)).append("</h2>");
        for (int i = 0; i < blocks.size(); i++) {
            SplitBlock block = blocks.get(i);
            builder.append("<p data-block-index=\"").append(i).append("\"");
            if (block.sourceDocHref() != null && !block.sourceDocHref().isBlank()) {
                builder.append(" data-spine-href=\"").append(escapeXml(block.sourceDocHref())).append("\"");
            }
            if (block.sourceUnitId() != null && !block.sourceUnitId().isBlank()) {
                builder.append(" data-source-unit-id=\"").append(escapeXml(block.sourceUnitId())).append("\"");
            }
            builder.append(">")
                    .append(escapeXml(block.text()))
                    .append("</p>");
        }
        builder.append("</section>");
        return builder.toString();
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
