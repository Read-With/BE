package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.enums.NormalizationVersionStatus;
import com.kw.readwith.dto.book.BookDetailDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.FavoriteRepository;
import com.kw.readwith.service.BookUploadTransactionService.BookUploadCompletion;
import com.kw.readwith.service.normalization.EpubMetadataExtractorService;
import com.kw.readwith.service.normalization.ExtractedEpubCover;
import com.kw.readwith.service.normalization.ExtractedEpubMetadata;
import com.kw.readwith.service.normalization.NormalizationJobDispatcher;
import com.kw.readwith.service.normalization.NormalizationVersionService;
import com.kw.readwith.service.normalization.NormalizedArtifactStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private EpubMetadataExtractorService epubMetadataExtractorService;

    @Mock
    private NormalizedArtifactStorageService normalizedArtifactStorageService;

    @Mock
    private NormalizationJobDispatcher normalizationJobDispatcher;

    @Mock
    private NormalizationVersionService normalizationVersionService;

    @Mock
    private BookUploadTransactionService bookUploadTransactionService;

    @InjectMocks
    private BookService bookService;

    @BeforeEach
    void setUp() {
        lenient().when(normalizationVersionService.resolveStatus(any(Book.class))).thenReturn(NormalizationVersionStatus.NOT_READY);
        lenient().when(normalizationVersionService.needsRenormalization(any(Book.class))).thenReturn(false);
    }

    @Test
    @DisplayName("uploadBook stores external artifacts before completing DB upload state")
    void uploadBookStoresExternalArtifactsBeforeCompletingDbState() {
        MockMultipartFile file = sampleEpubFile();
        String sourcePath = "books/101/source/source-v1/book.epub";
        String coverUrl = "https://cdn.readwith.store/public/books/101/covers/source-v1/cover.jpg";

        when(epubMetadataExtractorService.extract(file))
                .thenReturn(new ExtractedEpubMetadata(
                        "Dracula",
                        "Bram Stoker",
                        "en",
                        new ExtractedEpubCover("cover.jpg", "image/jpeg", "cover".getBytes(StandardCharsets.UTF_8))
                ));
        when(bookUploadTransactionService.createUploadBook(1L, "Dracula", "Bram Stoker", "en"))
                .thenReturn(uploadBookShell());
        when(normalizedArtifactStorageService.newSourceVersion()).thenReturn("source-v1");
        when(normalizedArtifactStorageService.storeSourceEpub(101L, "source-v1", file)).thenReturn(sourcePath);
        when(normalizedArtifactStorageService.storeBookCover(eq(101L), eq("source-v1"), any(ExtractedEpubCover.class)))
                .thenReturn(coverUrl);
        when(bookUploadTransactionService.completeUpload(101L, "source-v1", sourcePath, coverUrl))
                .thenReturn(new BookUploadCompletion(completedBook(sourcePath, coverUrl), 55L));

        BookDetailDTO response = bookService.uploadBook(1L, file, null, " ", null);

        InOrder inOrder = inOrder(bookUploadTransactionService, normalizedArtifactStorageService, normalizationJobDispatcher);
        inOrder.verify(bookUploadTransactionService).createUploadBook(1L, "Dracula", "Bram Stoker", "en");
        inOrder.verify(normalizedArtifactStorageService).newSourceVersion();
        inOrder.verify(normalizedArtifactStorageService).storeSourceEpub(101L, "source-v1", file);
        inOrder.verify(normalizedArtifactStorageService).storeBookCover(eq(101L), eq("source-v1"), any(ExtractedEpubCover.class));
        inOrder.verify(bookUploadTransactionService).completeUpload(101L, "source-v1", sourcePath, coverUrl);
        inOrder.verify(normalizationJobDispatcher).dispatch(55L);

        assertThat(response.getTitle()).isEqualTo("Dracula");
        assertThat(response.getAuthor()).isEqualTo("Bram Stoker");
        assertThat(response.getLanguage()).isEqualTo("en");
        assertThat(response.getCoverImgUrl()).isEqualTo(coverUrl);
        assertThat(response.getNormalizationStatus()).isEqualTo(NormalizationStatus.QUEUED.name());
        assertThat(response.getAnalysisStatus()).isEqualTo(AnalysisStatus.NONE.name());
    }

    @Test
    @DisplayName("uploadBook requires title and author metadata from the EPUB package")
    void uploadBookRequiresTitleAndAuthorMetadataFromEpub() {
        MockMultipartFile file = sampleEpubFile();

        when(epubMetadataExtractorService.extract(file)).thenReturn(ExtractedEpubMetadata.empty());

        assertThrows(GeneralException.class, () -> bookService.uploadBook(1L, file, "Manual Title", "Manual Author", "ko"));
        verifyNoInteractions(bookUploadTransactionService);
    }

    @Test
    @DisplayName("uploadBook continues when cover upload fails")
    void uploadBookContinuesWhenCoverUploadFails() {
        MockMultipartFile file = sampleEpubFile();
        String sourcePath = "books/101/source/source-v1/book.epub";

        when(epubMetadataExtractorService.extract(file))
                .thenReturn(new ExtractedEpubMetadata(
                        "Dracula",
                        "Bram Stoker",
                        "en",
                        new ExtractedEpubCover("cover.jpg", "image/jpeg", "cover".getBytes(StandardCharsets.UTF_8))
                ));
        when(bookUploadTransactionService.createUploadBook(1L, "Dracula", "Bram Stoker", "en"))
                .thenReturn(uploadBookShell());
        when(normalizedArtifactStorageService.newSourceVersion()).thenReturn("source-v1");
        when(normalizedArtifactStorageService.storeSourceEpub(101L, "source-v1", file)).thenReturn(sourcePath);
        doThrow(new IllegalStateException("cover upload failed"))
                .when(normalizedArtifactStorageService)
                .storeBookCover(eq(101L), eq("source-v1"), any(ExtractedEpubCover.class));
        when(bookUploadTransactionService.completeUpload(101L, "source-v1", sourcePath, null))
                .thenReturn(new BookUploadCompletion(completedBook(sourcePath, null), 55L));

        BookDetailDTO response = bookService.uploadBook(1L, file, null, null, null);

        assertThat(response.getTitle()).isEqualTo("Dracula");
        assertThat(response.getCoverImgUrl()).isNull();
        verify(normalizationJobDispatcher).dispatch(55L);
    }

    @Test
    @DisplayName("uploadBook marks upload failed when source EPUB staging fails")
    void uploadBookMarksFailedWhenSourceUploadFails() {
        MockMultipartFile file = sampleEpubFile();

        when(epubMetadataExtractorService.extract(file))
                .thenReturn(new ExtractedEpubMetadata("Dracula", "Bram Stoker", "en", null));
        when(bookUploadTransactionService.createUploadBook(1L, "Dracula", "Bram Stoker", "en"))
                .thenReturn(uploadBookShell());
        when(normalizedArtifactStorageService.newSourceVersion()).thenReturn("source-v1");
        doThrow(new IllegalStateException("source upload failed"))
                .when(normalizedArtifactStorageService)
                .storeSourceEpub(101L, "source-v1", file);

        assertThrows(IllegalStateException.class, () -> bookService.uploadBook(1L, file, null, null, null));

        verify(bookUploadTransactionService).markUploadFailed(101L);
        verify(bookUploadTransactionService, never()).completeUpload(any(), any(), any(), any());
        verifyNoInteractions(normalizationJobDispatcher);
    }

    @Test
    @DisplayName("uploadBook marks upload failed when DB completion fails after source staging")
    void uploadBookMarksFailedWhenCompletionFails() {
        MockMultipartFile file = sampleEpubFile();
        String sourcePath = "books/101/source/source-v1/book.epub";

        when(epubMetadataExtractorService.extract(file))
                .thenReturn(new ExtractedEpubMetadata("Dracula", "Bram Stoker", "en", null));
        when(bookUploadTransactionService.createUploadBook(1L, "Dracula", "Bram Stoker", "en"))
                .thenReturn(uploadBookShell());
        when(normalizedArtifactStorageService.newSourceVersion()).thenReturn("source-v1");
        when(normalizedArtifactStorageService.storeSourceEpub(101L, "source-v1", file)).thenReturn(sourcePath);
        doThrow(new IllegalStateException("db completion failed"))
                .when(bookUploadTransactionService)
                .completeUpload(101L, "source-v1", sourcePath, null);

        assertThrows(IllegalStateException.class, () -> bookService.uploadBook(1L, file, null, null, null));

        verify(bookUploadTransactionService).markUploadFailed(101L);
        verifyNoInteractions(normalizationJobDispatcher);
    }

    private MockMultipartFile sampleEpubFile() {
        return new MockMultipartFile(
                "file",
                "dracula.epub",
                "application/epub+zip",
                "epub".getBytes(StandardCharsets.UTF_8)
        );
    }

    private Book uploadBookShell() {
        return Book.builder()
                .id(101L)
                .title("Dracula")
                .author("Bram Stoker")
                .language("en")
                .isDefault(false)
                .summary(false)
                .build();
    }

    private Book completedBook(String sourcePath, String coverUrl) {
        Book book = uploadBookShell();
        book.assignUploadedSource(sourcePath);
        if (coverUrl != null) {
            book.updateCoverImage(coverUrl);
        }
        book.markNormalizationQueued();
        book.resetAnalysisStatus();
        return book;
    }
}
