package com.kw.readwith.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.service.normalization.NormalizationPipelineResult;
import com.kw.readwith.service.normalization.NormalizationPipelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationPipelineServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final NormalizationPipelineService normalizationPipelineService =
            new NormalizationPipelineService(objectMapper);

    @Test
    @DisplayName("normalize builds sanitized chapter artifacts and validation report")
    void normalizeEpubBuildsArtifacts() throws Exception {
        Path epubFile = createSampleEpub();

        try {
            NormalizationPipelineResult result = normalizationPipelineService.normalize(
                    epubFile,
                    42L,
                    "run-1",
                    "rule-v1",
                    "locator-v2"
            );

            assertThat(result.getChapters()).hasSize(2);
            assertThat(result.getCombinedXhtml()).contains("Chapter One");
            assertThat(result.getCombinedXhtml()).contains("Second paragraph");
            assertThat(result.getCombinedXhtml()).doesNotContain("<script");
            assertThat(result.getCombinedXhtml()).doesNotContain("https://evil.example");

            JsonNode meta = objectMapper.readTree(result.getMetaJson());
            assertThat(meta.get("bookId").asLong()).isEqualTo(42L);
            assertThat(meta.get("chapters")).hasSize(2);
            assertThat(meta.get("chapters").get(0).get("chapterIndex").asInt()).isEqualTo(1);
            assertThat(meta.get("chapters").get(1).get("paragraphCount").asInt()).isEqualTo(2);

            JsonNode report = objectMapper.readTree(result.getValidationReportJson());
            assertThat(report.get("status").asText()).isEqualTo("PASS");
            assertThat(report.get("roundTripFailures").asInt()).isZero();
        } finally {
            Files.deleteIfExists(epubFile);
        }
    }

    private Path createSampleEpub() throws IOException {
        Path epubFile = Files.createTempFile("normalization-pipeline-test-", ".epub");

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(epubFile))) {
            writeZipEntry(zipOutputStream, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);

            writeZipEntry(zipOutputStream, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="chap1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chap2" href="chap2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chap1"/>
                        <itemref idref="chap2"/>
                      </spine>
                    </package>
                    """);

            writeZipEntry(zipOutputStream, "OEBPS/chap1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter One</title></head>
                      <body>
                        <h1>Chapter One</h1>
                        <p>First paragraph.</p>
                        <p onclick="alert('x')">Second paragraph. <a href="https://evil.example">bad link</a></p>
                        <script>alert('x')</script>
                      </body>
                    </html>
                    """);

            writeZipEntry(zipOutputStream, "OEBPS/chap2.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter Two</title></head>
                      <body>
                        <h1>Chapter Two</h1>
                        <p>Last paragraph.</p>
                      </body>
                    </html>
                    """);
        }

        return epubFile;
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
