package com.kw.readwith.service;

import com.kw.readwith.config.AmazonConfig;
import com.kw.readwith.config.ArtifactStorageProperties;
import com.kw.readwith.config.CharacterImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CdnUrlService {

    private final ArtifactStorageProperties artifactStorageProperties;
    private final AmazonConfig amazonConfig;
    private final CharacterImageProperties characterImageProperties;

    public String toPublicUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String cloudFrontBaseUrl = trimTrailingSlash(artifactStorageProperties.getCloudFrontBaseUrl());
        if (cloudFrontBaseUrl.isBlank()) {
            return value;
        }

        String normalized = value.trim();
        if (normalized.equals(cloudFrontBaseUrl) || normalized.startsWith(cloudFrontBaseUrl + "/")) {
            return normalized;
        }

        return resolvePublicObjectKey(normalized)
                .map(key -> cloudFrontBaseUrl + "/" + key)
                .orElse(normalized);
    }

    public boolean isPublicObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return isAllowedPublicKey(stripLeadingSlash(value.trim()));
    }

    private Optional<String> resolvePublicObjectKey(String value) {
        String keyCandidate = stripLeadingSlash(value);
        if (isAllowedPublicKey(keyCandidate)) {
            return Optional.of(keyCandidate);
        }

        Optional<String> s3Key = extractS3Key(value);
        return s3Key.filter(this::isAllowedPublicKey);
    }

    private Optional<String> extractS3Key(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }

            String host = uri.getHost();
            String path = stripLeadingSlash(uri.getPath());
            String bucket = amazonConfig.getBucket();
            String region = amazonConfig.getRegion();
            if (host == null || path.isBlank() || bucket == null || bucket.isBlank()) {
                return Optional.empty();
            }

            if (host.equals(bucket + ".s3.amazonaws.com")
                    || host.equals(bucket + ".s3." + region + ".amazonaws.com")
                    || host.startsWith(bucket + ".s3.")) {
                return Optional.of(path);
            }

            if ((host.equals("s3.amazonaws.com") || host.startsWith("s3."))
                    && path.startsWith(bucket + "/")) {
                return Optional.of(path.substring(bucket.length() + 1));
            }

            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isAllowedPublicKey(String key) {
        String publicPrefix = normalizePrefix(artifactStorageProperties.getPublicPrefix(), "public");
        String characterImagePrefix = normalizePrefix(characterImageProperties.getS3Path(), "character-images");

        return key.equals(publicPrefix)
                || key.startsWith(publicPrefix + "/")
                || key.equals(characterImagePrefix)
                || key.startsWith(characterImagePrefix + "/");
    }

    private String normalizePrefix(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return stripSlashes(normalized);
    }

    private String stripLeadingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String stripSlashes(String value) {
        String result = stripLeadingSlash(value);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
