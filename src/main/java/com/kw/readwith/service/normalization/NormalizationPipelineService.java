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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private static final int MAX_CANONICAL_CHAPTER_COUNT = 20;
    private static final Set<String> TEXT_MEDIA_TYPES = Set.of("application/xhtml+xml", "text/html");

    private final ObjectMapper objectMapper;
    private final TocTreeBuilder tocTreeBuilder = new TocTreeBuilder();
    private final SplitUnitExtractor splitUnitExtractor = new SplitUnitExtractor(new SplitUnitClassifier());
    private final CanonicalChapterPlanner canonicalChapterPlanner = new CanonicalChapterPlanner();
    private final CanonicalChapterRenderer canonicalChapterRenderer = new CanonicalChapterRenderer();

    public NormalizationPipelineResult normalize(
            Path epubFile,
            Long bookId,
            String runId,
            String ruleVersion,
            String locatorVersion
    ) {
        try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {
            String packagePath = resolvePackagePath(zipFile);
            PackageDocumentData packageDocument = readPackageDocument(zipFile, packagePath);
            if (packageDocument.spineHrefs().isEmpty()) {
                throw failure(
                        NormalizationFailureCode.SPINE_RESOLVE_FAILED,
                        "resolve_spine",
                        "EPUB spine does not contain readable XHTML items."
                );
            }

            TocTree tocTree = tocTreeBuilder.build(zipFile, packagePath, packageDocument.manifestById());
            Map<String, Document> documentsByHref = loadSanitizedDocuments(zipFile, packageDocument.spineHrefs(), tocTree);
            List<SplitUnit> splitUnits = splitUnitExtractor.extract(tocTree, packageDocument.spineHrefs(), documentsByHref);
            if (splitUnits.isEmpty()) {
                throw failure(
                        NormalizationFailureCode.NO_TEXT_CHAPTERS,
                        "extract_chapters",
                        "No readable text chapters were extracted from EPUB."
                );
            }

            List<CanonicalChapterPlan> chapterPlans = canonicalChapterPlanner.plan(splitUnits);
            List<NormalizedChapterArtifact> chapters = canonicalChapterRenderer.render(chapterPlans);
            if (chapters.isEmpty()) {
                throw failure(
                        NormalizationFailureCode.NO_TEXT_CHAPTERS,
                        "plan_chapters",
                        "No canonical chapters were produced from EPUB."
                );
            }

            String combinedXhtml = canonicalChapterRenderer.buildCombinedXhtml(chapters);
            NormalizationMetaDocument metaDocument = buildMetaDocument(bookId, runId, ruleVersion, locatorVersion, chapters);
            ValidationReport validationReport = buildValidationReport(bookId, runId, ruleVersion, locatorVersion, chapters, combinedXhtml);
            if (validationReport.roundTripFailures() > 0) {
                throw failure(
                        NormalizationFailureCode.VALIDATION_FAILED,
                        "validate_locator_projection",
                        "Normalization validation failed with " + validationReport.roundTripFailures() + " round-trip errors."
                );
            }

            return NormalizationPipelineResult.builder()
                    .combinedXhtml(combinedXhtml)
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

    private PackageDocumentData readPackageDocument(ZipFile zipFile, String packagePath) throws IOException {
        ZipEntry packageEntry = findEntry(zipFile, packagePath);
        if (packageEntry == null) {
            throw failure(NormalizationFailureCode.PACKAGE_NOT_FOUND, "resolve_package", "EPUB package document is missing.");
        }

        try (InputStream inputStream = zipFile.getInputStream(packageEntry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            String baseDir = parentPath(packagePath);

            Map<String, EpubManifestItem> manifestById = new LinkedHashMap<>();
            NodeList nodes = document.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!matchesLocalName(node, "item") || node.getAttributes() == null) {
                    continue;
                }

                Node idNode = node.getAttributes().getNamedItem("id");
                Node hrefNode = node.getAttributes().getNamedItem("href");
                Node mediaTypeNode = node.getAttributes().getNamedItem("media-type");
                Node propertiesNode = node.getAttributes().getNamedItem("properties");
                if (idNode == null || hrefNode == null) {
                    continue;
                }

                String href = resolvePath(baseDir, hrefNode.getNodeValue());
                manifestById.put(idNode.getNodeValue(), new EpubManifestItem(
                        idNode.getNodeValue(),
                        href,
                        mediaTypeNode != null ? mediaTypeNode.getNodeValue() : null,
                        propertiesNode != null ? propertiesNode.getNodeValue() : null
                ));
            }

            List<String> spineHrefs = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!matchesLocalName(node, "itemref") || node.getAttributes() == null) {
                    continue;
                }

                Node idRefNode = node.getAttributes().getNamedItem("idref");
                if (idRefNode == null) {
                    continue;
                }

                EpubManifestItem manifestItem = manifestById.get(idRefNode.getNodeValue());
                if (manifestItem == null || manifestItem.mediaType() == null) {
                    continue;
                }
                String mediaType = manifestItem.mediaType().toLowerCase(Locale.ROOT);
                if (!TEXT_MEDIA_TYPES.contains(mediaType) || manifestItem.hasProperty("nav")) {
                    continue;
                }

                spineHrefs.add(manifestItem.href());
            }

            return new PackageDocumentData(manifestById, spineHrefs);
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

    private Map<String, Document> loadSanitizedDocuments(ZipFile zipFile, List<String> spineHrefs, TocTree tocTree) throws IOException {
        LinkedHashSet<String> docHrefs = new LinkedHashSet<>(spineHrefs);
        if (tocTree != null) {
            for (TocNode leaf : tocTree.leafNodes()) {
                if (leaf.sourceDocHref() != null && !leaf.sourceDocHref().isBlank()) {
                    docHrefs.add(leaf.sourceDocHref());
                }
            }
        }

        Map<String, Document> documents = new LinkedHashMap<>();
        for (String docHref : docHrefs) {
            Document document = readXhtml(zipFile, docHref);
            sanitize(document);
            documents.put(docHref, document);
        }
        return documents;
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

    private boolean isRemoteReference(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//")
                || normalized.startsWith("data:");
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
            List<NormalizedChapterArtifact> chapters,
            String combinedXhtml
    ) {
        List<Map<String, Object>> chapterReports = new ArrayList<>();
        int roundTripFailures = 0;
        List<Integer> invalidTitleChapters = new ArrayList<>();

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
            if (isInvalidCanonicalTitle(chapter.getTitle())) {
                invalidTitleChapters.add(chapter.getChapterIndex());
            }

            Map<String, Object> chapterReport = new LinkedHashMap<>();
            chapterReport.put("chapterIndex", chapter.getChapterIndex());
            chapterReport.put("title", chapter.getTitle());
            chapterReport.put("paragraphCount", chapter.getParagraphStarts().size());
            chapterReport.put("totalCodePoints", chapter.getTotalCodePoints());
            chapterReport.put("roundTripFailures", chapterFailures);
            chapterReports.add(chapterReport);
        }

        int combinedSectionCount = countCombinedSections(combinedXhtml);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("bookId", bookId);
        report.put("runId", runId);
        report.put("ruleVersion", ruleVersion);
        report.put("locatorVersion", locatorVersion);
        report.put("chapterCount", chapters.size());
        report.put("chapterCountLimit", MAX_CANONICAL_CHAPTER_COUNT);
        report.put("chapterCountWithinLimit", chapters.size() <= MAX_CANONICAL_CHAPTER_COUNT);
        report.put("combinedSectionCount", combinedSectionCount);
        report.put("combinedSectionCountMatchesChapterCount", combinedSectionCount == chapters.size());
        report.put("invalidTitleChapterIndexes", invalidTitleChapters);
        report.put("invalidTitleCount", invalidTitleChapters.size());
        report.put("roundTripFailures", roundTripFailures);
        report.put("status", roundTripFailures == 0 ? "PASS" : "FAIL");
        report.put("chapters", chapterReports);
        return new ValidationReport(writeJson(report), roundTripFailures);
    }

    private boolean isInvalidCanonicalTitle(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.equals("contents")
                || normalized.startsWith("contents ")
                || normalized.contains("project gutenberg license")
                || normalized.equals("index")
                || normalized.startsWith("index ");
    }

    private int countCombinedSections(String combinedXhtml) {
        if (combinedXhtml == null || combinedXhtml.isBlank()) {
            return 0;
        }
        Document document = Jsoup.parse(combinedXhtml, "", Parser.xmlParser());
        return document.select("section[data-chapter-index]").size();
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

    private NormalizationProcessingException failure(NormalizationFailureCode code, String step, String message) {
        return new NormalizationProcessingException(code, step, message);
    }

    private record PackageDocumentData(
            Map<String, EpubManifestItem> manifestById,
            List<String> spineHrefs
    ) {
    }

    private record ValidationReport(String reportJson, int roundTripFailures) {
    }
}
