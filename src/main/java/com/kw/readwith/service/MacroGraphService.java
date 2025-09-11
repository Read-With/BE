package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.domain.mapping.EventCharacterWeight;
import com.kw.readwith.dto.graph.*;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MacroGraphService {

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final UserReadStateRepository userReadStateRepository;
    private final EventCharacterWeightRepository eventCharacterWeightRepository;
    private final ObjectMapper objectMapper;

    /**
     * 거시(챕터 누적) 그래프 조회 - 각 챕터별 마지막 이벤트의 관계 데이터 사용
     */
    public MacroGraphResponseDTO getMacroGraph(Long bookId, Integer uptoChapter, Long userId) {
        // TODO: 사용자 인증 구현 후 접근 권한 검증 로직 추가
        
        // 책 존재 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 각 챕터별 마지막 이벤트들 조회
        List<Event> lastEvents = eventRepository.findLastEventsByChaptersUpTo(book, uptoChapter);

        // 각 마지막 이벤트의 관계 데이터 수집
        List<EventRelationshipEdge> macroEdges = new ArrayList<>();
        for (Event lastEvent : lastEvents) {
            List<EventRelationshipEdge> eventEdges = eventRelationshipEdgeRepository.findByEvent(lastEvent);
            macroEdges.addAll(eventEdges);
        }

        // 관련된 인물들 추출
        Set<Long> characterIds = macroEdges.stream()
                .flatMap(edge -> List.of(edge.getFromCharacter().getId(), edge.getToCharacter().getId()).stream())
                .collect(Collectors.toSet());

        List<Character> characters = characterRepository.findAllById(characterIds);

        // DTO 변환 - 각 캐릭터별로 해당하는 이벤트에서 중요도 조회
        List<GraphNodeDTO> nodes = characters.stream()
                .map(character -> {
                    // 해당 캐릭터가 포함된 관계의 이벤트 찾기 (아무거나 하나 - 어차피 누적이므로 동일)
                    Event relatedEvent = macroEdges.stream()
                            .filter(edge -> edge.getFromCharacter().getId().equals(character.getId()) || 
                                           edge.getToCharacter().getId().equals(character.getId()))
                            .map(EventRelationshipEdge::getEvent)
                            .findFirst()
                            .orElse(null);
                    
                    return convertToGraphNodeDTO(character, relatedEvent);
                })
                .collect(Collectors.toList());

        List<MacroGraphEdgeDTO> edgeDTOs = macroEdges.stream()
                .map(this::convertToMacroGraphEdgeDTO)
                .collect(Collectors.toList());

        // 사용자 현재 진도 조회
        Integer userCurrentChapter = getUserCurrentChapter(userId, bookId);

        return MacroGraphResponseDTO.builder()
                .nodes(nodes)
                .edges(edgeDTOs)
                .userCurrentChapter(userCurrentChapter)
                .build();
    }

    /**
     * Character를 GraphNodeDTO로 변환 (특정 이벤트에서의 중요도 사용)
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
     * EventRelationshipEdge를 MacroGraphEdgeDTO로 변환 (Fine Graph 형식과 동일)
     */
    private MacroGraphEdgeDTO convertToMacroGraphEdgeDTO(EventRelationshipEdge edge) {
        // relationTags를 List<String>으로 변환
        List<String> relationTags = new ArrayList<>();
        try {
            if (edge.getRelationTags() != null && !edge.getRelationTags().trim().isEmpty()) {
                // JSON 문자열을 파싱하여 List로 변환
                relationTags = objectMapper.readValue(edge.getRelationTags(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            // 파싱 실패 시 빈 리스트 사용
            log.warn("Failed to parse relationTags: {}", edge.getRelationTags(), e);
            relationTags = new ArrayList<>();
        }

        return MacroGraphEdgeDTO.builder()
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

    /**
     * 사용자의 현재 진도 챕터 조회 (스포일러 방지용)
     */
    private Integer getUserCurrentChapter(Long userId, Long bookId) {
        try {
            return userReadStateRepository.findByUserIdAndBookId(userId, bookId)
                    .map(userReadState -> userReadState.getLastReadChapterIdx())
                    .orElse(0); // 진도가 없으면 0 (모든 챕터 스포일러 처리)
        } catch (Exception e) {
            log.warn("Failed to get user current chapter for userId: {}, bookId: {}", userId, bookId, e);
            return 0; // 에러 시 안전하게 0 반환
        }
    }
}
