package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.domain.mapping.EventCharacterWeight;
import com.kw.readwith.dto.graph.*;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FineGraphService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final CharacterRepository characterRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterWeightRepository eventCharacterWeightRepository;

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
                .start(event.getStartPos())
                .end(event.getEndPos())
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
        EventCharacterWeight characterWeight = eventCharacterWeightRepository
                .findByEventAndCharacter(event, character)
                .orElse(null);
        
        Float weight = characterWeight != null ? characterWeight.getWeight() : null;
        Integer count = characterWeight != null ? characterWeight.getCount() : null;
        
        return GraphNodeDTO.builder()
                .id(character.getId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getProfileText(), 200))
                .portraitPrompt(character.getPersonalityText())
                .names(parseNames(character.getNames(), character.getName()))
                .weight(weight)
                .count(count)
                .build();
    }

    /**
     * EventRelationshipEdge를 FineGraphEdgeDTO로 변환
     */
    private FineGraphEdgeDTO convertToFineGraphEdgeDTO(EventRelationshipEdge edge) {
        List<String> relationTags = edge.getRelationTags() != null ? 
                Arrays.asList(edge.getRelationTags().split(",")) : 
                new ArrayList<>();

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
