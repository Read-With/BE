package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
// import com.kw.readwith.domain.Book; // Book을 직접 import할 필요는 없습니다.
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import com.kw.readwith.dto.admin.CharacterPovSummaryDTO;
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
}