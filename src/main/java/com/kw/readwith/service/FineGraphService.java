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
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.util.LocatorSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FineGraphService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;
    private final ObjectMapper objectMapper;
    private final V2TransitionGuard transitionGuard;
    private final BookAccessPolicy bookAccessPolicy;
    private final LocatorSupport locatorSupport;

    public FineGraphResponseDTO getFineGraph(
            Long bookId,
            Integer chapterIdx,
            Integer eventIdx,
            LocatorDTO locator,
            Long userId
    ) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        if (!book.isAnalysisReady()) {
            return emptyResponse();
        }

        Event event = resolveEvent(book, chapterIdx, eventIdx, locator).orElse(null);
        if (event == null) {
            return emptyResponse();
        }

        transitionGuard.ensureEventLocatorReady(event, "fine graph 조회");

        List<EventRelationshipEdge> edges = eventRelationshipEdgeRepository.findByEvent(event);
        List<EventCharacterStat> stats = eventCharacterStatRepository.findByEvent(event);

        List<GraphNodeDTO> nodes = collectCharacters(edges, stats).values().stream()
                .map(character -> convertToGraphNodeDTO(character, findStat(stats, character)))
                .toList();

        List<FineGraphEdgeDTO> edgeDTOs = edges.stream()
                .map(this::convertToFineGraphEdgeDTO)
                .toList();

        EventInfoDTO eventInfo = EventInfoDTO.builder()
                .chapterIdx(event.getChapter().getIdx())
                .eventIdx(event.getIdx())
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

    private FineGraphResponseDTO emptyResponse() {
        return FineGraphResponseDTO.builder()
                .nodes(List.of())
                .edges(List.of())
                .eventInfo(null)
                .build();
    }

    private Optional<Event> resolveEvent(Book book, Integer chapterIdx, Integer eventIdx, LocatorDTO locator) {
        if (locator != null) {
            return resolveEventByLocator(book, locator);
        }
        if (chapterIdx == null || eventIdx == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "chapterIdx/eventIdx or locator is required.");
        }

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), chapterIdx)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        return eventRepository.findByChapterAndIdx(chapter, eventIdx);
    }

    private Optional<Event> resolveEventByLocator(Book book, LocatorDTO locator) {
        if (locator.getChapterIndex() == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "locator.chapterIndex is required.");
        }

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), locator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        transitionGuard.ensureLocatorMetadataReady(book, chapter, "fine graph 조회");

        int txtOffset = locatorSupport.toTxtOffset(chapter, locator);
        List<Event> events = eventRepository.findByChapterOrderByIdx(chapter);

        return events.stream()
                .filter(event -> containsOffset(event, txtOffset))
                .findFirst()
                .or(() -> events.stream()
                        .filter(event -> event.getStartTxtOffset() <= txtOffset)
                        .reduce((first, second) -> second));
    }

    private boolean containsOffset(Event event, int txtOffset) {
        return event.getStartTxtOffset() <= txtOffset && txtOffset < event.getEndTxtOffset();
    }

    private Map<Long, Character> collectCharacters(List<EventRelationshipEdge> edges, List<EventCharacterStat> stats) {
        Map<Long, Character> characters = new LinkedHashMap<>();
        for (EventCharacterStat stat : stats) {
            characters.put(stat.getCharacter().getId(), stat.getCharacter());
        }
        for (EventRelationshipEdge edge : edges) {
            characters.put(edge.getFromCharacter().getId(), edge.getFromCharacter());
            characters.put(edge.getToCharacter().getId(), edge.getToCharacter());
        }
        return characters;
    }

    private EventCharacterStat findStat(List<EventCharacterStat> stats, Character character) {
        return stats.stream()
                .filter(stat -> stat.getCharacter().getId().equals(character.getId()))
                .findFirst()
                .orElse(null);
    }

    private GraphNodeDTO convertToGraphNodeDTO(Character character, EventCharacterStat characterStat) {
        Float weight = characterStat != null ? (float) characterStat.getNodeWeight() : null;

        return GraphNodeDTO.builder()
                .id(character.getCharacterId())
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
                .from(edge.getFromCharacter().getCharacterId())
                .to(edge.getToCharacter().getCharacterId())
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
