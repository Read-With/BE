package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.EventInfoDTO;
import com.kw.readwith.dto.graph.FineGraphEdgeDTO;
import com.kw.readwith.dto.graph.FineGraphResponseDTO;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
    private final V2TransitionGuard transitionGuard;
    private final BookAccessPolicy bookAccessPolicy;

    public FineGraphResponseDTO getFineGraph(Long bookId, Integer chapterIdx, Integer eventIdx, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        if (!book.isAnalysisReady()) {
            return FineGraphResponseDTO.builder()
                    .nodes(List.of())
                    .edges(List.of())
                    .eventInfo(null)
                    .build();
        }

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        Event event = eventRepository.findByChapterAndIdx(chapter, eventIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.EVENT_NOT_FOUND));
        transitionGuard.ensureEventLocatorReady(event, "fine graph 조회");

        List<EventRelationshipEdge> edges = eventRelationshipEdgeRepository.findByEvent(event);
        Set<Long> characterIds = edges.stream()
                .flatMap(edge -> List.of(edge.getFromCharacter().getId(), edge.getToCharacter().getId()).stream())
                .collect(Collectors.toSet());

        List<GraphNodeDTO> nodes = characterRepository.findAllById(characterIds).stream()
                .map(character -> convertToGraphNodeDTO(character, event))
                .toList();

        List<FineGraphEdgeDTO> edgeDTOs = edges.stream()
                .map(this::convertToFineGraphEdgeDTO)
                .toList();

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

    private GraphNodeDTO convertToGraphNodeDTO(Character character, Event event) {
        EventCharacterStat characterStat = eventCharacterStatRepository
                .findByEventAndCharacter(event, character)
                .orElse(null);

        Float weight = characterStat != null ? (float) characterStat.getNodeWeight() : null;

        return GraphNodeDTO.builder()
                .id(character.getId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getPersonalityText(), 200))
                .portraitPrompt(character.getProfileText())
                .names(parseNames(character.getNames(), character.getName()))
                .weight(weight)
                .count(null)
                .build();
    }

    private FineGraphEdgeDTO convertToFineGraphEdgeDTO(EventRelationshipEdge edge) {
        List<String> relationTags = new ArrayList<>();
        try {
            if (edge.getRelationTags() != null && !edge.getRelationTags().trim().isEmpty()) {
                relationTags = objectMapper.readValue(
                        edge.getRelationTags(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to parse relationTags: {}", edge.getRelationTags(), e);
        }

        return FineGraphEdgeDTO.builder()
                .from(edge.getFromCharacter().getId())
                .to(edge.getToCharacter().getId())
                .sentimentScore(edge.getSentimentScore() != null ? edge.getSentimentScore().doubleValue() : 0.0)
                .interactionCount(edge.getInteractionCount())
                .relationTags(relationTags)
                .build();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String cleanText = text.replaceAll("\\s+", " ").trim();
        if (cleanText.length() <= maxLength) {
            return cleanText;
        }
        return cleanText.substring(0, maxLength - 3) + "...";
    }

    private List<String> parseNames(String names, String fallbackName) {
        if (names == null || names.trim().isEmpty()) {
            return List.of(fallbackName);
        }
        try {
            List<String> nameList = Arrays.stream(names.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .toList();
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
