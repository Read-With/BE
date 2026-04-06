package com.kw.readwith.service.normalization;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class TocTreeBuilder {

    TocTree build(ZipFile zipFile, String packagePath, Map<String, EpubManifestItem> manifestById) throws IOException {
        TocTree navTree = buildFromNav(zipFile, manifestById);
        if (navTree != null && !navTree.leafNodes().isEmpty()) {
            return navTree;
        }

        TocTree ncxTree = buildFromNcx(zipFile, manifestById);
        if (ncxTree != null && !ncxTree.leafNodes().isEmpty()) {
            return ncxTree;
        }

        return null;
    }

    private TocTree buildFromNav(ZipFile zipFile, Map<String, EpubManifestItem> manifestById) throws IOException {
        EpubManifestItem navItem = manifestById.values().stream()
                .filter(item -> item.hasProperty("nav"))
                .findFirst()
                .orElse(null);
        if (navItem == null) {
            return null;
        }

        ZipEntry navEntry = findEntry(zipFile, navItem.href());
        if (navEntry == null) {
            return null;
        }

        try (InputStream inputStream = zipFile.getInputStream(navEntry)) {
            Document document = Jsoup.parse(inputStream, null, "", Parser.xmlParser());
            Element tocNav = findTocNav(document);
            if (tocNav == null) {
                return null;
            }

            Element rootList = firstChildTag(tocNav, "ol");
            if (rootList == null) {
                return null;
            }

            AtomicInteger sequence = new AtomicInteger(1);
            List<TocNode> roots = readNavList(
                    rootList,
                    1,
                    null,
                    parentPath(navItem.href()),
                    TocSource.NAV,
                    sequence,
                    List.of()
            );
            return new TocTree(TocSource.NAV, roots);
        }
    }

    private TocTree buildFromNcx(ZipFile zipFile, Map<String, EpubManifestItem> manifestById) throws IOException {
        EpubManifestItem ncxItem = manifestById.values().stream()
                .filter(item -> "application/x-dtbncx+xml".equalsIgnoreCase(item.mediaType()))
                .findFirst()
                .orElse(null);
        if (ncxItem == null) {
            return null;
        }

        ZipEntry ncxEntry = findEntry(zipFile, ncxItem.href());
        if (ncxEntry == null) {
            return null;
        }

        try (InputStream inputStream = zipFile.getInputStream(ncxEntry)) {
            org.w3c.dom.Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
            Node navMap = findFirstByLocalName(document.getDocumentElement().getChildNodes(), "navMap");
            if (navMap == null) {
                return null;
            }

            AtomicInteger sequence = new AtomicInteger(1);
            List<TocNode> roots = readNcxNavPoints(
                    navMap.getChildNodes(),
                    1,
                    null,
                    parentPath(ncxItem.href()),
                    sequence,
                    List.of()
            );
            return new TocTree(TocSource.NCX, roots);
        } catch (Exception e) {
            return null;
        }
    }

    private List<TocNode> readNavList(
            Element listElement,
            int depth,
            String parentNodeId,
            String baseDir,
            TocSource source,
            AtomicInteger sequence,
            List<String> parentPath
    ) {
        List<TocNode> nodes = new ArrayList<>();
        for (Element child : listElement.children()) {
            if (!"li".equalsIgnoreCase(child.tagName())) {
                continue;
            }

            Element link = firstChildTag(child, "a");
            Element labelElement = link != null ? link : firstChildTag(child, "span");
            Element nestedList = firstChildTag(child, "ol");
            String title = normalizeWhitespace(labelElement != null ? labelElement.text() : child.ownText());
            String href = link != null ? normalizeWhitespace(link.attr("href")) : null;

            String nodeId = source.name().toLowerCase(Locale.ROOT) + "-" + sequence.getAndIncrement();
            List<String> titlePath = extendPath(parentPath, title);
            List<TocNode> children = nestedList == null
                    ? List.of()
                    : readNavList(nestedList, depth + 1, nodeId, baseDir, source, sequence, titlePath);

            if ((title == null || title.isBlank()) && children.isEmpty()) {
                continue;
            }

            nodes.add(new TocNode(
                    nodeId,
                    title,
                    href,
                    resolveSourceDocHref(baseDir, href),
                    extractFragmentId(href),
                    depth,
                    parentNodeId,
                    children,
                    attributeValue(child, "epub:type"),
                    source,
                    titlePath
            ));
        }
        return nodes;
    }

    private List<TocNode> readNcxNavPoints(
            NodeList nodeList,
            int depth,
            String parentNodeId,
            String baseDir,
            AtomicInteger sequence,
            List<String> parentPath
    ) {
        List<TocNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (!matchesLocalName(node, "navPoint")) {
                continue;
            }

            String title = resolveNcxTitle(node);
            String href = resolveNcxHref(node);
            String nodeId = "ncx-" + sequence.getAndIncrement();
            List<String> titlePath = extendPath(parentPath, title);
            List<TocNode> children = readNcxNavPoints(node.getChildNodes(), depth + 1, nodeId, baseDir, sequence, titlePath);
            nodes.add(new TocNode(
                    nodeId,
                    title,
                    href,
                    resolveSourceDocHref(baseDir, href),
                    extractFragmentId(href),
                    depth,
                    parentNodeId,
                    children,
                    null,
                    TocSource.NCX,
                    titlePath
            ));
        }
        return nodes;
    }

    private Element findTocNav(Document document) {
        for (Element nav : document.select("nav")) {
            if (containsToken(attributeValue(nav, "epub:type"), "toc") || containsToken(attributeValue(nav, "type"), "toc")) {
                return nav;
            }
        }
        return document.selectFirst("nav");
    }

    private String resolveNcxTitle(Node navPoint) {
        Node navLabel = findFirstByLocalName(navPoint.getChildNodes(), "navLabel");
        if (navLabel == null) {
            return null;
        }
        Node textNode = findFirstByLocalName(navLabel.getChildNodes(), "text");
        return textNode == null ? null : normalizeWhitespace(textNode.getTextContent());
    }

    private String resolveNcxHref(Node navPoint) {
        Node content = findFirstByLocalName(navPoint.getChildNodes(), "content");
        if (content == null || content.getAttributes() == null || content.getAttributes().getNamedItem("src") == null) {
            return null;
        }
        return normalizeWhitespace(content.getAttributes().getNamedItem("src").getNodeValue());
    }

    private Node findFirstByLocalName(NodeList nodeList, String localName) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (matchesLocalName(node, localName)) {
                return node;
            }
        }
        return null;
    }

    private boolean matchesLocalName(Node node, String localName) {
        return localName.equalsIgnoreCase(node.getLocalName()) || localName.equalsIgnoreCase(node.getNodeName());
    }

    private DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory;
    }

    private Element firstChildTag(Element parent, String tagName) {
        for (Element child : parent.children()) {
            if (tagName.equalsIgnoreCase(child.tagName())) {
                return child;
            }
        }
        return null;
    }

    private String attributeValue(Element element, String key) {
        if (element.hasAttr(key)) {
            return element.attr(key);
        }
        return element.attributes().asList().stream()
                .filter(attribute -> key.equalsIgnoreCase(attribute.getKey()) || attribute.getKey().endsWith(":" + key))
                .map(org.jsoup.nodes.Attribute::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean containsToken(String value, String token) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String candidate : value.split("\\s+")) {
            if (token.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extendPath(List<String> parentPath, String title) {
        List<String> path = new ArrayList<>(parentPath);
        if (title != null && !title.isBlank()) {
            path.add(title);
        }
        return List.copyOf(path);
    }

    private String resolveSourceDocHref(String baseDir, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String withoutFragment = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
        if (withoutFragment.isBlank()) {
            return null;
        }
        return resolvePath(baseDir, withoutFragment);
    }

    private String extractFragmentId(String href) {
        if (href == null || href.isBlank() || !href.contains("#")) {
            return null;
        }
        return href.substring(href.indexOf('#') + 1);
    }

    private String resolvePath(String baseDir, String href) {
        if (baseDir == null || baseDir.isBlank()) {
            return href.replace("\\", "/");
        }
        return Path.of(baseDir).resolve(href).normalize().toString().replace("\\", "/");
    }

    private String parentPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private ZipEntry findEntry(ZipFile zipFile, String normalizedPath) {
        ZipEntry direct = zipFile.getEntry(normalizedPath);
        if (direct != null) {
            return direct;
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (normalizedPath.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    private String normalizeWhitespace(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
    }
}
