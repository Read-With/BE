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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
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
            String title = findFirstTextByLocalName(document.getElementsByTagName("*"), "title");
            String author = findFirstTextByLocalName(document.getElementsByTagName("*"), "creator");
            String language = findFirstTextByLocalName(document.getElementsByTagName("*"), "language");
            ExtractedEpubCover cover = extractCover(zipFile, packagePath, document);

            return new ExtractedEpubMetadata(blankToNull(title), blankToNull(author), blankToNull(language), cover);
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

    private ExtractedEpubCover extractCover(ZipFile zipFile,
                                            String packagePath,
                                            org.w3c.dom.Document packageDocument) {
        Map<String, ManifestItem> manifestItems = readManifestItems(packageDocument, packagePath);

        ManifestItem manifestCover = resolveCoverManifestItem(packageDocument, manifestItems);
        if (manifestCover != null) {
            ExtractedEpubCover cover = loadCoverFromManifestItem(zipFile, manifestItems, manifestCover);
            if (cover != null) {
                return cover;
            }
        }

        String guideHref = resolveGuideCoverHref(packageDocument);
        if (guideHref != null) {
            return loadCoverFromHref(zipFile, packagePath, guideHref, manifestItems);
        }

        return null;
    }

    private Map<String, ManifestItem> readManifestItems(org.w3c.dom.Document packageDocument, String packagePath) {
        Map<String, ManifestItem> manifestItems = new HashMap<>();
        String packageDir = packageDirectory(packagePath);
        NodeList nodes = packageDocument.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!matchesLocalName(node, "item")) {
                continue;
            }
            String id = attributeValue(node, "id");
            String href = attributeValue(node, "href");
            String mediaType = attributeValue(node, "media-type");
            String properties = attributeValue(node, "properties");
            if (id == null || href == null) {
                continue;
            }
            manifestItems.put(id, new ManifestItem(
                    id,
                    resolveRelativePath(packageDir, href),
                    blankToNull(mediaType),
                    properties == null ? "" : properties
            ));
        }
        return manifestItems;
    }

    private ManifestItem resolveCoverManifestItem(org.w3c.dom.Document packageDocument, Map<String, ManifestItem> manifestItems) {
        String coverItemId = resolveCoverItemId(packageDocument);
        if (coverItemId != null && manifestItems.containsKey(coverItemId)) {
            return manifestItems.get(coverItemId);
        }

        for (ManifestItem item : manifestItems.values()) {
            if (item.hasProperty("cover-image")) {
                return item;
            }
        }

        for (ManifestItem item : manifestItems.values()) {
            if (item.isImage() && item.looksLikeCover()) {
                return item;
            }
        }

        return null;
    }

    private String resolveCoverItemId(org.w3c.dom.Document packageDocument) {
        NodeList nodes = packageDocument.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!matchesLocalName(node, "meta")) {
                continue;
            }
            String name = attributeValue(node, "name");
            if (!"cover".equalsIgnoreCase(name)) {
                continue;
            }
            return blankToNull(attributeValue(node, "content"));
        }
        return null;
    }

    private String resolveGuideCoverHref(org.w3c.dom.Document packageDocument) {
        NodeList nodes = packageDocument.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!matchesLocalName(node, "reference")) {
                continue;
            }
            String type = attributeValue(node, "type");
            if (!"cover".equalsIgnoreCase(type)) {
                continue;
            }
            return blankToNull(attributeValue(node, "href"));
        }
        return null;
    }

    private ExtractedEpubCover loadCoverFromManifestItem(ZipFile zipFile,
                                                         Map<String, ManifestItem> manifestItems,
                                                         ManifestItem manifestItem) {
        if (manifestItem.isImage()) {
            return loadBinaryCover(zipFile, manifestItem.href(), manifestItem.mediaType());
        }

        if (manifestItem.isXhtml()) {
            return loadCoverFromContentDocument(zipFile, manifestItem.href(), manifestItems);
        }

        return null;
    }

    private ExtractedEpubCover loadCoverFromHref(ZipFile zipFile,
                                                 String basePath,
                                                 String href,
                                                 Map<String, ManifestItem> manifestItems) {
        String normalizedHref = stripFragment(href);
        if (isExternalReference(normalizedHref)) {
            return null;
        }
        String resolvedPath = resolveRelativePath(packageDirectory(basePath), normalizedHref);
        ManifestItem manifestItem = findManifestItemByHref(manifestItems, resolvedPath);
        String mediaType = manifestItem == null ? detectContentTypeFromPath(resolvedPath) : manifestItem.mediaType();

        if (isImageMediaType(mediaType)) {
            return loadBinaryCover(zipFile, resolvedPath, mediaType);
        }

        if (isXhtmlMediaType(mediaType)) {
            return loadCoverFromContentDocument(zipFile, resolvedPath, manifestItems);
        }

        return null;
    }

    private ExtractedEpubCover loadCoverFromContentDocument(ZipFile zipFile,
                                                            String contentDocumentPath,
                                                            Map<String, ManifestItem> manifestItems) {
        ZipEntry entry = findEntry(zipFile, contentDocumentPath);
        if (entry == null) {
            return null;
        }

        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            org.w3c.dom.Document document = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            NodeList nodes = document.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (matchesLocalName(node, "img")) {
                    String src = blankToNull(attributeValue(node, "src"));
                    if (src != null) {
                        return loadCoverFromHref(zipFile, contentDocumentPath, src, manifestItems);
                    }
                }
                if (matchesLocalName(node, "image")) {
                    String href = blankToNull(attributeValue(node, "href"));
                    if (href == null) {
                        href = blankToNull(attributeValue(node, "xlink:href"));
                    }
                    if (href != null) {
                        return loadCoverFromHref(zipFile, contentDocumentPath, href, manifestItems);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ExtractedEpubCover loadBinaryCover(ZipFile zipFile, String entryPath, String mediaType) {
        ZipEntry entry = findEntry(zipFile, entryPath);
        if (entry == null) {
            return null;
        }

        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return new ExtractedEpubCover(fileName(entryPath), mediaType, bytes);
        } catch (IOException e) {
            return null;
        }
    }

    private ManifestItem findManifestItemByHref(Map<String, ManifestItem> manifestItems, String href) {
        for (ManifestItem manifestItem : manifestItems.values()) {
            if (Objects.equals(manifestItem.href(), href)) {
                return manifestItem;
            }
        }
        return null;
    }

    private String findFirstTextByLocalName(NodeList nodes, String localName) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (matchesLocalName(node, localName)) {
                return normalize(node.getTextContent());
            }
        }
        return null;
    }

    private boolean matchesLocalName(Node node, String localName) {
        return localName.equalsIgnoreCase(node.getLocalName()) || localName.equalsIgnoreCase(node.getNodeName());
    }

    private String attributeValue(Node node, String attributeName) {
        if (node.getAttributes() == null) {
            return null;
        }
        Node attributeNode = node.getAttributes().getNamedItem(attributeName);
        if (attributeNode != null) {
            return attributeNode.getNodeValue();
        }
        for (int i = 0; i < node.getAttributes().getLength(); i++) {
            Node candidate = node.getAttributes().item(i);
            if (candidate == null) {
                continue;
            }
            String nodeName = candidate.getNodeName();
            String localName = candidate.getLocalName();
            if (attributeName.equalsIgnoreCase(nodeName) || attributeName.equalsIgnoreCase(localName)) {
                return candidate.getNodeValue();
            }
        }
        return null;
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

    private String packageDirectory(String packagePath) {
        Path parent = Paths.get(packagePath).getParent();
        if (parent == null) {
            return "";
        }
        return parent.toString().replace("\\", "/");
    }

    private String resolveRelativePath(String baseDirectory, String href) {
        String normalizedHref = href.replace("\\", "/");
        if (normalizedHref.startsWith("/")) {
            return normalizedHref.substring(1);
        }
        Path basePath = baseDirectory == null || baseDirectory.isBlank()
                ? Paths.get("")
                : Paths.get(baseDirectory);
        return basePath.resolve(normalizedHref).normalize().toString().replace("\\", "/");
    }

    private String detectContentTypeFromPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "application/xhtml+xml";
        }
        return null;
    }

    private boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private boolean isXhtmlMediaType(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String lower = mediaType.toLowerCase(Locale.ROOT);
        return "application/xhtml+xml".equals(lower) || "text/html".equals(lower);
    }

    private String fileName(String path) {
        Path filePath = Paths.get(path);
        Path fileName = filePath.getFileName();
        return fileName == null ? "cover" : fileName.toString();
    }

    private String stripFragment(String href) {
        int fragmentIndex = href.indexOf('#');
        if (fragmentIndex < 0) {
            return href;
        }
        return href.substring(0, fragmentIndex);
    }

    private boolean isExternalReference(String href) {
        String lower = href.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("data:")
                || lower.startsWith("mailto:");
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

    private record ManifestItem(
            String id,
            String href,
            String mediaType,
            String properties
    ) {
        private boolean hasProperty(String property) {
            for (String candidate : properties.split("\\s+")) {
                if (property.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isImage() {
            return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/");
        }

        private boolean isXhtml() {
            if (mediaType == null) {
                return false;
            }
            String lower = mediaType.toLowerCase(Locale.ROOT);
            return "application/xhtml+xml".equals(lower) || "text/html".equals(lower);
        }

        private boolean looksLikeCover() {
            String idLower = id.toLowerCase(Locale.ROOT);
            String hrefLower = href.toLowerCase(Locale.ROOT);
            return idLower.contains("cover") || hrefLower.contains("cover");
        }
    }
}
