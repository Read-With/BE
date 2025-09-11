package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.dto.manifest.*;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManifestService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final EventRepository eventRepository;

    /**
     * 책 구조/캐시 패키지 조회
     * 책 메타데이터 + 챕터/이벤트 + 인물 정보를 한 번에 제공
     */
    public ManifestResponseDTO getBookManifest(Long bookId, Long userId) {
        // 1. 책 접근 권한 확인 및 조회
        Book book = validateAndGetBook(bookId, userId);
        
        // 2. 챕터 목록 조회
        List<Chapter> chapters = chapterRepository.findByBookId(bookId);
        
        // 3. 인물 목록 조회 (주요 인물 우선, 이름순)
        List<Character> characters = characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book);
        
        // 4. 모든 이벤트 조회 후 챕터별로 그룹화
        List<Event> events = eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book);
        Map<Long, List<Event>> eventsByChapter = events.stream()
                .collect(Collectors.groupingBy(event -> event.getChapter().getId()));
        
        // 5. DTO 변환
        return ManifestResponseDTO.builder()
                .book(convertToBookManifestDTO(book))
                .chapters(convertToChapterManifestDTOs(chapters, eventsByChapter))
                .characters(convertToCharacterManifestDTOs(characters))
                .build();
    }

    /**
     * 책 접근 권한 확인 및 조회
     */
    private Book validateAndGetBook(Long bookId, Long userId) {
        Book book;
        if (userId == null) {
            // 비로그인 사용자: 기본 제공 + 요약 완료만
            book = bookRepository.findByIdAndSummaryTrueAndIsDefaultTrue(bookId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        } else {
            // 로그인 사용자: 기본 제공 + 본인 업로드 + 요약 완료만
            book = bookRepository.findAccessibleBook(bookId, userId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        }
        return book;
    }

    /**
     * Book -> BookManifestDTO 변환
     */
    private BookManifestDTO convertToBookManifestDTO(Book book) {
        return BookManifestDTO.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .language(book.getLanguage())
                .isDefault(book.isDefault())
                .summary(book.isSummary())
                .coverImgUrl(book.getCoverImgUrl())
                .summaryUrl(book.getSummaryUrl())
                .epubPath(book.getEpubPath())
                .build();
    }

    /**
     * Chapter 목록 -> ChapterManifestDTO 목록 변환
     */
    private List<ChapterManifestDTO> convertToChapterManifestDTOs(
            List<Chapter> chapters, 
            Map<Long, List<Event>> eventsByChapter) {
        
        return chapters.stream()
                .map(chapter -> {
                    List<Event> chapterEvents = eventsByChapter.getOrDefault(chapter.getId(), List.of());
                    return ChapterManifestDTO.builder()
                            .idx(chapter.getIdx())
                            .title(chapter.getTitle())
                            .startPos(chapter.getStartPos())
                            .endPos(chapter.getEndPos())
                            .rawText(truncateText(chapter.getRawText(), 200)) // 원본 텍스트 일부만
                            .summaryText(chapter.getSummaryText())
                            .summaryUploadUrl(chapter.getSummaryUploadUrl())
                            .povSummariesCached(chapter.isPovSummariesCached())
                            .events(convertToEventManifestDTOs(chapterEvents))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Event 목록 -> EventManifestDTO 목록 변환
     */
    private List<EventManifestDTO> convertToEventManifestDTOs(List<Event> events) {
        return events.stream()
                .map(event -> EventManifestDTO.builder()
                        .idx(event.getIdx())
                        .startPos(event.getStartPos())
                        .endPos(event.getEndPos())
                        .rawText(truncateText(event.getRawText(), 300)) // 원본 텍스트 일부
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Character 목록 -> CharacterManifestDTO 목록 변환
     */
    private List<CharacterManifestDTO> convertToCharacterManifestDTOs(List<Character> characters) {
        return characters.stream()
                .map(character -> CharacterManifestDTO.builder()
                        .id(character.getCharacterId()) // 책 내 고유 ID 사용
                        .name(character.getName())
                        .names(parseNames(character.getNames(), character.getName()))
                        .profileImage(character.getProfileImage())
                        .isMainCharacter(character.isMainCharacter())
                        .firstChapterIdx(character.getFirstChapterIdx())
                        .personalityText(character.getPersonalityText())
                        .description(character.getProfileText())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 텍스트를 지정된 길이로 자르기
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // 개행 문자 제거 및 공백 정리
        String cleanText = text.replaceAll("\\s+", " ").trim();
        
        if (cleanText.length() <= maxLength) {
            return cleanText;
        }
        
        return cleanText.substring(0, maxLength - 3) + "...";
    }

    /**
     * names 문자열을 List<String>으로 변환 (에러 처리 포함)
     */
    private List<String> parseNames(String namesString, String fallbackName) {
        try {
            if (namesString != null && !namesString.trim().isEmpty()) {
                List<String> parsedNames = Arrays.asList(namesString.split(","))
                        .stream()
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toList());
                
                if (!parsedNames.isEmpty()) {
                    return parsedNames;
                }
            }
        } catch (Exception e) {
            // 로그 기록하고 fallback 처리
            log.warn("Failed to parse names: {}, using fallback: {}", namesString, fallbackName, e);
        }
        
        // split 실패 시 기존 이름(fallbackName) 하나로 fallback
        return Collections.singletonList(fallbackName);
    }
}
