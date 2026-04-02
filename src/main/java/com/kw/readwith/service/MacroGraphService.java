package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.graph.GraphNodeDTO;
import com.kw.readwith.dto.graph.MacroGraphEdgeDTO;
import com.kw.readwith.dto.graph.MacroGraphResponseDTO;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MacroGraphService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final EventRepository eventRepository;
    private final EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    private final EventCharacterStatRepository eventCharacterStatRepository;
    private final ObjectMapper objectMapper;
    private final BookAccessPolicy bookAccessPolicy;
    private final LocatorSupport locatorSupport;

    public MacroGraphResponseDTO getMacroGraph(Long bookId, Integer uptoChapter, LocatorDTO uptoLocator, Long userId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
        bookAccessPolicy.ensureReadable(book, userId);

        Integer effectiveChapter = resolveEffectiveChapter(book, uptoChapter, uptoLocator);
        if (!book.isAnalysisReady()) {
            return emptyResponse(effectiveChapter);
        }

        List<Event> selectedEvents = resolveSelectedEvents(book, uptoChapter, uptoLocator);
        if (selectedEvents.isEmpty()) {
            return emptyResponse(effectiveChapter);
        }

        List<EventRelationshipEdge> macroEdges = eventRelationshipEdgeRepository.findByEventIn(selectedEvents);
        List<EventCharacterStat> macroStats = eventCharacterStatRepository.findByEventIn(selectedEvents);

        Map<Long, EventCharacterStat> latestStats = latestStatsByCharacter(selectedEvents, macroStats);
        Map<Long, Integer> statCounts = statCountsByCharacter(macroStats);

        List<GraphNodeDTO> nodes = collectCharacters(macroEdges, macroStats).values().stream()
                .map(character -> convertToGraphNodeDTO(
                        character,
                        latestStats.get(character.getId()),
                        statCounts.get(character.getId())
                ))
                .toList();

        List<MacroGraphEdgeDTO> edgeDTOs = macroEdges.stream()
                .map(this::convertToMacroGraphEdgeDTO)
                .toList();

        return MacroGraphResponseDTO.builder()
                .nodes(nodes)
                .edges(edgeDTOs)
                .userCurrentChapter(effectiveChapter)
                .build();
    }

    private MacroGraphResponseDTO emptyResponse(Integer chapterIdx) {
        return MacroGraphResponseDTO.builder()
                .nodes(List.of())
                .edges(List.of())
                .userCurrentChapter(chapterIdx)
                .build();
    }

    private Integer resolveEffectiveChapter(Book book, Integer uptoChapter, LocatorDTO uptoLocator) {
        if (uptoLocator != null) {
            if (uptoLocator.getChapterIndex() == null) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "uptoLocator.chapterIndex is required.");
            }
            return uptoLocator.getChapterIndex();
        }
        if (uptoChapter == null) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "uptoChapter or uptoLocator is required.");
        }
        return uptoChapter;
    }

    private List<Event> resolveSelectedEvents(Book book, Integer uptoChapter, LocatorDTO uptoLocator) {
        if (uptoLocator == null) {
            return eventRepository.findLastEventsByBookAndUptoChapter(book, uptoChapter);
        }

        Chapter chapter = chapterRepository.findByBookIdAndIdx(book.getId(), uptoLocator.getChapterIndex())
                .orElseThrow(() -> new GeneralException(ErrorStatus.CHAPTER_NOT_FOUND));
        int uptoTxtOffset = locatorSupport.toTxtOffset(chapter, uptoLocator);

        List<Event> events = eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book);
        Map<Integer, Event> selectedByChapter = new LinkedHashMap<>();
        for (Event event : events) {
            int chapterIdx = event.getChapter().getIdx();
            if (chapterIdx > chapter.getIdx()) {
                break;
            }
            if (chapterIdx < chapter.getIdx()) {
                selectedByChapter.put(chapterIdx, event);
                continue;
            }
            if (event.getStartTxtOffset() <= uptoTxtOffset) {
                selectedByChapter.put(chapterIdx, event);
            }
        }
        return new ArrayList<>(selectedByChapter.values());
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

    private Map<Long, EventCharacterStat> latestStatsByCharacter(List<Event> selectedEvents, List<EventCharacterStat> stats) {
        Map<Long, Integer> orderByEventId = new LinkedHashMap<>();
        for (int i = 0; i < selectedEvents.size(); i++) {
            orderByEventId.put(selectedEvents.get(i).getId(), i);
        }

        Map<Long, EventCharacterStat> latestStats = new LinkedHashMap<>();
        stats.stream()
                .sorted((left, right) -> Integer.compare(
                        orderByEventId.getOrDefault(left.getEvent().getId(), -1),
                        orderByEventId.getOrDefault(right.getEvent().getId(), -1)
                ))
                .forEach(stat -> latestStats.put(stat.getCharacter().getId(), stat));
        return latestStats;
    }

    private Map<Long, Integer> statCountsByCharacter(List<EventCharacterStat> stats) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (EventCharacterStat stat : stats) {
            counts.merge(stat.getCharacter().getId(), 1, Integer::sum);
        }
        return counts;
    }

    private GraphNodeDTO convertToGraphNodeDTO(Character character, EventCharacterStat stat, Integer count) {
        Float weight = stat != null ? (float) stat.getNodeWeight() : null;

        return GraphNodeDTO.builder()
                .id(character.getCharacterId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getPersonalityText(), 200))
                .portraitPrompt(character.getProfileText())
                .names(parseNames(character.getNames(), character.getName()))
                .weight(weight)
                .count(count)
                .build();
    }

    private MacroGraphEdgeDTO convertToMacroGraphEdgeDTO(EventRelationshipEdge edge) {
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

        return MacroGraphEdgeDTO.builder()
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
}
