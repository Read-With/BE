package com.kw.readwith.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import com.kw.readwith.dto.admin.UnsummarizedItemDTO;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.dto.book.BookSummaryDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterPovSummaryRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.FavoriteRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final FavoriteRepository favoriteRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final CharacterPovSummaryRepository characterPovSummaryRepository;
    private final ObjectMapper objectMapper;

    public List<BookSummaryDTO> getBooks(String keyword, String language, Boolean favoriteOnly, String sortBy, Long userId) {
        List<Book> books = bookRepository.findAll();
        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.toLowerCase();
            books = books.stream().filter(b -> b.getTitle().toLowerCase().contains(lower) || b.getAuthor().toLowerCase().contains(lower)).collect(Collectors.toList());
        }
        if (language != null && !language.isBlank()) {
            books = books.stream().filter(b -> language.equalsIgnoreCase(b.getLanguage())).collect(Collectors.toList());
        }
        final Set<Long> favoriteBookIds;
        if (userId != null) {
            favoriteBookIds = favoriteRepository.findByUserId(userId).stream().map(fav -> fav.getBook().getId()).collect(Collectors.toSet());
        } else {
            favoriteBookIds = Set.of();
        }
        if (favoriteOnly != null && favoriteOnly) {
            books = books.stream().filter(b -> favoriteBookIds.contains(b.getId())).collect(Collectors.toList());
        }
        books.sort((a, b) -> {
            if ("title".equalsIgnoreCase(sortBy)) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        return books.stream().map(book -> BookSummaryDTO.builder().id(book.getId()).title(book.getTitle()).author(book.getAuthor()).coverImgUrl(book.getCoverImgUrl()).isDefault(book.isDefault()).isFavorite(favoriteBookIds.contains(book.getId())).updatedAt(book.getUpdatedAt()).build()).collect(Collectors.toList());
    }

    public BookDetailDTO getBook(Long bookId, Long userId) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        boolean isFavorite = false;
        if (userId != null) {
            isFavorite = favoriteRepository.findByUserId(userId).stream().anyMatch(fav -> fav.getBook().getId().equals(bookId));
        }
        return convertToDetailDTO(book, isFavorite);
    }

    @Transactional
    public BookDetailDTO uploadBook(MultipartFile epubFile, String title, String author, String language) {
        String epubPath = epubFile != null ? epubFile.getOriginalFilename() : null;
        Book book = Book.builder().title(title).author(author).language(language).isDefault(false).coverImgUrl(null).epubPath(epubPath).uploadedBy(null).build();
        Book saved = bookRepository.save(book);
        return convertToDetailDTO(saved, false);
    }

    private BookDetailDTO convertToDetailDTO(Book book, boolean isFavorite) {
        return BookDetailDTO.builder().id(book.getId()).title(book.getTitle()).author(book.getAuthor()).language(book.getLanguage()).isDefault(book.isDefault()).coverImgUrl(book.getCoverImgUrl()).epubPath(book.getEpubPath()).isFavorite(isFavorite).build();
    }

    public List<UnsummarizedItemDTO> getUnsummarizedChapters() {
        return chapterRepository.findUnsummarizedChapters().stream().map(UnsummarizedItemDTO::from).collect(Collectors.toList());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class PovSummaryData {
        private String character_name;
        private String summary;
    }

    @Transactional
    public void uploadChapterSummary(Long bookId, Integer idx, MultipartFile summaryFile) {
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, idx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (chapter.isPovSummariesCached()) {
            throw new GeneralException(ErrorStatus.CHAPTER_ALREADY_SUMMARIZED);
        }

        Map<String, PovSummaryData> summaries;
        try {
            summaries = objectMapper.readValue(summaryFile.getInputStream(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR);
        }

        for (Map.Entry<String, PovSummaryData> entry : summaries.entrySet()) {
            Long characterIdInBook = Long.parseLong(entry.getKey());
            PovSummaryData summaryData = entry.getValue();

            Character character = characterRepository.findByBookAndCharacterId(chapter.getBook(), characterIdInBook)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));

            CharacterPovSummary povSummary = CharacterPovSummary.builder()
                    .book(chapter.getBook())
                    .chapter(chapter)
                    .character(character)
                    .summaryText(summaryData.getSummary())
                    .build();
            characterPovSummaryRepository.save(povSummary);
        }

        chapter.markAsSummarized();
        checkAndUpdateBookSummaryStatus(chapter.getBook());
    }

    private void checkAndUpdateBookSummaryStatus(Book book) {
        if (book.isSummary()) {
            return;
        }
        List<Chapter> chapters = chapterRepository.findByBookId(book.getId());
        boolean allChaptersSummarized = chapters.stream().allMatch(Chapter::isPovSummariesCached);
        if (allChaptersSummarized) {
            book.completeSummary();
        }
    }

    @Transactional(readOnly = true)
    public List<BookSummaryDTO> getUnsummarizedBooks() {
        List<Book> books = bookRepository.findBySummaryIsFalse();
        return books.stream().map(book -> BookSummaryDTO.builder().id(book.getId()).title(book.getTitle()).author(book.getAuthor()).coverImgUrl(book.getCoverImgUrl()).isDefault(book.isDefault()).isFavorite(false).updatedAt(book.getUpdatedAt()).build()).collect(Collectors.toList());
    }
}