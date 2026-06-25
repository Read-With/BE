package com.kw.readwith.service.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.EpubNormalizationProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NormalizationJobServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private ProcessingJobLogRepository processingJobLogRepository;

    @Mock
    private NormalizationPipelineService normalizationPipelineService;

    @Mock
    private NormalizedArtifactStorageService normalizedArtifactStorageService;

    @Mock
    private LocatorResolutionService locatorResolutionService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final EpubNormalizationProperties epubNormalizationProperties = new EpubNormalizationProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private NormalizationJobService normalizationJobService;

    @BeforeEach
    void setUp() {
        epubNormalizationProperties.setRuleVersion("rule-v1");
        epubNormalizationProperties.setLocatorVersion("locator-v2");
        normalizationJobService = new NormalizationJobService(
                bookRepository,
                chapterRepository,
                processingJobRepository,
                processingJobLogRepository,
                normalizationPipelineService,
                normalizedArtifactStorageService,
                locatorResolutionService,
                epubNormalizationProperties,
                objectMapper,
                transactionManager
        );
    }

    @Nested
    @DisplayName("retryFailedJob 메서드")
    class RetryFailedJobTest {

        private Book book;
        private ProcessingJob failedJob;

        @BeforeEach
        void setup() {
            book = Book.builder().title("Test Book").build();
            ReflectionTestUtils.setField(book, "id", 1L);

            failedJob = ProcessingJob.builder()
                    .id(100L)
                    .book(book)
                    .pipelineType(ProcessingPipelineType.NORMALIZATION)
                    .status(ProcessingJobStatus.FAILED)
                    .sourceVersion("source-v1")
                    .triggeredBy("UPLOAD")
                    .build();
        }

        @Test
        @DisplayName("실패한 정규화 작업을 재시도하면, 새로운 작업이 QUEUED 상태로 생성된다")
        void retryFailedNormalizationJob_shouldCreateNewQueuedJob() {
            // given
            when(processingJobRepository.findById(100L)).thenReturn(Optional.of(failedJob));
            when(normalizedArtifactStorageService.newNormalizationRunId()).thenReturn("new-run-id");
            when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
                ProcessingJob newJob = invocation.getArgument(0);
                ReflectionTestUtils.setField(newJob, "id", 101L);
                return newJob;
            });

            // when
            var response = normalizationJobService.retryFailedJob(100L);

            // then
            assertThat(response.getId()).isEqualTo(101L);
            assertThat(response.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
            assertThat(response.getSourceVersion()).isEqualTo("source-v1");
            assertThat(response.getTriggeredBy()).isEqualTo("UPLOAD");
            assertThat(response.getRunId()).isEqualTo("new-run-id");

            ArgumentCaptor<ProcessingJob> jobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);
            verify(processingJobRepository).save(jobCaptor.capture());
            ProcessingJob savedJob = jobCaptor.getValue();

            assertThat(savedJob.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
            assertThat(savedJob.getTriggeredBy()).isEqualTo("UPLOAD");
        }

        @Test
        @DisplayName("작업 상태가 FAILED가 아니면 예외를 발생시킨다")
        void retryNonFailedJob_shouldThrowException() {
            // given
            failedJob.markReady("some-path", "completed"); // 상태를 FAILED가 아닌 것으로 변경
            when(processingJobRepository.findById(100L)).thenReturn(Optional.of(failedJob));

            // when & then
            assertThatThrownBy(() -> normalizationJobService.retryFailedJob(100L))
                    .isInstanceOf(GeneralException.class)
                    .hasMessageContaining("Job did not fail. Cannot retry.");
        }

        @Test
        @DisplayName("존재하지 않는 작업 ID이면 예외를 발생시킨다")
        void retryNonExistentJob_shouldThrowException() {
            // given
            when(processingJobRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> normalizationJobService.retryFailedJob(999L))
                    .isInstanceOf(GeneralException.class)

                    .hasMessageContaining("Failed job not found.");
        }

        @Test
        @DisplayName("작업 타입이 NORMALIZATION이 아니면 예외를 발생시킨다")
        void retryNonNormalizationJob_shouldThrowException() {
            // given
            failedJob = ProcessingJob.builder()
                    .id(100L)
                    .book(book)
                    .pipelineType(ProcessingPipelineType.AI_ANALYSIS) // 다른 타입으로 설정
                    .status(ProcessingJobStatus.FAILED)
                    .sourceVersion("source-v1")
                    .triggeredBy("UPLOAD")
                    .build();
            when(processingJobRepository.findById(100L)).thenReturn(Optional.of(failedJob));

            // when & then
            assertThatThrownBy(() -> normalizationJobService.retryFailedJob(100L))
                    .isInstanceOf(GeneralException.class)
                    .hasMessageContaining("Job is not a normalization job.");
        }
    }


    @Test
    @DisplayName("createQueuedJob uses the caller transaction for upload flow")
    void createQueuedJobUsesCallerTransaction() {
        Book book = Book.builder()
                .title("Title")
                .author("Author")
                .language("en")
                .build();
        ReflectionTestUtils.setField(book, "id", 10L);

        when(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                10L,
                ProcessingPipelineType.NORMALIZATION,
                java.util.EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING)
        )).thenReturn(Optional.empty());
        when(normalizedArtifactStorageService.newNormalizationRunId()).thenReturn("run-1");
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob savedJob = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedJob, "id", 55L);
            return savedJob;
        });
        when(processingJobLogRepository.countByJobId(55L)).thenReturn(0L);

        ProcessingJob job = normalizationJobService.createQueuedJob(book, "source-v1", "UPLOAD");

        ArgumentCaptor<ProcessingJob> jobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);
        ArgumentCaptor<ProcessingJobLog> logCaptor = ArgumentCaptor.forClass(ProcessingJobLog.class);
        verify(processingJobRepository).save(jobCaptor.capture());
        verify(processingJobLogRepository).save(logCaptor.capture());
        verifyNoInteractions(transactionManager);

        ProcessingJob savedJob = jobCaptor.getValue();
        ProcessingJobLog savedLog = logCaptor.getValue();

        assertThat(job.getId()).isEqualTo(55L);
        assertThat(savedJob.getBook()).isSameAs(book);
        assertThat(savedJob.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
        assertThat(savedJob.getCurrentStep()).isEqualTo("queued");
        assertThat(savedJob.getRunId()).isEqualTo("run-1");
        assertThat(savedJob.getRuleVersion()).isEqualTo("rule-v1");
        assertThat(savedJob.getLocatorVersion()).isEqualTo("locator-v2");
        assertThat(savedLog.getLevel()).isEqualTo(ProcessingJobLogLevel.INFO);
        assertThat(savedLog.getStep()).isEqualTo("queued");
    }

    @Test
    @DisplayName("projectChapters stores only a preview in chapter rawText")
    void projectChaptersStoresRawTextPreview() {
        Book book = Book.builder()
                .title("Title")
                .author("Author")
                .language("en")
                .build();
        ReflectionTestUtils.setField(book, "id", 10L);

        String longRawText = "a".repeat(2500);
        NormalizedChapterArtifact artifact = NormalizedChapterArtifact.builder()
                .chapterIndex(1)
                .title("Chapter 1")
                .spineHref("chapter-1.xhtml")
                .paragraphStarts(List.of(0))
                .paragraphLengths(List.of(longRawText.length()))
                .totalCodePoints(longRawText.length())
                .startPos(0)
                .endPos(longRawText.length() - 1)
                .rawText(longRawText)
                .normalizedXhtml("<html/>")
                .build();

        when(chapterRepository.findByBookId(10L)).thenReturn(List.of());
        when(locatorResolutionService.writeIntegerList(List.of(0))).thenReturn("[0]");
        when(locatorResolutionService.writeIntegerList(List.of(longRawText.length()))).thenReturn("[" + longRawText.length() + "]");

        ReflectionTestUtils.invokeMethod(normalizationJobService, "projectChapters", book, List.of(artifact));

        ArgumentCaptor<List<Chapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepository).saveAll(chapterCaptor.capture());
        List<Chapter> savedChapters = chapterCaptor.getValue();

        assertThat(savedChapters).hasSize(1);
        assertThat(savedChapters.get(0).getRawText()).hasSize(2000);
        assertThat(savedChapters.get(0).getRawText()).isEqualTo("a".repeat(2000));
    }

    @Test
    @DisplayName("getLatestNormalizationJob returns the latest job")
    void getLatestNormalizationJobReturnsLatestJob() {
        Long bookId = 10L;
        Book book = Book.builder().title("Book Title").build();
        ReflectionTestUtils.setField(book, "id", bookId);

        ProcessingJob job = ProcessingJob.builder()
                .id(100L)
                .book(book)
                .pipelineType(ProcessingPipelineType.NORMALIZATION)
                .runId("run-123")
                .sourceVersion("src-v1")
                .artifactPath("path/to/artifact")
                .status(ProcessingJobStatus.READY)
                .currentStep("completed")
                .triggeredBy("UPLOAD")
                .ruleVersion("v2")
                .locatorVersion("v2")
                .build();
        ReflectionTestUtils.setField(job, "createdAt", java.time.LocalDateTime.now());

        when(processingJobRepository.findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
                bookId, ProcessingPipelineType.NORMALIZATION
        )).thenReturn(Optional.of(job));

        var response = normalizationJobService.getLatestNormalizationJob(bookId);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getBookId()).isEqualTo(bookId);
        assertThat(response.getBookTitle()).isEqualTo("Book Title");
        assertThat(response.getPipelineType()).isEqualTo(ProcessingPipelineType.NORMALIZATION);
        assertThat(response.getSourceVersion()).isEqualTo("src-v2");
        assertThat(response.getArtifactPath()).isEqualTo("path/to/artifact");
        assertThat(response.getStatus()).isEqualTo(ProcessingJobStatus.READY);
        assertThat(response.getCurrentStep()).isEqualTo("completed");
    }

    @Test
    @DisplayName("getRecentNormalizationJobs returns list of jobs")
    void getRecentNormalizationJobsReturnsList() {
        Book book = Book.builder().title("Book Title").build();
        ReflectionTestUtils.setField(book, "id", 10L);

        ProcessingJob job = ProcessingJob.builder()
                .id(100L)
                .book(book)
                .pipelineType(ProcessingPipelineType.NORMALIZATION)
                .runId("run-123")
                .sourceVersion("src-v2")
                .artifactPath("path/v2")
                .status(ProcessingJobStatus.READY)
                .currentStep("completed")
                .build();
        ReflectionTestUtils.setField(job, "createdAt", java.time.LocalDateTime.now());

        when(processingJobRepository.findAllByPipelineTypeOrderByCreatedAtDesc(
                any(), any()
        )).thenReturn(List.of(job));

        var response = normalizationJobService.getRecentNormalizationJobs();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(100L);
        assertThat(response.get(0).getBookTitle()).isEqualTo("Book Title");
        assertThat(response.get(0).getPipelineType()).isEqualTo(ProcessingPipelineType.NORMALIZATION);
        assertThat(response.get(0).getSourceVersion()).isEqualTo("src-v2");
        assertThat(response.get(0).getArtifactPath()).isEqualTo("path/v2");
    }

    @Test
    @DisplayName("getRecentJobLogs returns list of recent logs system-wide")
    void getRecentJobLogsReturnsList() {
        Book book = Book.builder().title("Book Title").build();
        ProcessingJob job = ProcessingJob.builder().id(100L).book(book).build();
        
        ProcessingJobLog logEntry = ProcessingJobLog.builder()
                .id(500L)
                .job(job)
                .seq(1)
                .level(ProcessingJobLogLevel.INFO)
                .step("test_step")
                .message("test message")
                .build();
        ReflectionTestUtils.setField(logEntry, "createdAt", java.time.LocalDateTime.now());

        when(processingJobLogRepository.findAllByOrderByIdDesc(any())).thenReturn(List.of(logEntry));

        var response = normalizationJobService.getRecentJobLogs();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(500L);
        assertThat(response.get(0).getJobId()).isEqualTo(100L);
        assertThat(response.get(0).getBookTitle()).isEqualTo("Book Title");
        assertThat(response.get(0).getMessage()).isEqualTo("test message");
    }
}
