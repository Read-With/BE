package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.service.normalization.EpubMetadataExtractorService;
import com.kw.readwith.service.normalization.ExtractedEpubMetadata;
import com.kw.readwith.service.normalization.NormalizationJobDispatcher;
import com.kw.readwith.service.normalization.NormalizationJobService;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final EpubMetadataExtractorService epubMetadataExtractorService;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;
    private final NormalizationJobService normalizationJobService;
    private final NormalizationJobDispatcher normalizationJobDispatcher;
    private final NormalizationVersionService normalizationVersionService;

    public List<BookSummaryDTO> getBooks(String keyword,
                                         String language,
                                         Boolean favoriteOnly,
                                         String sortBy,
                                         Long userId) {
        List<Book> books = userId == null
                ? bookRepository.findByNormalizationStatusAndIsDefaultTrue(NormalizationStatus.READY)
                : bookRepository.findAccessibleBooks(userId, NormalizationStatus.READY);

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
                        .coverImgUrl(book.getCoverImgUrl())
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
        Book book = userId == null
                ? bookRepository.findByIdAndNormalizationStatusAndIsDefaultTrue(bookId, NormalizationStatus.READY)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND))
                : bookRepository.findAccessibleBook(bookId, userId, NormalizationStatus.READY)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        boolean isFavorite = userId != null && favoriteRepository.findByUserId(userId).stream()
                .anyMatch(fav -> fav.getBook().getId().equals(bookId));

        return convertToDetailDTO(book, isFavorite);
    }

    @Transactional
    public BookDetailDTO uploadBook(Long userId,
                                    MultipartFile epubFile,
                                    String title,
                                    String author,
                                    String language) {
        if (epubFile == null || epubFile.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        ExtractedEpubMetadata extractedMetadata = epubMetadataExtractorService.extract(epubFile);
        String resolvedTitle = resolveRequiredMetadata("title", extractedMetadata.title(), title);
        String resolvedAuthor = resolveRequiredMetadata("author", extractedMetadata.author(), author);
        String resolvedLanguage = resolveRequiredMetadata("language", extractedMetadata.language(), language);

        Book book = Book.builder()
                .title(resolvedTitle)
                .author(resolvedAuthor)
                .language(resolvedLanguage)
                .isDefault(false)
                .coverImgUrl(null)
                .uploadedBy(uploader)
                .summary(false)
                .build();

        Book savedBook = bookRepository.save(book);
        String sourceVersion = normalizedArtifactStorageService.newSourceVersion();
        String sourcePath = normalizedArtifactStorageService.storeSourceEpub(savedBook.getId(), sourceVersion, epubFile);
        savedBook.assignUploadedSource(sourcePath);
        savedBook.markNormalizationQueued();
        savedBook.resetAnalysisStatus();

        ProcessingJob job = normalizationJobService.createQueuedJob(savedBook, sourceVersion, "UPLOAD");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                normalizationJobDispatcher.dispatch(job.getId());
            }
        });

        return convertToDetailDTO(savedBook, false);
    }

    private BookDetailDTO convertToDetailDTO(Book book, boolean isFavorite) {
        return BookDetailDTO.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .language(book.getLanguage())
                .isDefault(book.isDefault())
                .coverImgUrl(book.getCoverImgUrl())
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
