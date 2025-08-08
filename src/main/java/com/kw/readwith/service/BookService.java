package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.domain.User;
import com.kw.readwith.aws.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AmazonS3Manager amazonS3Manager;

    /**
     * 도서 목록 조회 (검색/필터/정렬/즐겨찾기)
     */
    public List<BookSummaryDTO> getBooks(String keyword,
                                         String language,
                                         Boolean favoriteOnly,
                                         String sortBy,
                                         Long userId) {
        List<Book> books = bookRepository.findAll().stream()
                .filter(b -> b.isInfoUploaded() || b.isDefault())
                .collect(Collectors.toList());

        // 검색
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            books = books.stream()
                    .filter(b -> b.getTitle().toLowerCase().contains(lower) || b.getAuthor().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        // 언어 필터
        if (language != null && !language.isBlank()) {
            books = books.stream()
                    .filter(b -> language.equalsIgnoreCase(b.getLanguage()))
                    .collect(Collectors.toList());
        }

        // 즐겨찾기 필터
        final Set<Long> favoriteBookIds;
        if (userId != null) {
            favoriteBookIds = favoriteRepository.findByUserId(userId).stream()
                    .map(fav -> fav.getBook().getId())
                    .collect(Collectors.toSet());
        } else {
            favoriteBookIds = Set.of();
        }

        if (favoriteOnly != null && favoriteOnly) {
            books = books.stream()
                    .filter(b -> favoriteBookIds.contains(b.getId()))
                    .collect(Collectors.toList());
        }

        // 정렬 (updatedAt, title)
        books.sort((a, b) -> {
            if ("title".equalsIgnoreCase(sortBy)) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
            // default updatedAt desc
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });

        return books.stream()
                .map(book -> BookSummaryDTO.builder()
                        .id(book.getId())
                        .title(book.getTitle())
                        .author(book.getAuthor())
                        .coverImgUrl(book.getCoverImgUrl())
                        .isDefault(book.isDefault())
                        .isFavorite(favoriteBookIds.contains(book.getId()))
                        .updatedAt(book.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 단일 도서 조회
     */
    public BookDetailDTO getBook(Long bookId, Long userId) {
        Book book = bookRepository.findById(bookId)
                .filter(b -> b.isInfoUploaded() || b.isDefault())
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        boolean isFavorite = false;
        if (userId != null) {
            isFavorite = favoriteRepository.findByUserId(userId).stream()
                    .anyMatch(fav -> fav.getBook().getId().equals(bookId));
        }
        return convertToDetailDTO(book, isFavorite);
    }

    /**
     * 도서 업로드 (EPUB 파일 S3 업로드 후 Book 레코드 저장)
     */
    @Transactional
    public BookDetailDTO uploadBook(Long userId,
                                    MultipartFile epubFile,
                                    String title,
                                    String author,
                                    String language) {
        if(epubFile == null || epubFile.isEmpty()){
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }
        // 업로더 유저 조회
        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        // S3 original 폴더에 EPUB 업로드
        String epubUrl = amazonS3Manager.uploadOriginal(title, epubFile);

        Book book = Book.builder()
                .title(title)
                .author(author)
                .language(language)
                .isDefault(false)
                .coverImgUrl(null)
                .epubPath(epubUrl)
                .infoUploaded(false)
                .uploadedBy(uploader)
                .build();

        Book saved = bookRepository.save(book);
        return convertToDetailDTO(saved, false);
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
                .isFavorite(isFavorite)
                .build();
    }
} 