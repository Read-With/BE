package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Bookmark;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.bookmark.BookmarkResponseDTO;
import com.kw.readwith.dto.bookmark.CreateBookmarkRequestDTO;
import com.kw.readwith.dto.bookmark.UpdateBookmarkRequestDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.BookmarkRepository;
import com.kw.readwith.repository.UserRepository;
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
                .map(BookmarkResponseDTO::from)
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

        // 겹치는 북마크 확인
        List<Bookmark> overlappingBookmarks = bookmarkRepository.findOverlappingBookmarks(
                userId, requestDTO.getBookId(), requestDTO.getStartCfi(), requestDTO.getEndCfi());
        
        if (!overlappingBookmarks.isEmpty()) {
            throw new GeneralException(ErrorStatus.BOOKMARK_ALREADY_EXISTS);
        }

        // 북마크 생성
        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .book(book)
                .startCfi(requestDTO.getStartCfi())
                .endCfi(requestDTO.getEndCfi())
                .color(requestDTO.getColor())
                .memo(requestDTO.getMemo())
                .build();

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        return BookmarkResponseDTO.from(savedBookmark);
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

        return BookmarkResponseDTO.from(bookmark);
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
}
