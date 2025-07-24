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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    // 즐겨찾기 추가
    @Transactional
    public void addFavorite(Long userId, Long bookId) {
        Favorite existing = favoriteRepository.findByUserIdAndBookId(userId, bookId);
        if (existing != null) return; // 이미 존재

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        Favorite favorite = Favorite.builder()
                .user(user)
                .book(book)
                .build();
        favoriteRepository.save(favorite);
    }

    // 즐겨찾기 삭제
    @Transactional
    public void removeFavorite(Long userId, Long bookId) {
        Favorite favorite = favoriteRepository.findByUserIdAndBookId(userId, bookId);
        if (favorite != null) {
            favoriteRepository.delete(favorite);
        }
    }

    // 즐겨찾기 목록 조회
    public List<BookSummaryDTO> getFavoriteBooks(Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserId(userId);
        return favorites.stream()
                .map(Favorite::getBook)
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(book.getCoverImgUrl())
                        .isDefault(book.isDefault())
                        .isFavorite(true)
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
} 