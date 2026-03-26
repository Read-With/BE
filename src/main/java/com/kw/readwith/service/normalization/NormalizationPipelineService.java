package com.kw.readwith.service.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.domain.enums.NormalizationFailureCode;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class NormalizationPipelineService {

    private static final Set<String> BLOCK_TAGS = Set.of("p", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> TEXT_MEDIA_TYPES = Set.of("application/xhtml+xml", "text/html");

    private final ObjectMapper objectMapper;

    public NormalizationPipelineResult normalize(
            Path epubFile,
            Long bookId,
            String runId,
            String ruleVersion,
            String locatorVersion
    ) {
        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {
            String packagePath = resolvePackagePath(zipFile);
            List<String> spineHrefs = resolveSpineHrefs(zipFile, packagePath);
            if (spineHrefs.isEmpty()) {
                throw failure(
                        NormalizationFailureCode.SPINE_RESOLVE_FAILED,
                        "resolve_spine",
                        "EPUB spine does not contain readable XHTML items."
                );
            }

            List<NormalizedChapterArtifact> chapters = new ArrayList<>();
            int cumulativeOffset = 0;

            for (String spineHref : spineHrefs) {
                Document chapterDocument = readXhtml(zipFile, spineHref);
                sanitize(chapterDocument);
                NormalizedChapterArtifact artifact = buildChapterArtifact(
                        chapters.size() + 1,
                        spineHref,
                        chapterDocument,
                        cumulativeOffset
                );
                if (artifact == null) {
                    continue;
                }
                chapters.add(artifact);
                cumulativeOffset += artifact.getTotalCodePoints();
            }

            if (chapters.isEmpty()) {
                throw failure(
                        NormalizationFailureCode.NO_TEXT_CHAPTERS,
                        "extract_chapters",
                        "No readable text chapters were extracted from EPUB."
                );
            }

            NormalizationMetaDocument metaDocument = buildMetaDocument(bookId, runId, ruleVersion, locatorVersion, chapters);
            ValidationReport validationReport = buildValidationReport(bookId, runId, ruleVersion, locatorVersion, chapters);
            if (validationReport.roundTripFailures() > 0) {
                throw failure(
                        NormalizationFailureCode.VALIDATION_FAILED,
                        "validate_locator_projection",
                        "Normalization validation failed with " + validationReport.roundTripFailures() + " round-trip errors."
                );
            }

            return NormalizationPipelineResult.builder()
                    .combinedXhtml(buildCombinedXhtml(chapters))
                    .metaJson(writeJson(metaDocument))
                    .validationReportJson(validationReport.reportJson())
                    .chapters(chapters)
                    .build();
        } catch (NormalizationProcessingException e) {
            throw e;
        } catch (ZipException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.INVALID_EPUB_ARCHIVE,
                    "open_epub",
                    "EPUB archive is invalid.",
                    e
            );
        } catch (IOException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.INVALID_EPUB_ARCHIVE,
                    "open_epub",
                    "Failed to read EPUB archive.",
                    e
            );
        }
    }

    private String resolvePackagePath(ZipFile zipFile) throws IOException {
        ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
        if (containerEntry == null) {
            throw failure(NormalizationFailureCode.CONTAINER_NOT_FOUND, "resolve_container", "EPUB container.xml is missing.");
        }

        try (InputStream inputStream = zipFile.getInputStream(containerEntry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            NodeList rootFiles = document.getElementsByTagName("rootfile");
            if (rootFiles.getLength() == 0) {
                throw failure(NormalizationFailureCode.CONTAINER_PARSE_FAILED, "resolve_container", "EPUB rootfile path is missing.");
            }

            Node fullPathNode = rootFiles.item(0).getAttributes().getNamedItem("full-path");
            if (fullPathNode == null || fullPathNode.getNodeValue() == null || fullPathNode.getNodeValue().isBlank()) {
                throw failure(NormalizationFailureCode.CONTAINER_PARSE_FAILED, "resolve_container", "EPUB rootfile path is missing.");
            }
            return fullPathNode.getNodeValue();
        } catch (NormalizationProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.CONTAINER_PARSE_FAILED,
                    "resolve_container",
                    "EPUB container.xml could not be parsed.",
                    e
            );
        }
    }

    private List<String> resolveSpineHrefs(ZipFile zipFile, String packagePath) throws IOException {
        ZipEntry packageEntry = findEntry(zipFile, packagePath);
        if (packageEntry == null) {
            throw failure(NormalizationFailureCode.PACKAGE_NOT_FOUND, "resolve_package", "EPUB package document is missing.");
        }

        try (InputStream inputStream = zipFile.getInputStream(packageEntry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            Map<String, ManifestItem> manifest = new LinkedHashMap<>();
            String baseDir = parentPath(packagePath);

            NodeList nodes = document.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!matchesLocalName(node, "item")) {
                    continue;
                }
                Node idNode = node.getAttributes().getNamedItem("id");
                Node hrefNode = node.getAttributes().getNamedItem("href");
                Node mediaTypeNode = node.getAttributes().getNamedItem("media-type");
                Node propertiesNode = node.getAttributes().getNamedItem("properties");
                if (idNode == null || hrefNode == null || mediaTypeNode == null) {
                    continue;
                }

                String mediaType = mediaTypeNode.getNodeValue().toLowerCase(Locale.ROOT);
                if (!TEXT_MEDIA_TYPES.contains(mediaType)) {
                    continue;
                }

                manifest.put(idNode.getNodeValue(), new ManifestItem(
                        resolvePath(baseDir, hrefNode.getNodeValue()),
                        propertiesNode != null ? propertiesNode.getNodeValue() : null
                ));
            }

            List<String> spineHrefs = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!matchesLocalName(node, "itemref")) {
                    continue;
                }
                Node idRefNode = node.getAttributes().getNamedItem("idref");
                if (idRefNode == null) {
                    continue;
                }

                ManifestItem manifestItem = manifest.get(idRefNode.getNodeValue());
                if (manifestItem != null && !manifestItem.isAuxiliary()) {
                    spineHrefs.add(manifestItem.href());
                }
            }

            return spineHrefs;
        } catch (NormalizationProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.PACKAGE_PARSE_FAILED,
                    "resolve_package",
                    "EPUB package document could not be parsed.",
                    e
            );
        }
    }

    private Document readXhtml(ZipFile zipFile, String entryPath) throws IOException {
        ZipEntry entry = findEntry(zipFile, entryPath);
        if (entry == null) {
            throw failure(NormalizationFailureCode.SPINE_ENTRY_NOT_FOUND, "read_xhtml", "EPUB spine entry is missing: " + entryPath);
        }
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            return Jsoup.parse(inputStream, null, "", Parser.xmlParser());
        } catch (IOException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.XHTML_PARSE_FAILED,
                    "read_xhtml",
                    "Failed to parse XHTML spine entry: " + entryPath,
                    e
            );
        }
    }

    private void sanitize(Document document) {
        document.select("script,iframe,object,embed,style,link").remove();
        for (Element element : document.getAllElements()) {
            List<String> attributesToRemove = new ArrayList<>();
            for (Attribute attribute : element.attributes()) {
                String key = attribute.getKey().toLowerCase(Locale.ROOT);
                String value = attribute.getValue();
                if (key.startsWith("on") || "style".equals(key)) {
                    attributesToRemove.add(attribute.getKey());
                    continue;
                }
                if (("src".equals(key) || "href".equals(key)) && isRemoteReference(value)) {
                    attributesToRemove.add(attribute.getKey());
                }
            }
            attributesToRemove.forEach(element::removeAttr);
        }
    }

    private NormalizedChapterArtifact buildChapterArtifact(
            int chapterIndex,
            String spineHref,
            Document document,
            int cumulativeOffset
    ) {
        Element body = document.body();
        if (body == null) {
            return null;
        }

        List<String> paragraphs = extractParagraphs(body);
        if (paragraphs.isEmpty()) {
            return null;
        }

        List<Integer> paragraphStarts = new ArrayList<>();
        List<Integer> paragraphLengths = new ArrayList<>();
        StringBuilder rawTextBuilder = new StringBuilder();
        int chapterOffset = 0;

        for (String paragraph : paragraphs) {
            paragraphStarts.add(chapterOffset);
            int paragraphLength = paragraph.codePointCount(0, paragraph.length());
            paragraphLengths.add(paragraphLength);
            rawTextBuilder.append(paragraph);
            chapterOffset += paragraphLength;
        }

        String title = resolveChapterTitle(document, chapterIndex);
        int startPos = cumulativeOffset;
        int endPos = chapterOffset == 0 ? cumulativeOffset : cumulativeOffset + chapterOffset - 1;

        return NormalizedChapterArtifact.builder()
                .chapterIndex(chapterIndex)
                .title(title)
                .spineHref(spineHref)
                .paragraphStarts(paragraphStarts)
                .paragraphLengths(paragraphLengths)
                .totalCodePoints(chapterOffset)
                .startPos(startPos)
                .endPos(endPos)
                .rawText(rawTextBuilder.toString())
                .normalizedXhtml(buildChapterXhtml(chapterIndex, title, spineHref, paragraphs))
                .build();
    }

    private List<String> extractParagraphs(Element body) {
        List<String> paragraphs = new ArrayList<>();
        for (Element candidate : body.select(String.join(",", BLOCK_TAGS))) {
            String text = normalizeWhitespace(candidate.text());
            if (!text.isBlank()) {
                paragraphs.add(text);
            }
        }
        if (paragraphs.isEmpty()) {
            String bodyText = normalizeWhitespace(body.text());
            if (!bodyText.isBlank()) {
                paragraphs.add(bodyText);
            }
        }
        return paragraphs;
    }

    private String resolveChapterTitle(Document document, int chapterIndex) {
        for (String selector : List.of("h1", "h2", "title")) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = normalizeWhitespace(element.text());
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "Chapter " + chapterIndex;
    }

    private String buildChapterXhtml(int chapterIndex, String title, String spineHref, List<String> paragraphs) {
        StringBuilder builder = new StringBuilder();
        builder.append("<section data-chapter-index=\"").append(chapterIndex).append("\"");
        builder.append(" data-spine-href=\"").append(escapeXml(spineHref)).append("\">");
        builder.append("<h2>").append(escapeXml(title)).append("</h2>");
        for (int i = 0; i < paragraphs.size(); i++) {
            builder.append("<p data-block-index=\"").append(i).append("\">")
                    .append(escapeXml(paragraphs.get(i)))
                    .append("</p>");
        }
        builder.append("</section>");
        return builder.toString();
    }

    private String buildCombinedXhtml(List<NormalizedChapterArtifact> chapters) {
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

    private NormalizationMetaDocument buildMetaDocument(
            Long bookId,
            String runId,
            String ruleVersion,
            String locatorVersion,
            List<NormalizedChapterArtifact> chapters
    ) {
        int totalCodePoints = chapters.stream()
                .mapToInt(NormalizedChapterArtifact::getTotalCodePoints)
                .sum();

        List<NormalizationMetaChapter> metaChapters = chapters.stream()
                .map(chapter -> NormalizationMetaChapter.builder()
                        .chapterIndex(chapter.getChapterIndex())
                        .title(chapter.getTitle())
                        .spineHref(chapter.getSpineHref())
                        .paragraphCount(chapter.getParagraphStarts().size())
                        .paragraphStarts(chapter.getParagraphStarts())
                        .paragraphLengths(chapter.getParagraphLengths())
                        .totalCodePoints(chapter.getTotalCodePoints())
                        .startPos(chapter.getStartPos())
                        .endPos(chapter.getEndPos())
                        .build())
                .toList();

        return NormalizationMetaDocument.builder()
                .bookId(bookId)
                .runId(runId)
                .ruleVersion(ruleVersion)
                .locatorVersion(locatorVersion)
                .generatedAt(LocalDateTime.now())
                .totalCodePoints(totalCodePoints)
                .chapters(metaChapters)
                .build();
    }

    private ValidationReport buildValidationReport(
            Long bookId,
            String runId,
            String ruleVersion,
            String locatorVersion,
            List<NormalizedChapterArtifact> chapters
    ) {
        List<Map<String, Object>> chapterReports = new ArrayList<>();
        int roundTripFailures = 0;

        for (NormalizedChapterArtifact chapter : chapters) {
            int chapterFailures = 0;
            for (int i = 0; i < chapter.getParagraphStarts().size(); i++) {
                int start = chapter.getParagraphStarts().get(i);
                int end = start + chapter.getParagraphLengths().get(i);
                if (start < 0 || end > chapter.getTotalCodePoints() || start > end) {
                    chapterFailures++;
                }
            }

            roundTripFailures += chapterFailures;

            Map<String, Object> chapterReport = new LinkedHashMap<>();
            chapterReport.put("chapterIndex", chapter.getChapterIndex());
            chapterReport.put("paragraphCount", chapter.getParagraphStarts().size());
            chapterReport.put("totalCodePoints", chapter.getTotalCodePoints());
            chapterReport.put("roundTripFailures", chapterFailures);
            chapterReports.add(chapterReport);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("bookId", bookId);
        report.put("runId", runId);
        report.put("ruleVersion", ruleVersion);
        report.put("locatorVersion", locatorVersion);
        report.put("chapterCount", chapters.size());
        report.put("roundTripFailures", roundTripFailures);
        report.put("status", roundTripFailures == 0 ? "PASS" : "FAIL");
        report.put("chapters", chapterReports);
        return new ValidationReport(writeJson(report), roundTripFailures);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.UNEXPECTED_FAILURE,
                    "serialize_metadata",
                    "Failed to serialize normalization metadata.",
                    e
            );
        }
    }

    private ZipEntry findEntry(ZipFile zipFile, String normalizedPath) {
        ZipEntry direct = zipFile.getEntry(normalizedPath);
        if (direct != null) {
            return direct;
        }

        String target = normalizedPath.replace("\\", "/");
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (Objects.equals(entry.getName(), target)) {
                return entry;
            }
        }
        return null;
    }

    private boolean matchesLocalName(Node node, String localName) {
        return localName.equalsIgnoreCase(node.getLocalName()) || localName.equalsIgnoreCase(node.getNodeName());
    }

    private String parentPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private String resolvePath(String baseDir, String href) {
        if (baseDir == null || baseDir.isBlank()) {
            return href.replace("\\", "/");
        }
        return Path.of(baseDir).resolve(href).normalize().toString().replace("\\", "/");
    }

    private boolean isRemoteReference(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//")
                || normalized.startsWith("data:");
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private DocumentBuilderFactory newSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private NormalizationProcessingException failure(NormalizationFailureCode code, String step, String message) {
        return new NormalizationProcessingException(code, step, message);
    }

    private record ManifestItem(String href, String properties) {

        private boolean isAuxiliary() {
            if (properties == null || properties.isBlank()) {
                return false;
            }
            List<String> tokens = List.of(properties.split("\\s+"));
            return tokens.contains("nav") || tokens.contains("svg");
        }
    }

    private record ValidationReport(String reportJson, int roundTripFailures) {
    }
}
