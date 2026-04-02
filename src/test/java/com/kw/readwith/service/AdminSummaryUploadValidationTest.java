package com.kw.readwith.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.V2TransitionGuard;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterPovSummaryRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import com.kw.readwith.util.LocatorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSummaryUploadValidationTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventRelationshipEdgeRepository eventRelationshipEdgeRepository;
    @Mock
    private CharacterPovSummaryRepository characterPovSummaryRepository;
    @Mock
    private EventCharacterStatRepository statRepository;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private BookAnalysisStatusService bookAnalysisStatusService;
    @Mock
    private LocatorSupport locatorSupport;
    @Mock
    private V2TransitionGuard transitionGuard;
    @Mock
    private NormalizationVersionService normalizationVersionService;

    @Spy
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("ko summary 업로드는 빈 items payload를 요약 완료로 처리하지 않는다")
    void uploadChapterSummariesRejectsEmptyKoItems() {
        Long bookId = 1L;
        Chapter chapter = Chapter.builder()
                .id(10L)
                .idx(1)
                .build();
        Book book = Book.builder()
                .id(bookId)
                .build();
        String jsonContent = """
                {
                  "chapterIndex": 1,
                  "language": "ko",
                  "items": []
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "chapter_001_summaries_Ko.json",
                "application/json",
                jsonContent.getBytes()
        );

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdAndIdx(bookId, 1)).thenReturn(Optional.of(chapter));
        when(characterPovSummaryRepository.existsByChapter(chapter)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> adminService.uploadChapterSummaries(bookId, java.util.List.of(file))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus._BAD_REQUEST);
        assertThat(chapter.isPovSummariesCached()).isFalse();
        verify(characterPovSummaryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(bookAnalysisStatusService).markRejectedIfPending(bookId);
    }
}
