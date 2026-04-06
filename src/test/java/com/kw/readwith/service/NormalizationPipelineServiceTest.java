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
import java.util.LinkedHashMap;
import java.util.Map;
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
        Path epubFile = createEpub(Map.of(
                "META-INF/container.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>
                        """,
                "OEBPS/content.opf", """
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
                        """,
                "OEBPS/chap1.xhtml", """
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
                        """,
                "OEBPS/chap2.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Chapter Two</title></head>
                          <body>
                            <h1>Chapter Two</h1>
                            <p>Last paragraph.</p>
                          </body>
                        </html>
                        """
        ));

        try {
            NormalizationPipelineResult result = normalizationPipelineService.normalize(
                    epubFile,
                    42L,
                    "run-1",
                    "rule-v2",
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
            assertThat(report.get("chapterCountWithinLimit").asBoolean()).isTrue();
            assertThat(report.get("combinedSectionCountMatchesChapterCount").asBoolean()).isTrue();
            assertThat(report.get("invalidTitleCount").asInt()).isZero();
        } finally {
            Files.deleteIfExists(epubFile);
        }
    }

    @Test
    @DisplayName("normalize splits multiple fragments in a single XHTML document into canonical chapters")
    void normalizeSplitsNavFragmentsWithinSingleDocument() throws Exception {
        Path epubFile = createEpub(Map.of(
                "META-INF/container.xml", containerXml(),
                "OEBPS/content.opf", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                            <item id="book" href="book.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="book"/>
                          </spine>
                        </package>
                        """,
                "OEBPS/toc.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                          <body>
                            <nav epub:type="toc">
                              <ol>
                                <li><a href="book.xhtml#chapter-1">CHAPTER I</a></li>
                                <li><a href="book.xhtml#chapter-2">CHAPTER II</a></li>
                              </ol>
                            </nav>
                          </body>
                        </html>
                        """,
                "OEBPS/book.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Novel</title></head>
                          <body>
                            <section id="chapter-1">
                              <h2>CHAPTER I</h2>
                              <p>Alpha one.</p>
                              <p>Alpha two.</p>
                            </section>
                            <section id="chapter-2">
                              <h2>CHAPTER II</h2>
                              <p>Beta one.</p>
                              <p>Beta two.</p>
                            </section>
                          </body>
                        </html>
                        """
        ));

        try {
            NormalizationPipelineResult result = normalizationPipelineService.normalize(
                    epubFile,
                    7L,
                    "run-fragments",
                    "rule-v2",
                    "locator-v2"
            );

            assertThat(result.getChapters()).hasSize(2);
            assertThat(result.getChapters().get(0).getTitle()).isEqualTo("CHAPTER I");
            assertThat(result.getChapters().get(1).getTitle()).isEqualTo("CHAPTER II");
            assertThat(result.getChapters().get(0).getSpineHref()).isEqualTo("OEBPS/book.xhtml");
            assertThat(result.getCombinedXhtml()).contains("data-spine-href=\"OEBPS/book.xhtml\"");

            JsonNode meta = objectMapper.readTree(result.getMetaJson());
            assertThat(meta.get("chapters")).hasSize(2);
            assertThat(meta.get("chapters").get(0).get("title").asText()).isEqualTo("CHAPTER I");
            assertThat(meta.get("chapters").get(1).get("title").asText()).isEqualTo("CHAPTER II");

            JsonNode report = objectMapper.readTree(result.getValidationReportJson());
            assertThat(report.get("chapterCountWithinLimit").asBoolean()).isTrue();
            assertThat(report.get("combinedSectionCountMatchesChapterCount").asBoolean()).isTrue();
        } finally {
            Files.deleteIfExists(epubFile);
        }
    }

    @Test
    @DisplayName("normalize promotes scenes to acts for canonical chapters")
    void normalizePromotesScenesToActs() throws Exception {
        Path epubFile = createEpub(Map.of(
                "META-INF/container.xml", containerXml(),
                "OEBPS/content.opf", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                            <item id="play" href="play.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="play"/>
                          </spine>
                        </package>
                        """,
                "OEBPS/toc.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                          <body>
                            <nav epub:type="toc">
                              <ol>
                                <li>
                                  <span>PLAY</span>
                                  <ol>
                                    <li>
                                      <span>ACT I</span>
                                      <ol>
                                        <li><a href="play.xhtml#scene-1">SCENE I</a></li>
                                        <li><a href="play.xhtml#scene-2">SCENE II</a></li>
                                      </ol>
                                    </li>
                                    <li>
                                      <span>ACT II</span>
                                      <ol>
                                        <li><a href="play.xhtml#scene-3">SCENE I</a></li>
                                      </ol>
                                    </li>
                                  </ol>
                                </li>
                              </ol>
                            </nav>
                          </body>
                        </html>
                        """,
                "OEBPS/play.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Play</title></head>
                          <body>
                            <div id="scene-1">
                              <h2>SCENE I</h2>
                              <p>Scene one opening.</p>
                              <p>Scene one continues with enough text to be treated as dramatic content.</p>
                            </div>
                            <div id="scene-2">
                              <h2>SCENE II</h2>
                              <p>Scene two opening.</p>
                              <p>Scene two continues with enough text to remain in the same canonical act.</p>
                            </div>
                            <div id="scene-3">
                              <h2>SCENE I</h2>
                              <p>Act two begins.</p>
                              <p>Act two continues with its own dramatic content.</p>
                            </div>
                          </body>
                        </html>
                        """
        ));

        try {
            NormalizationPipelineResult result = normalizationPipelineService.normalize(
                    epubFile,
                    8L,
                    "run-drama",
                    "rule-v2",
                    "locator-v2"
            );

            assertThat(result.getChapters()).hasSize(2);
            assertThat(result.getChapters().get(0).getTitle()).isEqualTo("ACT I");
            assertThat(result.getChapters().get(1).getTitle()).isEqualTo("ACT II");

            JsonNode report = objectMapper.readTree(result.getValidationReportJson());
            assertThat(report.get("chapterCountWithinLimit").asBoolean()).isTrue();
            assertThat(report.get("invalidTitleCount").asInt()).isZero();
        } finally {
            Files.deleteIfExists(epubFile);
        }
    }

    @Test
    @DisplayName("normalize excludes contents and project gutenberg license from canonical chapters")
    void normalizeExcludesContentsAndLicense() throws Exception {
        Path epubFile = createEpub(Map.of(
                "META-INF/container.xml", containerXml(),
                "OEBPS/content.opf", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <manifest>
                            <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                            <item id="contents" href="contents.xhtml" media-type="application/xhtml+xml"/>
                            <item id="book" href="book.xhtml" media-type="application/xhtml+xml"/>
                            <item id="license" href="license.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine>
                            <itemref idref="contents"/>
                            <itemref idref="book"/>
                            <itemref idref="license"/>
                          </spine>
                        </package>
                        """,
                "OEBPS/toc.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                          <body>
                            <nav epub:type="toc">
                              <ol>
                                <li><a href="contents.xhtml">Contents</a></li>
                                <li><a href="book.xhtml#chapter-1">CHAPTER I</a></li>
                                <li><a href="book.xhtml#chapter-2">CHAPTER II</a></li>
                                <li><a href="license.xhtml">THE FULL PROJECT GUTENBERG LICENSE</a></li>
                              </ol>
                            </nav>
                          </body>
                        </html>
                        """,
                "OEBPS/contents.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Contents</title></head>
                          <body><h1>Contents</h1><p>Skip me.</p></body>
                        </html>
                        """,
                "OEBPS/book.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Book</title></head>
                          <body>
                            <section id="chapter-1"><h2>CHAPTER I</h2><p>Start.</p></section>
                            <section id="chapter-2"><h2>CHAPTER II</h2><p>Next.</p></section>
                          </body>
                        </html>
                        """,
                "OEBPS/license.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>THE FULL PROJECT GUTENBERG LICENSE</title></head>
                          <body><h1>THE FULL PROJECT GUTENBERG LICENSE</h1><p>Skip me too.</p></body>
                        </html>
                        """
        ));

        try {
            NormalizationPipelineResult result = normalizationPipelineService.normalize(
                    epubFile,
                    9L,
                    "run-filtered",
                    "rule-v2",
                    "locator-v2"
            );

            assertThat(result.getChapters()).hasSize(2);
            assertThat(result.getChapters().get(0).getTitle()).isEqualTo("CHAPTER I");
            assertThat(result.getChapters().get(1).getTitle()).isEqualTo("CHAPTER II");

            JsonNode report = objectMapper.readTree(result.getValidationReportJson());
            assertThat(report.get("invalidTitleCount").asInt()).isZero();
        } finally {
            Files.deleteIfExists(epubFile);
        }
    }

    private String containerXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """;
    }

    private Path createEpub(Map<String, String> entries) throws IOException {
        Path epubFile = Files.createTempFile("normalization-pipeline-test-", ".epub");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(epubFile))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(entries).entrySet()) {
                writeZipEntry(zipOutputStream, entry.getKey(), entry.getValue());
            }
        }
        return epubFile;
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
