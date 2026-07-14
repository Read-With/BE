package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.service.BookUploadTransactionService.BookUploadCompletion;
import com.kw.readwith.service.normalization.EpubMetadataExtractorService;
import com.kw.readwith.service.normalization.ExtractedEpubMetadata;
import com.kw.readwith.service.normalization.NormalizationJobDispatcher;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final FavoriteRepository favoriteRepository;
    private final EpubMetadataExtractorService epubMetadataExtractorService;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;
    private final NormalizationJobDispatcher normalizationJobDispatcher;
    private final NormalizationVersionService normalizationVersionService;
    private final BookUploadTransactionService bookUploadTransactionService;
    private final CdnUrlService cdnUrlService;

    public List<BookSummaryDTO> getBooks(String keyword,
                                         String language,
                                         Boolean favoriteOnly,
                                         String sortBy,
                                         Long userId) {
        List<Book> books = bookRepository.findByNormalizationStatus(NormalizationStatus.READY);

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            books = books.stream()
                    .filter(b -> b.getTitle().toLowerCase().contains(lower) || b.getAuthor().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        if (language != null && !language.isBlank()) {
            books = books.stream()
                    .filter(b -> language.equalsIgnoreCase(b.getLanguage()))
                    .collect(Collectors.toList());
        }

        final Set<Long> favoriteBookIds = userId == null
                ? Set.of()
                : favoriteRepository.findByUserId(userId).stream()
                .map(fav -> fav.getBook().getId())
                .collect(Collectors.toSet());

        if (Boolean.TRUE.equals(favoriteOnly)) {
            books = books.stream()
                    .filter(b -> favoriteBookIds.contains(b.getId()))
                    .collect(Collectors.toList());
        }

        books.sort((a, b) -> {
            if ("title".equalsIgnoreCase(sortBy)) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });

        return books.stream()
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(cdnUrlService.toPublicUrl(book.getCoverImgUrl()))
                        .epubPath(book.getEpubPath())
                        .normalizationStatus(enumName(book.getNormalizationStatus()))
                        .analysisStatus(enumName(book.getAnalysisStatus()))
                        .ruleVersion(book.getRuleVersion())
                        .locatorVersion(book.getLocatorVersion())
                        .normalizationRunId(book.getNormalizationRunId())
                        .normalizationVersionStatus(normalizationVersionService.resolveStatus(book).name())
                        .needsRenormalization(normalizationVersionService.needsRenormalization(book))
                        .normalizedArtifactPath(book.getNormalizedArtifactPath())
                        .isDefault(book.isDefault())
                        .isFavorite(favoriteBookIds.contains(book.getId()))
                        .summary(book.isSummary())
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .toList();
    }

    public BookDetailDTO getBook(Long bookId, Long userId) {
        Book book = bookRepository.findByIdAndNormalizationStatus(bookId, NormalizationStatus.READY)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        boolean isFavorite = userId != null && favoriteRepository.findByUserId(userId).stream()
                .anyMatch(fav -> fav.getBook().getId().equals(bookId));

        return convertToDetailDTO(book, isFavorite);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookDetailDTO uploadBook(Long userId,
                                    MultipartFile epubFile,
                                    String title,
                                    String author,
                                    String language) {
        if (epubFile == null || epubFile.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        ExtractedEpubMetadata extractedMetadata = epubMetadataExtractorService.extract(epubFile);
        String resolvedTitle = resolveRequiredMetadata("title", extractedMetadata.title(), null);
        String resolvedAuthor = resolveRequiredMetadata("author", extractedMetadata.author(), null);
        String resolvedLanguage = resolveRequiredMetadata("language", extractedMetadata.language(), language);

        Book savedBook = bookUploadTransactionService.createUploadBook(
                userId,
                resolvedTitle,
                resolvedAuthor,
                resolvedLanguage
        );
        String sourceVersion = normalizedArtifactStorageService.newSourceVersion();
        String sourcePath;
        try {
            sourcePath = normalizedArtifactStorageService.storeSourceEpub(savedBook.getId(), sourceVersion, epubFile);
        } catch (RuntimeException e) {
            markUploadFailedQuietly(savedBook.getId());
            throw e;
        }
        String coverUrl = storeCoverImageIfPresent(savedBook.getId(), sourceVersion, extractedMetadata);

        BookUploadCompletion completion;
        try {
            completion = bookUploadTransactionService.completeUpload(
                    savedBook.getId(),
                    sourceVersion,
                    sourcePath,
                    coverUrl
            );
        } catch (RuntimeException e) {
            markUploadFailedQuietly(savedBook.getId());
            throw e;
        }
        normalizationJobDispatcher.dispatch(completion.jobId());

        return convertToDetailDTO(completion.book(), false);
    }

    private BookDetailDTO convertToDetailDTO(Book book, boolean isFavorite) {
        return BookDetailDTO.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .language(book.getLanguage())
                .isDefault(book.isDefault())
                .coverImgUrl(cdnUrlService.toPublicUrl(book.getCoverImgUrl()))
                .epubPath(book.getEpubPath())
                .normalizationStatus(enumName(book.getNormalizationStatus()))
                .analysisStatus(enumName(book.getAnalysisStatus()))
                .ruleVersion(book.getRuleVersion())
                .locatorVersion(book.getLocatorVersion())
                .normalizationRunId(book.getNormalizationRunId())
                .normalizationVersionStatus(normalizationVersionService.resolveStatus(book).name())
                .needsRenormalization(normalizationVersionService.needsRenormalization(book))
                .normalizedArtifactPath(book.getNormalizedArtifactPath())
                .isFavorite(isFavorite)
                .summary(book.isSummary())
                .build();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String storeCoverImageIfPresent(Long bookId, String sourceVersion, ExtractedEpubMetadata extractedMetadata) {
        if (extractedMetadata.cover() == null || extractedMetadata.cover().isEmpty()) {
            return null;
        }
        try {
            return normalizedArtifactStorageService.storeBookCover(bookId, sourceVersion, extractedMetadata.cover());
        } catch (RuntimeException e) {
            log.warn("Failed to store EPUB cover image. bookId={}, sourceVersion={}", bookId, sourceVersion, e);
            return null;
        }
    }

    private void markUploadFailedQuietly(Long bookId) {
        try {
            bookUploadTransactionService.markUploadFailed(bookId);
        } catch (RuntimeException nested) {
            log.warn("Failed to mark upload as failed. bookId={}", bookId, nested);
        }
    }

    private String resolveRequiredMetadata(String fieldName, String extractedValue, String fallbackValue) {
        String resolved = normalizeMetadataValue(extractedValue);
        if (resolved == null) {
            resolved = normalizeMetadataValue(fallbackValue);
        }
        if (resolved == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Missing book " + fieldName + ".");
        }
        return resolved;
    }

    private String normalizeMetadataValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
