package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.ArtifactStorageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.dto.admin.AnalysisInputChapterDTO;
import com.kw.readwith.dto.admin.AnalysisInputResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisInputExportService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final AmazonS3Manager amazonS3Manager;
    private final ArtifactStorageProperties artifactStorageProperties;

    public AnalysisInputResponseDTO getAnalysisInput(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        if (!book.isNormalizationReady() || book.getNormalizedArtifactPath() == null || book.getNormalizedArtifactPath().isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "정규화가 완료된 도서만 analysis input을 조회할 수 있습니다.");
        }

        List<Chapter> chapters = chapterRepository.findByBookId(bookId).stream()
                .sorted(Comparator.comparingInt(Chapter::getIdx))
                .toList();
        if (chapters.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "정규화 챕터 산출물이 아직 준비되지 않았습니다.");
        }

        Duration ttl = artifactStorageProperties.getPrivateUrlTtl();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(ttl);
        String artifactRoot = book.getNormalizedArtifactPath();

        return AnalysisInputResponseDTO.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .language(book.getLanguage())
                .normalizationRunId(book.getNormalizationRunId())
                .normalizedArtifactPath(artifactRoot)
                .ruleVersion(book.getRuleVersion())
                .locatorVersion(book.getLocatorVersion())
                .metaUrl(presignPrivateObject(artifactRoot + "/meta.json", ttl))
                .expiresAt(expiresAt)
                .chapters(chapters.stream()
                        .map(chapter -> AnalysisInputChapterDTO.builder()
                                .chapterIndex(chapter.getIdx())
                                .title(chapter.getTitle())
                                .txtUrl(presignPrivateObject(
                                        artifactRoot + "/text/" + String.format("chapter_%03d.txt", chapter.getIdx()),
                                        ttl
                                ))
                                .build())
                        .toList())
                .build();
    }

    private String presignPrivateObject(String relativePath, Duration ttl) {
        String key = artifactStorageProperties.getPrivatePrefix() + "/" + relativePath;
        return amazonS3Manager.generatePresignedGetUrl(key, ttl);
    }
}
