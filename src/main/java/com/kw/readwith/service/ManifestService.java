package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.dto.common.LocatorDTO;
import com.kw.readwith.dto.manifest.BookManifestDTO;
import com.kw.readwith.dto.manifest.ChapterLengthDTO;
import com.kw.readwith.dto.manifest.ChapterManifestDTO;
import com.kw.readwith.dto.manifest.CharacterManifestDTO;
import com.kw.readwith.dto.manifest.EventManifestDTO;
import com.kw.readwith.dto.manifest.ManifestResponseDTO;
import com.kw.readwith.dto.manifest.ProgressMetadataDTO;
import com.kw.readwith.dto.manifest.ReaderArtifactsDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManifestService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final EventRepository eventRepository;
    private final V2TransitionGuard transitionGuard;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;
    private final NormalizationVersionService normalizationVersionService;

    public ManifestResponseDTO getBookManifest(Long bookId, Long userId) {
        Book book = validateAndGetBook(bookId, userId);
        if (!book.isNormalizationReady()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "정규화가 완료되지 않은 책입니다.");
        }

        List<Chapter> chapters = chapterRepository.findByBookId(bookId);
        boolean analysisReady = book.isAnalysisReady();
        List<Character> characters = analysisReady
                ? characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book)
                : List.of();
        List<Event> events = analysisReady
                ? eventRepository.findByBookOrderByChapterIdxAscIdxAsc(book)
                : List.of();
        Map<Long, List<Event>> eventsByChapter = events.stream()
                .collect(Collectors.groupingBy(event -> event.getChapter().getId()));

        transitionGuard.ensureManifestReady(book, chapters, events);

        return ManifestResponseDTO.builder()
                .book(convertToBookManifestDTO(book))
                .chapters(convertToChapterManifestDTOs(chapters, eventsByChapter))
                .characters(convertToCharacterManifestDTOs(characters))
                .readerArtifacts(ReaderArtifactsDTO.builder()
                        .combinedXhtmlPath(normalizedArtifactStorageService.resolveCombinedXhtmlUrl(book.getNormalizedArtifactPath()))
                        .dataAttributes(List.of("data-chapter-index", "data-block-index", "data-spine-href"))
                        .build())
                .progressMetadata(calculateProgressMetadata(book))
                .build();
    }

    private Book validateAndGetBook(Long bookId, Long userId) {
        return bookRepository.findByIdAndNormalizationStatus(bookId, NormalizationStatus.READY)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));
    }

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
                .normalizationStatus(enumName(book.getNormalizationStatus()))
                .analysisStatus(enumName(book.getAnalysisStatus()))
                .ruleVersion(book.getRuleVersion())
                .locatorVersion(book.getLocatorVersion())
                .normalizationRunId(book.getNormalizationRunId())
                .normalizationVersionStatus(normalizationVersionService.resolveStatus(book).name())
                .needsRenormalization(normalizationVersionService.needsRenormalization(book))
                .normalizedArtifactPath(book.getNormalizedArtifactPath())
                .build();
    }

    private List<ChapterManifestDTO> convertToChapterManifestDTOs(
            List<Chapter> chapters,
            Map<Long, List<Event>> eventsByChapter
    ) {
        return chapters.stream()
                .map(chapter -> ChapterManifestDTO.builder()
                        .idx(chapter.getIdx())
                        .title(chapter.getTitle())
                        .spineHref(chapter.getSpineHref())
                        .paragraphCount(chapter.getParagraphCount())
                        .paragraphStartsJson(chapter.getParagraphStartsJson())
                        .paragraphLengthsJson(chapter.getParagraphLengthsJson())
                        .totalCodePoints(chapter.getTotalCodePoints())
                        .startPos(chapter.getStartPos())
                        .endPos(chapter.getEndPos())
                        .rawText(truncateText(chapter.getRawText(), 200))
                        .summaryText(chapter.getSummaryText())
                        .summaryUploadUrl(chapter.getSummaryUploadUrl())
                        .povSummariesCached(chapter.isPovSummariesCached())
                        .events(convertToEventManifestDTOs(eventsByChapter.getOrDefault(chapter.getId(), List.of())))
                        .build())
                .toList();
    }

    private List<EventManifestDTO> convertToEventManifestDTOs(List<Event> events) {
        return events.stream()
                .map(event -> EventManifestDTO.builder()
                        .idx(event.getIdx())
                        .eventId(event.getEventId())
                        .startLocator(buildEventLocator(event, true))
                        .endLocator(buildEventLocator(event, false))
                        .startTxtOffset(event.getStartTxtOffset())
                        .endTxtOffset(event.getEndTxtOffset())
                        .rawText(truncateText(event.getRawText(), 300))
                        .build())
                .toList();
    }

    private List<CharacterManifestDTO> convertToCharacterManifestDTOs(List<Character> characters) {
        return characters.stream()
                .map(character -> CharacterManifestDTO.builder()
                        .id(character.getCharacterId())
                        .name(character.getName())
                        .names(character.getNames())
                        .profileImage(character.getProfileImage())
                        .isMainCharacter(character.isMainCharacter())
                        .firstChapterIdx(character.getFirstChapterIdx())
                        .personalityText(character.getPersonalityText())
                        .profileText(character.getProfileText())
                        .build())
                .toList();
    }

    private ProgressMetadataDTO calculateProgressMetadata(Book book) {
        Integer maxChapter = chapterRepository.findMaxChapterIdxByBook(book);
        if (maxChapter == null) {
            maxChapter = 0;
        }

        List<ChapterLengthDTO> chapterLengths = chapterRepository.findChapterLengthsByBook(book).stream()
                .map(data -> ChapterLengthDTO.builder()
                        .chapterIdx((Integer) data[0])
                        .length((Integer) data[1])
                        .build())
                .toList();

        int totalLength = chapterLengths.stream().mapToInt(ChapterLengthDTO::getLength).sum();

        return ProgressMetadataDTO.builder()
                .maxChapter(maxChapter)
                .chapterLengths(chapterLengths)
                .totalLength(Math.max(totalLength, 0))
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

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
