package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.NormalizationVersionStatus;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.service.normalization.EpubMetadataExtractorService;
import com.kw.readwith.service.normalization.ExtractedEpubMetadata;
import com.kw.readwith.service.normalization.NormalizationJobDispatcher;
import com.kw.readwith.service.normalization.NormalizationJobService;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EpubMetadataExtractorService epubMetadataExtractorService;

    @Mock
    private NormalizedArtifactStorageService normalizedArtifactStorageService;

    @Mock
    private NormalizationJobService normalizationJobService;

    @Mock
    private NormalizationJobDispatcher normalizationJobDispatcher;

    @Mock
    private NormalizationVersionService normalizationVersionService;

    @InjectMocks
    private BookService bookService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        lenient().when(normalizationVersionService.resolveStatus(any(Book.class))).thenReturn(NormalizationVersionStatus.NOT_READY);
        lenient().when(normalizationVersionService.needsRenormalization(any(Book.class))).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("uploadBook stores extracted EPUB metadata when request metadata is missing")
    void uploadBookUsesExtractedMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dracula.epub",
                "application/epub+zip",
                "epub".getBytes(StandardCharsets.UTF_8)
        );
        User uploader = sampleUser();

        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(epubMetadataExtractorService.extract(file))
                .thenReturn(new ExtractedEpubMetadata("Dracula", "Bram Stoker", "en"));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });
        when(normalizedArtifactStorageService.newSourceVersion()).thenReturn("source-v1");
        when(normalizedArtifactStorageService.storeSourceEpub(101L, "source-v1", file))
                .thenReturn("books/101/source/source-v1/book.epub");
        when(normalizationJobService.createQueuedJob(any(Book.class), eq("source-v1"), eq("UPLOAD")))
                .thenAnswer(invocation -> ProcessingJob.builder()
                        .id(55L)
                        .book(invocation.getArgument(0))
                        .pipelineType(ProcessingPipelineType.NORMALIZATION)
                        .runId("run-1")
                        .sourceVersion("source-v1")
                        .status(ProcessingJobStatus.QUEUED)
                        .build());

        BookDetailDTO response = bookService.uploadBook(1L, file, null, " ", null);

        ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(bookCaptor.capture());
        Book savedBook = bookCaptor.getValue();

        assertThat(savedBook.getTitle()).isEqualTo("Dracula");
        assertThat(savedBook.getAuthor()).isEqualTo("Bram Stoker");
        assertThat(savedBook.getLanguage()).isEqualTo("en");
        assertThat(savedBook.getNormalizationStatus()).isEqualTo(NormalizationStatus.QUEUED);
        assertThat(savedBook.getAnalysisStatus()).isEqualTo(AnalysisStatus.NONE);

        assertThat(response.getTitle()).isEqualTo("Dracula");
        assertThat(response.getAuthor()).isEqualTo("Bram Stoker");
        assertThat(response.getLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("uploadBook는 제목/저자 메타데이터가 없으면 요청값이 있어도 실패한다")
    void uploadBookRequiresTitleAndAuthorMetadataFromEpub() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "custom.epub",
                "application/epub+zip",
                "epub".getBytes(StandardCharsets.UTF_8)
        );
        User uploader = sampleUser();

        when(userRepository.findById(1L)).thenReturn(Optional.of(uploader));
        when(epubMetadataExtractorService.extract(file)).thenReturn(ExtractedEpubMetadata.empty());

        assertThrows(GeneralException.class, () -> bookService.uploadBook(1L, file, "Manual Title", "Manual Author", "ko"));
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
