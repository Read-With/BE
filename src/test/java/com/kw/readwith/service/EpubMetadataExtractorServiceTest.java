package com.kw.readwith.service;

import com.kw.readwith.service.normalization.EpubMetadataExtractorService;
import com.kw.readwith.service.normalization.ExtractedEpubCover;
import com.kw.readwith.service.normalization.ExtractedEpubMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EpubMetadataExtractorServiceTest {

    private final EpubMetadataExtractorService epubMetadataExtractorService = new EpubMetadataExtractorService();

    @Test
    @DisplayName("EPUB package metadata extracts title author and language")
    void extractMetadataFromPackageDocument() throws Exception {
        MockMultipartFile epubFile = new MockMultipartFile(
                "file",
                "sample.epub",
                "application/epub+zip",
                createSampleEpub("""
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                          <dc:title>Dracula</dc:title>
                          <dc:creator>Bram Stoker</dc:creator>
                          <dc:language>en</dc:language>
                        </metadata>
                        """)
        );

        ExtractedEpubMetadata metadata = epubMetadataExtractorService.extract(epubFile);

        assertThat(metadata.title()).isEqualTo("Dracula");
        assertThat(metadata.author()).isEqualTo("Bram Stoker");
        assertThat(metadata.language()).isEqualTo("en");
        assertThat(metadata.cover()).isNull();
    }

    @Test
    @DisplayName("EPUB package metadata keeps missing fields null")
    void extractMetadataAllowsPartialValues() throws Exception {
        MockMultipartFile epubFile = new MockMultipartFile(
                "file",
                "sample.epub",
                "application/epub+zip",
                createSampleEpub("""
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                          <dc:title>Little Women</dc:title>
                        </metadata>
                        """)
        );

        ExtractedEpubMetadata metadata = epubMetadataExtractorService.extract(epubFile);

        assertThat(metadata.title()).isEqualTo("Little Women");
        assertThat(metadata.author()).isNull();
        assertThat(metadata.language()).isNull();
        assertThat(metadata.cover()).isNull();
    }

    @Test
    @DisplayName("EPUB package metadata extracts cover image when manifest declares cover-image")
    void extractMetadataIncludesCoverImage() throws Exception {
        byte[] coverBytes = "cover-image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile epubFile = new MockMultipartFile(
                "file",
                "sample.epub",
                "application/epub+zip",
                createSampleEpub(
                        """
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                          <dc:title>Dracula</dc:title>
                          <dc:creator>Bram Stoker</dc:creator>
                          <dc:language>en</dc:language>
                          <meta name="cover" content="cover-image"/>
                        </metadata>
                        """,
                        """
                        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                        <item id="chap1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
                        """,
                        coverBytes
                )
        );

        ExtractedEpubMetadata metadata = epubMetadataExtractorService.extract(epubFile);

        assertThat(metadata.title()).isEqualTo("Dracula");
        assertThat(metadata.author()).isEqualTo("Bram Stoker");
        assertThat(metadata.language()).isEqualTo("en");
        assertThat(metadata.cover()).isNotNull();

        ExtractedEpubCover cover = metadata.cover();
        assertThat(cover.fileName()).isEqualTo("cover.jpg");
        assertThat(cover.contentType()).isEqualTo("image/jpeg");
        assertThat(cover.bytes()).isEqualTo(coverBytes);
    }

    private byte[] createSampleEpub(String metadataXml) throws IOException {
        return createSampleEpub(
                metadataXml,
                """
                <item id="chap1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
                """,
                null
        );
    }

    private byte[] createSampleEpub(String metadataXml, String manifestItemsXml, byte[] coverBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
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
                    """ + metadataXml + """
                      <manifest>
                        """ + manifestItemsXml + """
                      </manifest>
                      <spine>
                        <itemref idref="chap1"/>
                      </spine>
                    </package>
                    """);

            writeZipEntry(zipOutputStream, "OEBPS/chap1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Sample</title></head>
                      <body><p>Sample text.</p></body>
                    </html>
                    """);

            if (coverBytes != null) {
                writeZipEntry(zipOutputStream, "OEBPS/images/cover.jpg", coverBytes);
            }
        }

        return outputStream.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        writeZipEntry(zipOutputStream, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }
}
