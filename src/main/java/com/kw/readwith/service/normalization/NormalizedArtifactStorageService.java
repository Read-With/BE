package com.kw.readwith.service.normalization;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.ArtifactStorageProperties;
import com.kw.readwith.domain.enums.NormalizationFailureCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NormalizedArtifactStorageService {

    private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final AmazonS3Manager amazonS3Manager;
    private final ArtifactStorageProperties artifactStorageProperties;

    public String newSourceVersion() {
        return RUN_ID_FORMATTER.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String newNormalizationRunId() {
        return RUN_ID_FORMATTER.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String storeSourceEpub(Long bookId, String sourceVersion, MultipartFile file) {
        String relativePath = buildSourceRelativePath(bookId, sourceVersion);
        try {
            amazonS3Manager.uploadFile(privateKey(relativePath), file);
            return relativePath;
        } catch (RuntimeException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.SOURCE_STORE_FAILED,
                    "storing_source",
                    "Failed to store uploaded EPUB source.",
                    e
            );
        }
    }

    public byte[] loadPrivateObject(String relativePath) {
        try {
            return amazonS3Manager.downloadFile(privateKey(relativePath));
        } catch (RuntimeException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.SOURCE_DOWNLOAD_FAILED,
                    "downloading_source",
                    "Failed to download normalization source artifact.",
                    e
            );
        }
    }

    public String storeNormalizationArtifacts(Long bookId, String runId, NormalizationPipelineResult result) {
        String artifactRoot = buildArtifactRoot(bookId, runId);
        try {
            amazonS3Manager.uploadBytes(
                    publicKey(artifactRoot + "/combined.xhtml"),
                    result.getCombinedXhtml().getBytes(StandardCharsets.UTF_8),
                    "application/xhtml+xml"
            );

            amazonS3Manager.uploadBytes(
                    privateKey(artifactRoot + "/meta.json"),
                    result.getMetaJson().getBytes(StandardCharsets.UTF_8),
                    "application/json"
            );

            amazonS3Manager.uploadBytes(
                    privateKey(artifactRoot + "/reports/validation_report.json"),
                    result.getValidationReportJson().getBytes(StandardCharsets.UTF_8),
                    "application/json"
            );

            for (NormalizedChapterArtifact chapter : result.getChapters()) {
                String chapterFileName = String.format("chapter_%03d", chapter.getChapterIndex());
                amazonS3Manager.uploadBytes(
                        privateKey(artifactRoot + "/normalized/" + chapterFileName + ".xhtml"),
                        chapter.getNormalizedXhtml().getBytes(StandardCharsets.UTF_8),
                        "application/xhtml+xml"
                );
                amazonS3Manager.uploadBytes(
                        privateKey(artifactRoot + "/text/" + chapterFileName + ".txt"),
                        chapter.getRawText().getBytes(StandardCharsets.UTF_8),
                        "text/plain"
                );
            }
        } catch (RuntimeException e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.ARTIFACT_UPLOAD_FAILED,
                    "storing_artifacts",
                    "Failed to store normalization artifacts.",
                    e
            );
        }

        return artifactRoot;
    }

    public String resolveCombinedXhtmlUrl(String artifactRoot) {
        String publicRelativePath = artifactRoot + "/combined.xhtml";
        String cloudFrontBaseUrl = trimTrailingSlash(artifactStorageProperties.getCloudFrontBaseUrl());
        if (!cloudFrontBaseUrl.isBlank()) {
            return cloudFrontBaseUrl + "/" + publicKey(publicRelativePath);
        }
        return amazonS3Manager.getObjectUrl(publicKey(publicRelativePath));
    }

    private String buildSourceRelativePath(Long bookId, String sourceVersion) {
        return "books/" + bookId + "/source/" + sourceVersion + "/book.epub";
    }

    private String buildArtifactRoot(Long bookId, String runId) {
        return "books/" + bookId + "/normalizations/" + runId;
    }

    private String publicKey(String relativePath) {
        return artifactStorageProperties.getPublicPrefix() + "/" + relativePath;
    }

    private String privateKey(String relativePath) {
        return artifactStorageProperties.getPrivatePrefix() + "/" + relativePath;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
