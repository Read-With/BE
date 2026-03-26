package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import com.kw.readwith.dto.admin.CharacterPovSummaryDTO;
import com.kw.readwith.dto.book.ChapterPovSummaryDTO;
import com.kw.readwith.dto.book.ChapterPovSummaryResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterPovSummaryRepository;
import com.kw.readwith.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterPovSummaryService {

    private final CharacterPovSummaryRepository characterPovSummaryRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final BookRepository bookRepository;
    private final BookAccessPolicy bookAccessPolicy;

    @Transactional
    public Long createCharacterPovSummary(Long chapterId, Long characterId, String summaryText) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));

        CharacterPovSummary newSummary = CharacterPovSummary.builder()
                .book(chapter.getBook())
                .chapter(chapter)
                .character(character)
                .summaryText(summaryText)
                .build();

        characterPovSummaryRepository.save(newSummary);
        return newSummary.getId();
    }

    public List<CharacterPovSummaryDTO> getCharacterPovSummariesByChapter(Long chapterId) {
        if (!chapterRepository.existsById(chapterId)) {
            throw new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND);
        }
        return characterPovSummaryRepository.findByChapterId(chapterId).stream()
                .map(summary -> new CharacterPovSummaryDTO(
                        summary.getId(),
                        summary.getChapter().getId(),
                        summary.getCharacter().getId(),
                        summary.getSummaryText()))
                .toList();
    }

    public ChapterPovSummaryResponseDTO getChapterPovSummaries(Long bookId, Integer chapterIdx, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        if (!book.isAnalysisReady()) {
            return new ChapterPovSummaryResponseDTO(bookId, chapterIdx, chapter.getTitle(), List.of());
        }

        List<ChapterPovSummaryDTO> povSummaryDTOs = characterPovSummaryRepository.findByBookIdAndChapterIdx(bookId, chapterIdx).stream()
                .map(summary -> new ChapterPovSummaryDTO(
                        summary.getCharacter().getId(),
                        summary.getCharacter().getName(),
                        summary.getSummaryText(),
                        summary.getCharacter().isMainCharacter()
                ))
                .toList();

        return new ChapterPovSummaryResponseDTO(bookId, chapterIdx, chapter.getTitle(), povSummaryDTOs);
    }
}
