package com.kw.readwith.service.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.config.EpubNormalizationProperties;
import com.kw.readwith.domain.Book;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private NormalizationJobService normalizationJobService;

    @BeforeEach
    void setUp() {
        epubNormalizationProperties.setRuleVersion("rule-v1");
        epubNormalizationProperties.setLocatorVersion("locator-v2");
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
}
