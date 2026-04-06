package com.kw.readwith.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.service.normalization.NormalizationPipelineResult;
import com.kw.readwith.service.normalization.NormalizationPipelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GutenbergNormalizationRegressionTest {

    private static final Path SAMPLE_DIR = Path.of("src", "main", "resources", "static", "books");
    private static final Path REPORT_PATH = Path.of("build", "reports", "normalization", "gutenberg-regression-report.json");
    private static final int MAX_ALLOWED_CHAPTERS = 20;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final NormalizationPipelineService normalizationPipelineService =
            new NormalizationPipelineService(objectMapper);

    @Test
    @DisplayName("Project Gutenberg EPUB samples normalize and produce a regression report")
    void normalizeSampleBooksAndWriteRegressionReport() throws Exception {
        assertThat(Files.isDirectory(SAMPLE_DIR))
                .as("sample directory must exist: %s", SAMPLE_DIR)
                .isTrue();

        List<Path> files = Files.list(SAMPLE_DIR)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".epub"))
                .sorted()
                .toList();

        assertThat(files)
                .as("at least one EPUB sample is required in %s", SAMPLE_DIR)
                .isNotEmpty();

        List<Map<String, Object>> items = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        List<String> chapterLimitViolations = new ArrayList<>();
        List<String> invalidTitleViolations = new ArrayList<>();
        List<String> sectionCountViolations = new ArrayList<>();
        List<String> structuralViolations = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", file.getFileName().toString());
            item.put("bookTitle", inferBookTitle(file.getFileName().toString()));
            item.put("variant", inferVariant(file.getFileName().toString()));
            item.put("sizeBytes", Files.size(file));

            try {
                NormalizationPipelineResult result = normalizationPipelineService.normalize(
                        file,
                        10_000L + i,
                        "regression-" + (i + 1),
                        "rule-v2",
                        "locator-v2"
                );

                JsonNode meta = objectMapper.readTree(result.getMetaJson());
                JsonNode report = objectMapper.readTree(result.getValidationReportJson());

                item.put("success", true);
                int chapterCount = meta.path("chapters").size();
                int combinedSectionCount = countCombinedSections(result.getCombinedXhtml());
                List<String> invalidTitles = invalidChapterTitles(meta);
                List<String> structuralIssues = structuralIssues(meta);

                item.put("chapterCount", chapterCount);
                item.put("chapterCountLimit", MAX_ALLOWED_CHAPTERS);
                item.put("chapterCountWithinLimit", chapterCount <= MAX_ALLOWED_CHAPTERS);
                item.put("totalCodePoints", meta.path("totalCodePoints").asInt());
                item.put("validationStatus", report.path("status").asText());
                item.put("roundTripFailures", report.path("roundTripFailures").asInt());
                item.put("combinedXhtmlLength", result.getCombinedXhtml().length());
                item.put("combinedSectionCount", combinedSectionCount);
                item.put("combinedSectionCountMatchesChapterCount", combinedSectionCount == chapterCount);
                item.put("invalidChapterTitles", invalidTitles);
                item.put("structurallyValid", structuralIssues.isEmpty());
                item.put("structuralIssues", structuralIssues);

                if (chapterCount > MAX_ALLOWED_CHAPTERS) {
                    chapterLimitViolations.add(file.getFileName().toString() + "=" + chapterCount);
                }
                if (combinedSectionCount != chapterCount) {
                    sectionCountViolations.add(file.getFileName().toString() + "=" + combinedSectionCount + "/" + chapterCount);
                }
                if (!invalidTitles.isEmpty()) {
                    invalidTitleViolations.add(file.getFileName().toString() + "=" + invalidTitles);
                }
                if (!structuralIssues.isEmpty()) {
                    structuralViolations.add(file.getFileName().toString() + "=" + structuralIssues);
                }
                successCount++;
            } catch (Exception e) {
                item.put("success", false);
                item.put("errorType", e.getClass().getSimpleName());
                item.put("errorMessage", resolveErrorMessage(e));
                item.put("errorLocation", resolveTopStackLocation(e));
                failureCount++;
            }

            items.add(item);
        }

        writeReport(files.size(), successCount, failureCount, items);

        assertThat(failureCount)
                .as("all Gutenberg EPUB samples should normalize successfully. report=%s", REPORT_PATH)
                .isZero();
        assertThat(chapterLimitViolations)
                .as("all Gutenberg EPUB samples should produce at most %s canonical chapters. report=%s", MAX_ALLOWED_CHAPTERS, REPORT_PATH)
                .isEmpty();
        assertThat(sectionCountViolations)
                .as("combined.xhtml section count must match meta.json chapter count. report=%s", REPORT_PATH)
                .isEmpty();
        assertThat(invalidTitleViolations)
                .as("canonical chapter titles must not contain contents/license/index placeholders. report=%s", REPORT_PATH)
                .isEmpty();
        assertThat(structuralViolations)
                .as("canonical chapter structure must be contiguous and internally consistent. report=%s", REPORT_PATH)
                .isEmpty();
    }

    private void writeReport(int totalCount,
                             int successCount,
                             int failureCount,
                             List<Map<String, Object>> items) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", OffsetDateTime.now());
        report.put("sampleDirectory", SAMPLE_DIR.toString().replace("\\", "/"));
        report.put("totalCount", totalCount);
        report.put("successCount", successCount);
        report.put("failureCount", failureCount);
        report.put("items", items);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);
    }

    private String inferBookTitle(String fileName) {
        return stripKnownSuffixes(fileName)
                .replace(".epub", "")
                .trim();
    }

    private String inferVariant(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith("-images-3.epub")) {
            return "EPUB3_IMAGES";
        }
        if (lower.endsWith("-images.epub")) {
            return "EPUB2_IMAGES";
        }
        return "EPUB_DEFAULT";
    }

    private String stripKnownSuffixes(String fileName) {
        if (fileName.endsWith("-images-3.epub")) {
            return fileName.substring(0, fileName.length() - "-images-3.epub".length());
        }
        if (fileName.endsWith("-images.epub")) {
            return fileName.substring(0, fileName.length() - "-images.epub".length());
        }
        return fileName;
    }

    private String resolveErrorMessage(Exception e) {
        if (e instanceof GeneralException generalException) {
            return generalException.getErrorReasonHttpStatus().getMessage();
        }
        return e.getMessage();
    }

    private String resolveTopStackLocation(Exception e) {
        if (e.getStackTrace().length == 0) {
            return null;
        }
        StackTraceElement top = e.getStackTrace()[0];
        return top.getClassName() + ":" + top.getLineNumber();
    }

    private int countCombinedSections(String combinedXhtml) {
        return combinedXhtml.split("<section data-chapter-index=\"", -1).length - 1;
    }

    private List<String> invalidChapterTitles(JsonNode meta) {
        List<String> invalidTitles = new ArrayList<>();
        for (JsonNode chapterNode : meta.path("chapters")) {
            String title = chapterNode.path("title").asText();
            String normalized = title == null ? "" : title.toLowerCase().replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()
                    || normalized.equals("contents")
                    || normalized.startsWith("contents ")
                    || normalized.contains("project gutenberg license")
                    || normalized.equals("index")
                    || normalized.startsWith("index ")) {
                invalidTitles.add(title);
            }
        }
        return invalidTitles;
    }

    private List<String> structuralIssues(JsonNode meta) {
        List<String> issues = new ArrayList<>();
        int expectedStartPos = 0;

        for (JsonNode chapterNode : meta.path("chapters")) {
            int chapterIndex = chapterNode.path("chapterIndex").asInt();
            int startPos = chapterNode.path("startPos").asInt();
            int endPos = chapterNode.path("endPos").asInt();
            int totalCodePoints = chapterNode.path("totalCodePoints").asInt();
            JsonNode starts = chapterNode.path("paragraphStarts");
            JsonNode lengths = chapterNode.path("paragraphLengths");
            int paragraphCount = chapterNode.path("paragraphCount").asInt();

            if (paragraphCount <= 0 || totalCodePoints <= 0) {
                issues.add("chapter " + chapterIndex + " is empty");
            }
            if (paragraphCount != starts.size() || paragraphCount != lengths.size()) {
                issues.add("chapter " + chapterIndex + " paragraph count mismatch");
            }
            if (startPos != expectedStartPos) {
                issues.add("chapter " + chapterIndex + " startPos mismatch: expected " + expectedStartPos + " but was " + startPos);
            }
            if (endPos != startPos + totalCodePoints - 1) {
                issues.add("chapter " + chapterIndex + " endPos mismatch");
            }

            int expectedParagraphStart = 0;
            int paragraphTotal = 0;
            for (int i = 0; i < Math.min(starts.size(), lengths.size()); i++) {
                int actualParagraphStart = starts.get(i).asInt();
                int paragraphLength = lengths.get(i).asInt();
                if (actualParagraphStart != expectedParagraphStart) {
                    issues.add("chapter " + chapterIndex + " paragraph start mismatch at index " + i);
                    break;
                }
                if (paragraphLength < 0) {
                    issues.add("chapter " + chapterIndex + " paragraph length is negative at index " + i);
                    break;
                }
                expectedParagraphStart += paragraphLength;
                paragraphTotal += paragraphLength;
            }

            if (paragraphTotal != totalCodePoints) {
                issues.add("chapter " + chapterIndex + " paragraph total mismatch");
            }

            expectedStartPos = endPos + 1;
        }

        return issues;
    }
}
