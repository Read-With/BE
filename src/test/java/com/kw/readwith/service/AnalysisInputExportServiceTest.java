package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.ArtifactStorageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.dto.admin.AnalysisInputResponseDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisInputExportServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private AmazonS3Manager amazonS3Manager;

    @Mock
    private ArtifactStorageProperties artifactStorageProperties;

    @InjectMocks
    private AnalysisInputExportService analysisInputExportService;

    @Test
    @DisplayName("정규화 산출물 export는 meta와 chapter txt presigned URL을 함께 내려준다")
    void getAnalysisInputReturnsPresignedUrls() {
        Book book = Book.builder()
                .id(42L)
                .title("Sherlock Holmes")
                .author("Arthur Conan Doyle")
                .language("en")
                .normalizationStatus(NormalizationStatus.READY)
                .normalizedArtifactPath("books/42/normalizations/run-1")
                .normalizationRunId("run-1")
                .ruleVersion("v1")
                .locatorVersion("v2")
                .build();
        Chapter chapter1 = Chapter.builder().idx(1).title("Chapter 1").build();
        Chapter chapter2 = Chapter.builder().idx(2).title("Chapter 2").build();

        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookId(42L)).thenReturn(List.of(chapter2, chapter1));
        when(artifactStorageProperties.getPrivatePrefix()).thenReturn("private");
        when(artifactStorageProperties.getPrivateUrlTtl()).thenReturn(Duration.ofHours(1));
        when(amazonS3Manager.generatePresignedGetUrl(eq("private/books/42/normalizations/run-1/meta.json"), eq(Duration.ofHours(1))))
                .thenReturn("https://signed/meta");
        when(amazonS3Manager.generatePresignedGetUrl(eq("private/books/42/normalizations/run-1/text/chapter_001.txt"), eq(Duration.ofHours(1))))
                .thenReturn("https://signed/ch1");
        when(amazonS3Manager.generatePresignedGetUrl(eq("private/books/42/normalizations/run-1/text/chapter_002.txt"), eq(Duration.ofHours(1))))
                .thenReturn("https://signed/ch2");

        AnalysisInputResponseDTO response = analysisInputExportService.getAnalysisInput(42L);

        assertThat(response.getBookId()).isEqualTo(42L);
        assertThat(response.getMetaUrl()).isEqualTo("https://signed/meta");
        assertThat(response.getChapters()).hasSize(2);
        assertThat(response.getChapters().get(0).getChapterIndex()).isEqualTo(1);
        assertThat(response.getChapters().get(0).getTxtUrl()).isEqualTo("https://signed/ch1");
        assertThat(response.getChapters().get(1).getChapterIndex()).isEqualTo(2);
        assertThat(response.getChapters().get(1).getTxtUrl()).isEqualTo("https://signed/ch2");
        assertThat(response.getExpiresAt()).isNotNull();

        verify(amazonS3Manager).generatePresignedGetUrl("private/books/42/normalizations/run-1/meta.json", Duration.ofHours(1));
        verify(amazonS3Manager).generatePresignedGetUrl("private/books/42/normalizations/run-1/text/chapter_001.txt", Duration.ofHours(1));
        verify(amazonS3Manager).generatePresignedGetUrl("private/books/42/normalizations/run-1/text/chapter_002.txt", Duration.ofHours(1));
    }

    @Test
    @DisplayName("정규화 미완료 도서는 analysis input 조회를 거부한다")
    void getAnalysisInputRejectsNonReadyBook() {
        Book book = Book.builder()
                .id(42L)
                .normalizationStatus(NormalizationStatus.QUEUED)
                .normalizedArtifactPath("books/42/normalizations/run-1")
                .build();

        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> analysisInputExportService.getAnalysisInput(42L))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorReason().getMessage())
                        .contains("정규화가 완료된 도서만"));
    }
}
