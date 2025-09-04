package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.ChapterRelationshipEdge;
import com.kw.readwith.dto.graph.*;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MacroGraphService {

    private final BookRepository bookRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRelationshipEdgeRepository chapterRelationshipEdgeRepository;

    /**
     * 거시(챕터 누적) 그래프 조회
     */
    public MacroGraphResponseDTO getMacroGraph(Long bookId, Integer uptoChapter, Long userId) {
        // TODO: 사용자 인증 구현 후 접근 권한 검증 로직 추가
        
        // 책 존재 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        // 해당 챕터까지의 모든 관계 엣지 조회
        List<ChapterRelationshipEdge> edges = chapterRelationshipEdgeRepository
                .findByBookAndChapterIdxLessThanEqual(book, uptoChapter);

        // 관련된 인물들 추출
        Set<Long> characterIds = edges.stream()
                .flatMap(edge -> List.of(edge.getFromCharacter().getId(), edge.getToCharacter().getId()).stream())
                .collect(Collectors.toSet());

        List<Character> characters = characterRepository.findAllById(characterIds);

        // DTO 변환
        List<GraphNodeDTO> nodes = characters.stream()
                .map(this::convertToGraphNodeDTO)
                .collect(Collectors.toList());

        List<MacroGraphEdgeDTO> edgeDTOs = edges.stream()
                .map(this::convertToMacroGraphEdgeDTO)
                .collect(Collectors.toList());

        MacroGraphSummaryDTO summary = MacroGraphSummaryDTO.builder()
                .uptoChapter(uptoChapter)
                .totalCharacters(characters.size())
                .totalRelationships(edges.size())
                .build();

        return MacroGraphResponseDTO.builder()
                .nodes(nodes)
                .edges(edgeDTOs)
                .summary(summary)
                .build();
    }

    /**
     * Character를 GraphNodeDTO로 변환
     */
    private GraphNodeDTO convertToGraphNodeDTO(Character character) {
        return GraphNodeDTO.builder()
                .id(character.getId())
                .label(character.getName())
                .isMainCharacter(character.isMainCharacter())
                .profileImage(character.getProfileImage())
                .description(truncateText(character.getProfileText(), 200))
                .portraitPrompt(character.getPersonalityText())
                .names(character.getNames())
                .build();
    }

    /**
     * ChapterRelationshipEdge를 MacroGraphEdgeDTO로 변환
     */
    private MacroGraphEdgeDTO convertToMacroGraphEdgeDTO(ChapterRelationshipEdge edge) {
        return MacroGraphEdgeDTO.builder()
                .from(edge.getFromCharacter().getId())
                .to(edge.getToCharacter().getId())
                .cumulativeInteraction(edge.getCumulativeInteraction())
                .cumulativeSentiment(edge.getCumulativeInteraction() > 0 ? 
                    edge.getSentimentWeightedSum() / edge.getCumulativeInteraction() : 0.0)
                .sentimentWeightedSum((double) edge.getSentimentWeightedSum())
                .chapterIdx(edge.getChapterIdx())
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
}
