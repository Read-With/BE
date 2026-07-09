package com.kw.readwith.service;

import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipDeltaImportJobServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private ProcessingJobLogRepository processingJobLogRepository;

    @Mock
    private AdminService adminService;

    @Mock
    private BookAnalysisStatusService bookAnalysisStatusService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void queueRelationshipDeltaImportStagesFilesAndCreatesAnalysisJob() throws IOException {
        Book book = Book.builder()
                .title("The Great Gatsby")
                .author("F. Scott Fitzgerald")
                .language("en")
                .build();
        ReflectionTestUtils.setField(book, "id", 17L);

        when(bookRepository.findById(17L)).thenReturn(Optional.of(book));
        when(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                eq(17L),
                eq(ProcessingPipelineType.AI_ANALYSIS),
                any()
        )).thenReturn(Optional.empty());
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 137L);
            return job;
        });
        when(processingJobLogRepository.countByJobId(137L)).thenReturn(0L);

        RelationshipDeltaImportJobService service = newService();
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "chapter1_event1.json",
                "application/json",
                "{\"contractVersion\":\"relationship-delta-v1\"}".getBytes()
        );

        var response = service.queueRelationshipDeltaImport(17L, java.util.List.of(file));

        ArgumentCaptor<ProcessingJob> jobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);
        ArgumentCaptor<ProcessingJobLog> logCaptor = ArgumentCaptor.forClass(ProcessingJobLog.class);
        verify(processingJobRepository).save(jobCaptor.capture());
        verify(processingJobLogRepository).save(logCaptor.capture());

        ProcessingJob savedJob = jobCaptor.getValue();
        Path workspace = Path.of(savedJob.getArtifactPath());
        try {
            assertThat(response.getId()).isEqualTo(137L);
            assertThat(savedJob.getPipelineType()).isEqualTo(ProcessingPipelineType.AI_ANALYSIS);
            assertThat(savedJob.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
            assertThat(savedJob.getCurrentStep()).isEqualTo("queued");
            assertThat(savedJob.getSourceVersion()).isEqualTo("relationship-delta-v1");
            assertThat(savedJob.getRunId()).startsWith("relationship-delta-");
            try (Stream<Path> stagedFiles = Files.list(workspace)) {
                assertThat(stagedFiles).hasSize(1);
            }
            assertThat(logCaptor.getValue().getLevel()).isEqualTo(ProcessingJobLogLevel.INFO);
            assertThat(logCaptor.getValue().getStep()).isEqualTo("queued");
        } finally {
            cleanup(workspace);
        }
    }

    @Test
    void queueRelationshipDeltaImportRejectsWhenAnalysisJobIsActive() {
        Book book = Book.builder().title("Book").author("Author").language("en").build();
        ReflectionTestUtils.setField(book, "id", 17L);
        ProcessingJob activeJob = ProcessingJob.builder()
                .id(99L)
                .book(book)
                .pipelineType(ProcessingPipelineType.AI_ANALYSIS)
                .status(ProcessingJobStatus.PROCESSING)
                .build();

        when(bookRepository.findById(17L)).thenReturn(Optional.of(book));
        when(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                eq(17L),
                eq(ProcessingPipelineType.AI_ANALYSIS),
                any()
        )).thenReturn(Optional.of(activeJob));

        RelationshipDeltaImportJobService service = newService();
        MockMultipartFile file = new MockMultipartFile("files", "delta.json", "application/json", "{}".getBytes());

        assertThatThrownBy(() -> service.queueRelationshipDeltaImport(17L, java.util.List.of(file)))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("already active");
        verify(processingJobRepository, never()).save(any());
    }

    private RelationshipDeltaImportJobService newService() {
        return new RelationshipDeltaImportJobService(
                bookRepository,
                processingJobRepository,
                processingJobLogRepository,
                adminService,
                bookAnalysisStatusService,
                new ObjectMapper(),
                transactionManager
        );
    }

    private void cleanup(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
