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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterPovSummaryService {

    private final CharacterPovSummaryRepository characterPovSummaryRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final BookRepository bookRepository;

    @Transactional
    public Long createCharacterPovSummary(Long chapterId, Long characterId, String summaryText) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));

        // .book(chapter.getBook()) 코드 추가.
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
        List<CharacterPovSummary> summaries = characterPovSummaryRepository.findByChapterId(chapterId);
        return summaries.stream()
                .map(summary -> new CharacterPovSummaryDTO(
                        summary.getId(),
                        summary.getChapter().getId(),
                        summary.getCharacter().getId(),
                        summary.getSummaryText()))
                .collect(Collectors.toList());
    }

    /**
     * 특정 책의 특정 챕터에 대한 인물별 시점요약을 조회합니다.
     * @param bookId 책 ID
     * @param chapterIdx 챕터 인덱스 (1-based)
     * @param userId 사용자 ID
     * @return 챕터 시점요약 응답 DTO
     */
    public ChapterPovSummaryResponseDTO getChapterPovSummaries(Long bookId, Integer chapterIdx, Long userId) {
        // 책 접근 권한 확인 및 조회
        Book book = validateBookAccess(bookId, userId);

        // 챕터 존재 여부 확인
        Chapter chapter = chapterRepository.findByBookIdAndIdx(bookId, chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        // 시점요약 조회
        List<CharacterPovSummary> povSummaries = characterPovSummaryRepository.findByBookIdAndChapterIdx(bookId, chapterIdx);

        // DTO 변환
        List<ChapterPovSummaryDTO> povSummaryDTOs = povSummaries.stream()
                .map(summary -> new ChapterPovSummaryDTO(
                        summary.getCharacter().getId(),
                        summary.getCharacter().getName(),
                        summary.getSummaryText(),
                        summary.getCharacter().isMainCharacter()
                ))
                .collect(Collectors.toList());

        return new ChapterPovSummaryResponseDTO(
                bookId,
                chapterIdx,
                chapter.getTitle(),
                povSummaryDTOs
        );
    }

    /**
     * 책 접근 권한 확인 및 조회
     */
    private Book validateBookAccess(Long bookId, Long userId) {
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
}