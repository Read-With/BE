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
                        "rule-v1",
                        "locator-v2"
                );

                JsonNode meta = objectMapper.readTree(result.getMetaJson());
                JsonNode report = objectMapper.readTree(result.getValidationReportJson());

                item.put("success", true);
                item.put("chapterCount", meta.path("chapters").size());
                item.put("totalCodePoints", meta.path("totalCodePoints").asInt());
                item.put("validationStatus", report.path("status").asText());
                item.put("roundTripFailures", report.path("roundTripFailures").asInt());
                item.put("combinedXhtmlLength", result.getCombinedXhtml().length());
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
}
