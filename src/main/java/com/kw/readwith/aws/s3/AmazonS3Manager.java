package com.kw.readwith.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.kw.readwith.config.AmazonConfig;
import com.kw.readwith.service.CdnUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonS3Manager {

    private final AmazonS3 amazonS3;
    private final AmazonConfig amazonConfig;
    private final CdnUrlService cdnUrlService;

    public String uploadFile(String keyName, MultipartFile file) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(amazonConfig.getBucket(), keyName, inputStream, metadata));
        } catch (IOException e) {
            throw new IllegalStateException("S3 파일 업로드에 실패했습니다. key=" + keyName, e);
        }
        return getObjectUrl(keyName);
    }

    public void uploadBytes(String keyName, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            amazonS3.putObject(new PutObjectRequest(amazonConfig.getBucket(), keyName, inputStream, metadata));
        } catch (IOException e) {
            throw new IllegalStateException("S3 바이트 업로드에 실패했습니다. key=" + keyName, e);
        }
    }

    public byte[] downloadFile(String keyName) {
        try (InputStream inputStream = amazonS3.getObject(amazonConfig.getBucket(), keyName).getObjectContent()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("S3 파일 다운로드에 실패했습니다. key=" + keyName, e);
        }
    }

    public <T> T readObject(String keyName, S3ObjectReader<T> reader) throws IOException {
        try (S3Object object = amazonS3.getObject(amazonConfig.getBucket(), keyName);
             InputStream inputStream = object.getObjectContent()) {
            return reader.read(inputStream);
        }
    }

    public List<String> listKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(amazonConfig.getBucket())
                .withPrefix(prefix);

        ListObjectsV2Result result;
        do {
            result = amazonS3.listObjectsV2(request);
            result.getObjectSummaries().forEach(summary -> keys.add(summary.getKey()));
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());

        return keys;
    }

    public void deleteKeys(List<String> keyNames) {
        if (keyNames == null || keyNames.isEmpty()) {
            return;
        }
        keyNames.forEach(keyName -> amazonS3.deleteObject(amazonConfig.getBucket(), keyName));
    }

    public String getObjectUrl(String keyName) {
        String cdnUrl = cdnUrlService.toPublicUrl(keyName);
        if (!cdnUrl.equals(keyName)) {
            return cdnUrl;
        }
        return amazonS3.getUrl(amazonConfig.getBucket(), keyName).toString();
    }

    public String generatePresignedGetUrl(String keyName, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Presigned URL TTL must be positive.");
        }

        Date expiration = new Date(System.currentTimeMillis() + ttl.toMillis());
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(amazonConfig.getBucket(), keyName)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(request).toString();
    }

    public String uploadFileFromBase64(String keyName, String base64Data, String contentType) {
        base64Data = base64Data.replaceAll("\\s+", "");
        byte[] fileContent = Base64.getDecoder().decode(base64Data);
        MultipartFile multipartFile = new Base64ToMultipartFile(fileContent, keyName, contentType);
        return uploadFile(keyName, multipartFile);
    }

    public String uploadOriginal(String title, MultipartFile file) {
        String slug = slugify(title);
        String ext = getExtension(file.getOriginalFilename());
        String keyName = amazonConfig.getOriginal() + "/" + slug + ext;
        return uploadFile(keyName, file);
    }

    public String uploadMetadata(String title, MultipartFile file) {
        String slug = slugify(title);
        String uniqueName = generateUniqueFileName(file.getOriginalFilename());
        String keyName = amazonConfig.getMetadata() + "/" + slug + "/" + uniqueName;
        return uploadFile(keyName, file);
    }

    private String slugify(String input) {
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.length() > 0 ? slug : UUID.randomUUID().toString();
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx > -1) {
            return filename.substring(idx);
        }
        return "";
    }

    private String generateUniqueFileName(String originalFilename) {
        String ext = "";
        int idx = originalFilename.lastIndexOf('.');
        if (idx > -1) {
            ext = originalFilename.substring(idx);
        }
        return UUID.randomUUID() + ext;
    }

    @FunctionalInterface
    public interface S3ObjectReader<T> {
        T read(InputStream inputStream) throws IOException;
    }
}
