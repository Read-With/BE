package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.service.BookUploadTransactionService.BookUploadCompletion;
import com.kw.readwith.service.normalization.NormalizationJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookUploadTransactionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private NormalizationJobService normalizationJobService;

    @InjectMocks
    private BookUploadTransactionService bookUploadTransactionService;

    @Test
    @DisplayName("createUploadBook stores only the initial book row")
    void createUploadBookStoresInitialBookRow() {
        User uploader = sampleUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(bookRepository.save(org.mockito.ArgumentMatchers.any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book book = bookUploadTransactionService.createUploadBook(1L, "Dracula", "Bram Stoker", "en");

        ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getTitle()).isEqualTo("Dracula");
        assertThat(bookCaptor.getValue().getAuthor()).isEqualTo("Bram Stoker");
        assertThat(bookCaptor.getValue().getLanguage()).isEqualTo("en");
        assertThat(bookCaptor.getValue().getUploadedBy()).isEqualTo(uploader);
        assertThat(book).isSameAs(bookCaptor.getValue());
    }

    @Test
    @DisplayName("completeUpload attaches artifacts and creates normalization job in one short DB transaction")
    void completeUploadAttachesArtifactsAndCreatesJob() {
        Book book = Book.builder()
                .id(101L)
                .title("Dracula")
                .author("Bram Stoker")
                .language("en")
                .isDefault(false)
                .summary(false)
                .build();

        when(bookRepository.findById(101L)).thenReturn(Optional.of(book));
        when(normalizationJobService.createQueuedJob(book, "source-v1", "UPLOAD"))
                .thenReturn(ProcessingJob.builder()
                        .id(55L)
                        .book(book)
                        .pipelineType(ProcessingPipelineType.NORMALIZATION)
                        .runId("run-1")
                        .sourceVersion("source-v1")
                        .status(ProcessingJobStatus.QUEUED)
                        .build());

        BookUploadCompletion completion = bookUploadTransactionService.completeUpload(
                101L,
                "source-v1",
                "books/101/source/source-v1/book.epub",
                "https://cdn.readwith.store/public/books/101/covers/source-v1/cover.jpg"
        );

        assertThat(completion.book()).isSameAs(book);
        assertThat(completion.jobId()).isEqualTo(55L);
        assertThat(book.getEpubPath()).isEqualTo("books/101/source/source-v1/book.epub");
        assertThat(book.getCoverImgUrl()).isEqualTo("https://cdn.readwith.store/public/books/101/covers/source-v1/cover.jpg");
        assertThat(book.getNormalizationStatus()).isEqualTo(NormalizationStatus.QUEUED);
        assertThat(book.getAnalysisStatus()).isEqualTo(AnalysisStatus.NONE);
        verify(normalizationJobService).createQueuedJob(eq(book), eq("source-v1"), eq("UPLOAD"));
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("user")
                .provider(Provider.GOOGLE)
                .providerUid("provider-uid")
                .isAdmin(false)
                .build();
    }
}
