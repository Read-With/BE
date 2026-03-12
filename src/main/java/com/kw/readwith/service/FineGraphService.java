package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.*;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FineGraphService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final CharacterRepository characterRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;
    private final ObjectMapper objectMapper;

    /**
     * 세밀(이벤트) 그래프 조회 - 특정 이벤트에서의 관계
     */
    public FineGraphResponseDTO getFineGraph(Long bookId, Integer chapterIdx, Integer eventIdx, Long userId) {
        // TODO: 사용자 인증 구현 후 접근 권한 검증 로직 추가
        
        // 책 존재 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 챕터 존재 확인
        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));

        // 이벤트 존재 확인
        Event event = eventRepository.findByChapterAndIdx(chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));

        // 해당 이벤트의 모든 관계 엣지 조회
        List<EventRelationshipEdge> edges = eventRelationshipEdgeRepository.findByEvent(event);

        // 관련된 인물들 추출
        Set<Long> characterIds = edges.stream()
                .flatMap(edge -> List.of(edge.getFromCharacter().getId(), edge.getToCharacter().getId()).stream())
                .collect(Collectors.toSet());

        List<Character> characters = characterRepository.findAllById(characterIds);

        // DTO 변환
        List<GraphNodeDTO> nodes = characters.stream()
                .map(character -> convertToGraphNodeDTO(character, event))
                .collect(Collectors.toList());

        List<FineGraphEdgeDTO> edgeDTOs = edges.stream()
                .map(this::convertToFineGraphEdgeDTO)
                .collect(Collectors.toList());

        EventInfoDTO eventInfo = EventInfoDTO.builder()
                .chapterIdx(chapterIdx)
                .eventIdx(eventIdx)
                .eventId(event.getEventId())
                .startLocator(buildEventLocator(event, true))
                .endLocator(buildEventLocator(event, false))
                .startTxtOffset(event.getStartTxtOffset())
                .endTxtOffset(event.getEndTxtOffset())
                .build();

        return FineGraphResponseDTO.builder()
                .nodes(nodes)
                .edges(edgeDTOs)
                .eventInfo(eventInfo)
                .build();
    }

    /**
     * Character를 GraphNodeDTO로 변환 (노드 중요도 포함)
     */
    private GraphNodeDTO convertToGraphNodeDTO(Character character, Event event) {
        // 해당 이벤트에서의 캐릭터 중요도 조회
        EventCharacterStat characterStat = eventCharacterStatRepository
                .findByEventAndCharacter(event, character)
                .orElse(null);
        
        Float weight = characterStat != null ? (float) characterStat.getNodeWeight() : null;
        // EventCharacterStat에는 count 필드가 없으므로 null로 설정
        Integer count = null;
        
        return GraphNodeDTO.builder()
                .id(character.getId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getPersonalityText(), 200))  // 성격 설명
                .portraitPrompt(character.getProfileText())  // 외모 묘사 (이미지 생성용)
                .names(parseNames(character.getNames(), character.getName()))
                .weight(weight)
                .count(count)
                .build();
    }

    /**
     * EventRelationshipEdge를 FineGraphEdgeDTO로 변환
     */
    private FineGraphEdgeDTO convertToFineGraphEdgeDTO(EventRelationshipEdge edge) {
        List<String> relationTags = new ArrayList<>();
        try {
            if (edge.getRelationTags() != null && !edge.getRelationTags().trim().isEmpty()) {
                relationTags = objectMapper.readValue(edge.getRelationTags(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            log.warn("Failed to parse relationTags: {}", edge.getRelationTags(), e);
            relationTags = new ArrayList<>();
        }

        return FineGraphEdgeDTO.builder()
                .from(edge.getFromCharacter().getId())
                .to(edge.getToCharacter().getId())
                .sentimentScore(edge.getSentimentScore() != null ? edge.getSentimentScore().doubleValue() : 0.0)
                .interactionCount(edge.getInteractionCount())
                .relationTags(relationTags)
                .build();
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
     * names 문자열을 List<String>으로 파싱 (안전한 에러 처리)
     */
    private List<String> parseNames(String names, String fallbackName) {
        if (names == null || names.trim().isEmpty()) {
            return List.of(fallbackName);
        }
        
        try {
            List<String> nameList = Arrays.stream(names.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.toList());
            
            return nameList.isEmpty() ? List.of(fallbackName) : nameList;
        } catch (Exception e) {
            log.warn("Failed to parse names: {}, using fallback: {}", names, fallbackName, e);
            return List.of(fallbackName);
        }
    }

    private LocatorDTO buildEventLocator(Event event, boolean isStart) {
        Integer blockIndex = isStart ? event.getStartBlockIndex() : event.getEndBlockIndex();
        Integer offset = isStart ? event.getStartOffset() : event.getEndOffset();
        if (blockIndex == null || offset == null || event.getChapter() == null) {
            return null;
        }

        return LocatorDTO.builder()
                .chapterIndex(event.getChapter().getIdx())
                .blockIndex(blockIndex)
                .offset(offset)
                .build();
    }
}
