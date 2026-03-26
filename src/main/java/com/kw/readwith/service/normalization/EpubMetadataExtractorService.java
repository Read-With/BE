package com.kw.readwith.service.normalization;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
public class EpubMetadataExtractorService {

    public ExtractedEpubMetadata extract(MultipartFile epubFile) {
        Path tempFile = copyToTempFile(epubFile);

        try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
            String packagePath = resolvePackagePath(zipFile);
            return readPackageMetadata(zipFile, packagePath);
        } catch (ZipException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Invalid EPUB file.");
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to read EPUB metadata.");
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private Path copyToTempFile(MultipartFile epubFile) {
        try {
            Path tempFile = Files.createTempFile("readwith-upload-", ".epub");
            try (InputStream inputStream = epubFile.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to prepare EPUB upload.");
        }
    }

    private ExtractedEpubMetadata readPackageMetadata(ZipFile zipFile, String packagePath) throws IOException {
        ZipEntry packageEntry = findEntry(zipFile, packagePath);
        if (packageEntry == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB package document is missing.");
        }

        try (InputStream inputStream = zipFile.getInputStream(packageEntry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            NodeList nodes = document.getElementsByTagName("*");

            String title = null;
            String author = null;
            String language = null;

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (title == null && matchesLocalName(node, "title")) {
                    title = normalize(node.getTextContent());
                }
                if (author == null && matchesLocalName(node, "creator")) {
                    author = normalize(node.getTextContent());
                }
                if (language == null && matchesLocalName(node, "language")) {
                    language = normalize(node.getTextContent());
                }
            }

            return new ExtractedEpubMetadata(blankToNull(title), blankToNull(author), blankToNull(language));
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB package metadata could not be parsed.");
        }
    }

    private String resolvePackagePath(ZipFile zipFile) throws IOException {
        ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
        if (containerEntry == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB container.xml is missing.");
        }

        try (InputStream inputStream = zipFile.getInputStream(containerEntry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            NodeList rootFiles = document.getElementsByTagName("rootfile");
            if (rootFiles.getLength() == 0) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB rootfile path is missing.");
            }
            Node pathNode = rootFiles.item(0).getAttributes().getNamedItem("full-path");
            if (pathNode == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB rootfile path is missing.");
            }
            return pathNode.getNodeValue();
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "EPUB container.xml could not be parsed.");
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

    private String normalize(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }
}
