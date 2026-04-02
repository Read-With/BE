package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.EventCharacterStatRepository;
import com.kw.readwith.repository.EventRelationshipEdgeRepository;
import com.kw.readwith.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookAnalysisStatusServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRelationshipEdgeRepository eventRelationshipEdgeRepository;

    @Mock
    private EventCharacterStatRepository eventCharacterStatRepository;

    @InjectMocks
    private BookAnalysisStatusService bookAnalysisStatusService;

    @Test
    @DisplayName("정규화와 분석 입력이 모두 갖춰지면 analysisStatus를 READY로 올린다")
    void refreshStatusMarksReadyWhenAnalysisIsComplete() {
        Book book = Book.builder()
                .id(1L)
                .normalizationStatus(NormalizationStatus.READY)
                .analysisStatus(AnalysisStatus.NONE)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(characterRepository.existsByBook(book)).thenReturn(true);
        when(eventRepository.existsByBook(book)).thenReturn(true);
        when(chapterRepository.findByBookId(1L)).thenReturn(List.of(
                Chapter.builder().idx(1).povSummariesCached(true).build(),
                Chapter.builder().idx(2).povSummariesCached(true).build()
        ));
        when(eventRelationshipEdgeRepository.existsByBook(book)).thenReturn(false);
        when(eventCharacterStatRepository.existsByBook(book)).thenReturn(true);

        bookAnalysisStatusService.refreshStatus(1L);

        assertThat(book.getAnalysisStatus()).isEqualTo(AnalysisStatus.READY);
        assertThat(book.isSummary()).isTrue();
    }

    @Test
    @DisplayName("분석 입력이 부분 상태면 analysisStatus를 NONE으로 유지한다")
    void refreshStatusResetsToNoneWhenAnalysisIsIncomplete() {
        Book book = Book.builder()
                .id(1L)
                .normalizationStatus(NormalizationStatus.READY)
                .analysisStatus(AnalysisStatus.READY)
                .summary(true)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(characterRepository.existsByBook(book)).thenReturn(true);
        when(eventRepository.existsByBook(book)).thenReturn(true);
        when(chapterRepository.findByBookId(1L)).thenReturn(List.of(
                Chapter.builder().idx(1).povSummariesCached(true).build(),
                Chapter.builder().idx(2).povSummariesCached(false).build()
        ));

        bookAnalysisStatusService.refreshStatus(1L);

        assertThat(book.getAnalysisStatus()).isEqualTo(AnalysisStatus.NONE);
        assertThat(book.isSummary()).isFalse();
    }

    @Test
    @DisplayName("READY 전 책에서 검증 실패가 나면 analysisStatus를 REJECTED로 기록한다")
    void markRejectedIfPendingMarksRejected() {
        Book book = Book.builder()
                .id(1L)
                .analysisStatus(AnalysisStatus.NONE)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookAnalysisStatusService.markRejectedIfPending(1L);

        assertThat(book.getAnalysisStatus()).isEqualTo(AnalysisStatus.REJECTED);
        assertThat(book.isSummary()).isFalse();
    }

    @Test
    @DisplayName("이미 READY인 책은 재업로드 실패가 나도 READY를 유지한다")
    void markRejectedIfPendingLeavesReadyBookUntouched() {
        Book book = Book.builder()
                .id(1L)
                .analysisStatus(AnalysisStatus.READY)
                .summary(true)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookAnalysisStatusService.markRejectedIfPending(1L);

        assertThat(book.getAnalysisStatus()).isEqualTo(AnalysisStatus.READY);
        assertThat(book.isSummary()).isTrue();
    }
}
