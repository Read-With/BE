package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.dto.admin.RelationshipUploadDTO;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipDeltaImportJobService {

    private static final String SOURCE_VERSION = "relationship-delta-v1";
    private static final String TRIGGERED_BY = "RELATIONSHIP_DELTA_UPLOAD";
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^ch(\\d+)-e(\\d+)$");

    private final BookRepository bookRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobLogRepository processingJobLogRepository;
    private final AdminService adminService;
    private final BookAnalysisStatusService bookAnalysisStatusService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public ProcessingJobResponseDTO queueRelationshipDeltaImport(Long bookId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "No relationship delta files provided.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BOOK_NOT_FOUND));

        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        bookId,
                        ProcessingPipelineType.AI_ANALYSIS,
                        EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING)
                )
                .ifPresent(existing -> {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "An AI analysis job is already active for this book.");
                });

        Path workspace = prepareWorkspace(bookId, files);
        try {
            ProcessingJob job = ProcessingJob.builder()
                    .book(book)
                    .pipelineType(ProcessingPipelineType.AI_ANALYSIS)
                    .runId("relationship-delta-" + UUID.randomUUID())
                    .sourceVersion(SOURCE_VERSION)
                    .artifactPath(workspace.toString())
                    .status(ProcessingJobStatus.QUEUED)
                    .currentStep("queued")
                    .triggeredBy(TRIGGERED_BY)
                    .build();

            ProcessingJob savedJob = processingJobRepository.save(job);
            writeLog(savedJob, ProcessingJobLogLevel.INFO, "queued", "Relationship delta import job has been queued.", Map.of(
                    "fileCount", countRegularFiles(workspace),
                    "workspace", workspace.toString()
            ));
            return ProcessingJobResponseDTO.from(savedJob);
        } catch (RuntimeException e) {
            cleanupWorkspace(workspace);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponseDTO getRelationshipDeltaImportJob(Long jobId) {
        ProcessingJob job = findAnalysisJob(jobId);
        return ProcessingJobResponseDTO.from(job);
    }

    @Transactional(readOnly = true)
    public List<ProcessingJobLogResponseDTO> getRelationshipDeltaImportJobLogs(Long jobId) {
        ProcessingJob job = findAnalysisJob(jobId);
        return processingJobLogRepository.findAllByJobIdOrderBySeqAsc(jobId).stream()
                .map(logEntry -> ProcessingJobLogResponseDTO.builder()
                        .id(logEntry.getId())
                        .jobId(job.getId())
                        .bookTitle(job.getBook().getTitle())
                        .seq(logEntry.getSeq())
                        .level(logEntry.getLevel())
                        .step(logEntry.getStep())
                        .message(logEntry.getMessage())
                        .payloadJson(logEntry.getPayloadJson())
                        .createdAt(logEntry.getCreatedAt())
                        .build())
                .toList();
    }

    public void execute(Long jobId) {
        Path workspace = null;
        try {
            RelationshipDeltaExecutionContext context = transitionToProcessing(jobId);
            workspace = Path.of(context.workspace());
            int processedFileCount = processWorkspace(jobId, context.bookId(), workspace);
            completeSuccess(jobId, processedFileCount);
        } catch (Exception e) {
            log.error("Relationship delta import job failed. jobId={}", jobId, e);
            completeFailure(jobId, e);
        } finally {
            if (workspace != null) {
                cleanupWorkspace(workspace);
            }
        }
    }

    private Path prepareWorkspace(Long bookId, List<MultipartFile> files) {
        try {
            Path workspace = Files.createTempDirectory("readwith-relationship-delta-" + bookId + "-");
            int storedCount = 0;
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                Path target = workspace.resolve(safeFileName(file.getOriginalFilename(), i + 1));
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                storedCount++;
            }

            if (storedCount == 0) {
                cleanupWorkspace(workspace);
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "No non-empty relationship delta files provided.");
            }
            return workspace;
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to stage relationship delta files.");
        }
    }

    private int processWorkspace(Long jobId, Long bookId, Path workspace) {
        List<Path> files = listWorkspaceFiles(workspace);
        Set<String> seenEventIds = new HashSet<>();
        int processedFileCount = 0;

        for (Path file : files) {
            advanceStep(jobId, "processing_file", "Processing relationship delta file.", Map.of(
                    "fileName", file.getFileName().toString(),
                    "processedFileCount", processedFileCount
            ));

            RelationshipUploadDTO dto = readRelationshipDeltaFile(file);
            String eventKey = resolveEventKey(dto, file);
            if (!seenEventIds.add(eventKey)) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "Duplicate relationship delta eventId in job: " + eventKey);
            }

            int savedCount = adminService.replaceRelationshipDeltaPayload(bookId, dto, false);
            processedFileCount++;
            writeLogInNewTransaction(jobId, ProcessingJobLogLevel.INFO, "file_processed", "Relationship delta file has been imported.", Map.of(
                    "fileName", file.getFileName().toString(),
                    "eventId", eventKey,
                    "savedCount", savedCount,
                    "processedFileCount", processedFileCount
            ));
        }

        return processedFileCount;
    }

    private RelationshipUploadDTO readRelationshipDeltaFile(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return objectMapper.readValue(inputStream, RelationshipUploadDTO.class);
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to parse relationship delta JSON: " + file.getFileName());
        }
    }

    private RelationshipDeltaExecutionContext transitionToProcessing(Long jobId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = findAnalysisJob(jobId);
            if (job.getStatus() != ProcessingJobStatus.QUEUED) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "Relationship delta import job is not queued.");
            }
            if (job.getArtifactPath() == null || job.getArtifactPath().isBlank()) {
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Relationship delta import workspace is missing.");
            }

            job.markProcessing("processing_files");
            job.getBook().resetAnalysisStatus();
            writeLog(job, ProcessingJobLogLevel.INFO, "processing_files", "Relationship delta import has started.", Map.of(
                    "runId", job.getRunId(),
                    "workspace", job.getArtifactPath()
            ));

            return new RelationshipDeltaExecutionContext(job.getBook().getId(), job.getArtifactPath());
        });
    }

    private void advanceStep(Long jobId, String step, String message, Map<String, Object> payload) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findAnalysisJob(jobId);
            job.markProcessing(step);
            writeLog(job, ProcessingJobLogLevel.INFO, step, message, payload);
        });
    }

    private void completeSuccess(Long jobId, int processedFileCount) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findAnalysisJob(jobId);
            bookAnalysisStatusService.refreshStatus(job.getBook().getId());
            job.markReady(job.getArtifactPath(), "completed");
            writeLog(job, ProcessingJobLogLevel.INFO, "completed", "Relationship delta import job completed.", Map.of(
                    "runId", job.getRunId(),
                    "processedFileCount", processedFileCount
            ));
        });
    }

    private void completeFailure(Long jobId, Exception e) {
        try {
            writableTransaction().executeWithoutResult(status -> {
                ProcessingJob job = processingJobRepository.findById(jobId)
                        .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Relationship delta import job not found."));
                String currentStep = job.getCurrentStep() == null ? "failed" : job.getCurrentStep();
                String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();

                job.markFailed(currentStep, resolveFailureCode(e), failureMessage);
                writeLog(job, ProcessingJobLogLevel.ERROR, currentStep, "Relationship delta import job failed.", Map.of(
                        "runId", job.getRunId(),
                        "failureCode", resolveFailureCode(e),
                        "error", failureMessage
                ));
            });
        } catch (Exception nested) {
            log.error("Relationship delta import job failure state persistence failed. jobId={}", jobId, nested);
        }
    }

    private ProcessingJob findAnalysisJob(Long jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Relationship delta import job not found."));
        if (job.getPipelineType() != ProcessingPipelineType.AI_ANALYSIS) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "Job is not an AI analysis job.");
        }
        return job;
    }

    private void writeLogInNewTransaction(Long jobId, ProcessingJobLogLevel level, String step, String message, Map<String, Object> payload) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findAnalysisJob(jobId);
            writeLog(job, level, step, message, payload);
        });
    }

    private void writeLog(
            ProcessingJob job,
            ProcessingJobLogLevel level,
            String step,
            String message,
            Map<String, Object> payload
    ) {
        int sequence = (int) processingJobLogRepository.countByJobId(job.getId()) + 1;
        processingJobLogRepository.save(ProcessingJobLog.builder()
                .job(job)
                .seq(sequence)
                .level(level)
                .step(step)
                .message(message)
                .payloadJson(serializePayload(payload))
                .build());
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            return "{\"serializationError\":true}";
        }
    }

    private List<Path> listWorkspaceFiles(Path workspace) {
        try (Stream<Path> stream = Files.list(workspace)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to list relationship delta workspace files.");
        }
    }

    private int countRegularFiles(Path workspace) {
        try (Stream<Path> stream = Files.list(workspace)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to count relationship delta workspace files.");
        }
    }

    private String safeFileName(String originalFilename, int index) {
        String name = originalFilename == null || originalFilename.isBlank()
                ? "relationship-delta-" + index + ".json"
                : originalFilename.replace('\\', '/');
        int slashIndex = name.lastIndexOf('/');
        if (slashIndex >= 0) {
            name = name.substring(slashIndex + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            name = "relationship-delta-" + index + ".json";
        }
        return String.format("%04d-%s", index, name);
    }

    private String resolveEventKey(RelationshipUploadDTO dto, Path file) {
        if (dto == null || dto.getChapterIndex() == null || dto.getEventId() == null || dto.getEventId().isBlank()) {
            return file.getFileName().toString();
        }

        String eventId = dto.getEventId().trim();
        Matcher matcher = EVENT_ID_PATTERN.matcher(eventId);
        if (matcher.matches()) {
            return "ch" + Integer.parseInt(matcher.group(1)) + "-e" + Integer.parseInt(matcher.group(2));
        }
        if (eventId.matches("\\d+")) {
            return "ch" + dto.getChapterIndex() + "-e" + Integer.parseInt(eventId);
        }
        return "ch" + dto.getChapterIndex() + "-" + eventId;
    }

    private void cleanupWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(workspace)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to clean relationship delta workspace path={}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk relationship delta workspace. path={}", workspace, e);
        }
    }

    private String resolveFailureCode(Exception exception) {
        if (exception instanceof GeneralException generalException) {
            return generalException.getErrorReason().getCode();
        }
        return "RELATIONSHIP_DELTA_IMPORT_FAILED";
    }

    private TransactionTemplate writableTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private record RelationshipDeltaExecutionContext(Long bookId, String workspace) {
    }
}
