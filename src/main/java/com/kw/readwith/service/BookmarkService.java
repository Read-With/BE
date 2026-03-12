package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Bookmark;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.bookmark.BookmarkResponseDTO;
import com.kw.readwith.dto.bookmark.CreateBookmarkRequestDTO;
import com.kw.readwith.dto.bookmark.UpdateBookmarkRequestDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.BookmarkRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final LocatorSupport locatorSupport;

    /**
     * 북마크 목록 조회
     */
    public List<BookmarkResponseDTO> getBookmarks(Long userId, Long bookId, String sort) {
        // 사용자와 책 존재 여부 확인
        validateUserExists(userId);
        validateBookAccess(userId, bookId);

        List<Bookmark> bookmarks;
        if ("time_asc".equalsIgnoreCase(sort)) {
            bookmarks = bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtAsc(userId, bookId);
        } else {
            // 기본값: time_desc (최신순)
            bookmarks = bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtDesc(userId, bookId);
        }

        return bookmarks.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * 북마크 생성
     */
    @Transactional
    public BookmarkResponseDTO createBookmark(Long userId, CreateBookmarkRequestDTO requestDTO) {
        // 사용자와 책 존재 여부 확인
        User user = validateUserExists(userId);
        Book book = validateBookAccess(userId, requestDTO.getBookId());
        ResolvedBookmarkRange resolvedRange = resolveRange(book, requestDTO.getStartLocator(), requestDTO.getEndLocator());

        List<Bookmark> existingBookmarks = bookmarkRepository.findByUserIdAndBookIdOrderByCreatedAtDesc(userId, requestDTO.getBookId());
        if (hasDuplicateOrOverlap(existingBookmarks, resolvedRange)) {
            throw new GeneralException(ErrorStatus.BOOKMARK_ALREADY_EXISTS);
        }

        // 북마크 생성
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

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        return convertToResponseDTO(savedBookmark);
    }

    /**
     * 북마크 수정
     */
    @Transactional
    public BookmarkResponseDTO updateBookmark(Long userId, Long bookmarkId, UpdateBookmarkRequestDTO requestDTO) {
        // 북마크 존재 및 권한 확인
        Bookmark bookmark = validateBookmarkOwnership(userId, bookmarkId);

        // 북마크 업데이트
        bookmark.updateBookmark(requestDTO.getColor(), requestDTO.getMemo());

        return convertToResponseDTO(bookmark);
    }

    /**
     * 북마크 삭제
     */
    @Transactional
    public void deleteBookmark(Long userId, Long bookmarkId) {
        // 북마크 존재 및 권한 확인
        Bookmark bookmark = validateBookmarkOwnership(userId, bookmarkId);

        bookmarkRepository.delete(bookmark);
    }

    /**
     * 사용자 존재 여부 확인
     */
    private User validateUserExists(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
    }

    /**
     * 책 존재 및 접근 권한 확인
     */
    private Book validateBookAccess(Long userId, Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 접근 권한 확인: 기본 제공 도서 또는 본인이 업로드한 도서
        boolean hasAccess = book.isDefault() || 
                           (book.getUploadedBy() != null && book.getUploadedBy().getId().equals(userId));
        
        if (!hasAccess) {
            throw new GeneralException(ErrorStatus.BOOK_ACCESS_DENIED);
        }

        return book;
    }

    /**
     * 북마크 소유권 확인
     */
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
        int startTxtOffset = locatorSupport.toTxtOffset(startChapter, startLocator);

        if (endLocator == null) {
            return new ResolvedBookmarkRange(startTxtOffset, null);
        }
        if (endLocator.getChapterIndex() == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "endLocator.chapterIndex는 필수입니다.");
        }

        if (!startLocator.getChapterIndex().equals(endLocator.getChapterIndex())) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "북마크 범위는 동일 챕터 내에서만 저장할 수 있습니다.");
        }

        Chapter endChapter = chapterRepository.findByBookIdAndIdx(book.getId(), endLocator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
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
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private record ResolvedBookmarkRange(Integer startTxtOffset, Integer endTxtOffset) {
    }
}
