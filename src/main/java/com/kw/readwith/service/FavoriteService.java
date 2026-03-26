package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.mapping.Favorite;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookAccessPolicy bookAccessPolicy;
    private final NormalizationVersionService normalizationVersionService;

    @Transactional
    public void addFavorite(Long userId, Long bookId) {
        Favorite existing = favoriteRepository.findByUserIdAndBookId(userId, bookId);
        if (existing != null) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        favoriteRepository.save(Favorite.builder()
                .user(user)
                .book(book)
                .build());
    }

    @Transactional
    public void removeFavorite(Long userId, Long bookId) {
        Favorite favorite = favoriteRepository.findByUserIdAndBookId(userId, bookId);
        if (favorite != null) {
            favoriteRepository.delete(favorite);
        }
    }

    public List<BookSummaryDTO> getFavoriteBooks(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(Favorite::getBook)
                .filter(book -> bookAccessPolicy.isReadable(book, userId))
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
                        .isFavorite(true)
                        .summary(book.isSummary())
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .toList();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
