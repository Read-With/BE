package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Bookmark;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.bookmark.BookmarkResponseDTO;
import com.kw.readwith.dto.bookmark.CreateBookmarkRequestDTO;
import com.kw.readwith.dto.bookmark.UpdateBookmarkRequestDTO;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.BookmarkRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final LocatorSupport locatorSupport;
    private final V2TransitionGuard transitionGuard;
    private final BookAccessPolicy bookAccessPolicy;

    public List<BookmarkResponseDTO> getBookmarks(Long userId, Long bookId, String sort) {
        validateUserExists(userId);
        validateReadableBook(userId, bookId);

        List<Bookmark> bookmarks = switch (normalizeSort(sort)) {
            case "time_asc" -> bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtAsc(userId, bookId);
            case "time_desc" -> bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtDesc(userId, bookId);
            default -> throw new GeneralException(ErrorStatus._BAD_REQUEST, "sort must be time_desc or time_asc.");
        };

        return bookmarks.stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    @Transactional
    public BookmarkResponseDTO createBookmark(Long userId, CreateBookmarkRequestDTO requestDTO) {
        User user = validateUserExists(userId);
        Book book = validateReadableBook(userId, requestDTO.getBookId());

        transitionGuard.ensureLocatorWritesEnabled("bookmark 생성");
        ResolvedBookmarkRange resolvedRange = resolveRange(book, requestDTO.getStartLocator(), requestDTO.getEndLocator());

        List<Bookmark> existingBookmarks = bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtDesc(userId, requestDTO.getBookId());
        if (hasDuplicateOrOverlap(existingBookmarks, resolvedRange)) {
            throw new GeneralException(ErrorStatus.BOOKMARK_ALREADY_EXISTS);
        }

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .book(book)
                .startLocatorJson(locatorSupport.writeLocator(requestDTO.getStartLocator()))
                .endLocatorJson(locatorSupport.writeLocator(requestDTO.getEndLocator()))
                .startTxtOffset(resolvedRange.startTxtOffset())
                .endTxtOffset(resolvedRange.endTxtOffset())
                .locatorVersion(book.getLocatorVersion())
                .color(requestDTO.getColor())
                .memo(requestDTO.getMemo())
                .build();

        return convertToResponseDTO(bookmarkRepository.save(bookmark));
    }

    @Transactional
    public BookmarkResponseDTO updateBookmark(Long userId, Long bookmarkId, UpdateBookmarkRequestDTO requestDTO) {
        Bookmark bookmark = validateBookmarkOwnership(userId, bookmarkId);
        bookmark.updateBookmark(requestDTO.getColor(), requestDTO.getMemo());
        return convertToResponseDTO(bookmark);
    }

    @Transactional
    public void deleteBookmark(Long userId, Long bookmarkId) {
        Bookmark bookmark = validateBookmarkOwnership(userId, bookmarkId);
        bookmarkRepository.delete(bookmark);
    }

    private User validateUserExists(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
    }

    private Book validateReadableBook(Long userId, Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);
        return book;
    }

    private Bookmark validateBookmarkOwnership(Long userId, Long bookmarkId) {
        return bookmarkRepository.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOKMARK_NOT_FOUND));
    }

    private BookmarkResponseDTO convertToResponseDTO(Bookmark bookmark) {
        return BookmarkResponseDTO.builder()
                .id(bookmark.getId())
                .bookId(bookmark.getBook().getId())
                .startLocator(locatorSupport.readLocator(bookmark.getStartLocatorJson()))
                .endLocator(locatorSupport.readLocator(bookmark.getEndLocatorJson()))
                .startTxtOffset(bookmark.getStartTxtOffset())
                .endTxtOffset(bookmark.getEndTxtOffset())
                .locatorVersion(bookmark.getLocatorVersion())
                .color(bookmark.getColor())
                .memo(bookmark.getMemo())
                .isRangeBookmark(bookmark.isRangeBookmark())
                .createdAt(bookmark.getCreatedAt())
                .updatedAt(bookmark.getUpdatedAt())
                .build();
    }

    private ResolvedBookmarkRange resolveRange(Book book, LocatorDTO startLocator, LocatorDTO endLocator) {
        if (startLocator == null || startLocator.getChapterIndex() == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "startLocator.chapterIndex는 필수입니다.");
        }

        Chapter startChapter = chapterRepository.findByBookIdAndIdx(book.getId(), startLocator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        transitionGuard.ensureLocatorMetadataReady(book, startChapter, "bookmark 생성");
        int startTxtOffset = locatorSupport.toTxtOffset(startChapter, startLocator);

        if (endLocator == null) {
            return new ResolvedBookmarkRange(startTxtOffset, null);
        }
        if (endLocator.getChapterIndex() == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "endLocator.chapterIndex는 필수입니다.");
        }
        if (!startLocator.getChapterIndex().equals(endLocator.getChapterIndex())) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "범위 북마크는 동일 챕터 내에서만 생성할 수 있습니다.");
        }

        Chapter endChapter = chapterRepository.findByBookIdAndIdx(book.getId(), endLocator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        transitionGuard.ensureLocatorMetadataReady(book, endChapter, "bookmark 생성");
        int endTxtOffset = locatorSupport.toTxtOffset(endChapter, endLocator);
        if (startTxtOffset >= endTxtOffset) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "startLocator는 endLocator보다 앞에 있어야 합니다.");
        }

        return new ResolvedBookmarkRange(startTxtOffset, endTxtOffset);
    }

    private boolean hasDuplicateOrOverlap(List<Bookmark> existingBookmarks, ResolvedBookmarkRange newRange) {
        for (Bookmark existing : existingBookmarks) {
            boolean exactSamePoint = existing.getStartTxtOffset().equals(newRange.startTxtOffset())
                    && existing.getEndTxtOffset() == null
                    && newRange.endTxtOffset() == null;
            boolean exactSameRange = existing.getStartTxtOffset().equals(newRange.startTxtOffset())
                    && equalsNullable(existing.getEndTxtOffset(), newRange.endTxtOffset());

            if (exactSamePoint || exactSameRange) {
                return true;
            }

            if (existing.getEndTxtOffset() != null && newRange.endTxtOffset() != null) {
                boolean overlaps = existing.getStartTxtOffset() < newRange.endTxtOffset()
                        && existing.getEndTxtOffset() > newRange.startTxtOffset();
                if (overlaps) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean equalsNullable(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "time_desc";
        }
        return sort.trim().toLowerCase();
    }

    private record ResolvedBookmarkRange(Integer startTxtOffset, Integer endTxtOffset) {
    }
}
