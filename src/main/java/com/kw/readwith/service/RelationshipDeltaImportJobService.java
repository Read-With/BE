package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.ArtifactStorageProperties;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final AmazonS3Manager amazonS3Manager;
    private final ArtifactStorageProperties artifactStorageProperties;
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

        String runId = "relationship-delta-" + UUID.randomUUID();
        String artifactRoot = buildArtifactRoot(bookId, runId);
        S3StagingResult stagingResult = stageFiles(artifactRoot, files);

        try {
            ProcessingJob job = ProcessingJob.builder()
                    .book(book)
                    .pipelineType(ProcessingPipelineType.AI_ANALYSIS)
                    .runId(runId)
                    .sourceVersion(SOURCE_VERSION)
                    .artifactPath(artifactRoot)
                    .status(ProcessingJobStatus.QUEUED)
                    .currentStep("queued")
                    .triggeredBy(TRIGGERED_BY)
                    .build();

            ProcessingJob savedJob = processingJobRepository.save(job);
            writeLog(savedJob, ProcessingJobLogLevel.INFO, "queued", "Relationship delta import job has been queued.", Map.of(
                    "fileCount", stagingResult.fileCount(),
                    "artifactRoot", artifactRoot,
                    "inputPrefix", privateKey(inputPrefix(artifactRoot))
            ));
            return ProcessingJobResponseDTO.from(savedJob);
        } catch (RuntimeException e) {
            cleanupStagedFiles(artifactRoot);
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
        try {
            RelationshipDeltaExecutionContext context = transitionToProcessing(jobId);
            int processedFileCount = processArtifactRoot(jobId, context.bookId(), context.artifactRoot());
            completeSuccess(jobId, processedFileCount);
        } catch (Exception e) {
            log.error("Relationship delta import job failed. jobId={}", jobId, e);
            completeFailure(jobId, e);
        }
    }

    private S3StagingResult stageFiles(String artifactRoot, List<MultipartFile> files) {
        int storedCount = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }

            String key = privateKey(inputPrefix(artifactRoot) + "/" + safeFileName(file.getOriginalFilename(), i + 1));
            try {
                amazonS3Manager.uploadFile(key, file);
            } catch (RuntimeException e) {
                cleanupStagedFiles(artifactRoot);
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to stage relationship delta files to S3.");
            }
            storedCount++;
        }

        if (storedCount == 0) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "No non-empty relationship delta files provided.");
        }
        return new S3StagingResult(artifactRoot, storedCount);
    }

    private int processArtifactRoot(Long jobId, Long bookId, String artifactRoot) {
        List<String> keys = listStagedInputKeys(artifactRoot);
        if (keys.isEmpty()) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Relationship delta staged input files are missing.");
        }

        Set<String> seenEventIds = new HashSet<>();
        int processedFileCount = 0;

        for (String key : keys) {
            String fileName = fileNameFromKey(key);
            advanceStep(jobId, "processing_file", "Processing relationship delta file.", Map.of(
                    "fileName", fileName,
                    "processedFileCount", processedFileCount
            ));

            RelationshipUploadDTO dto = readRelationshipDeltaFile(key, fileName);
            String eventKey = resolveEventKey(dto, fileName);
            if (!seenEventIds.add(eventKey)) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "Duplicate relationship delta eventId in job: " + eventKey);
            }

            int savedCount = adminService.replaceRelationshipDeltaPayload(bookId, dto, false);
            processedFileCount++;
            writeLogInNewTransaction(jobId, ProcessingJobLogLevel.INFO, "file_processed", "Relationship delta file has been imported.", Map.of(
                    "fileName", fileName,
                    "eventId", eventKey,
                    "savedCount", savedCount,
                    "processedFileCount", processedFileCount
            ));
        }

        return processedFileCount;
    }

    private RelationshipUploadDTO readRelationshipDeltaFile(String key, String fileName) {
        try {
            return amazonS3Manager.readObject(key, inputStream -> objectMapper.readValue(inputStream, RelationshipUploadDTO.class));
        } catch (IOException e) {
            throw new GeneralException(ErrorStatus.JSON_PARSING_ERROR, "Failed to parse relationship delta JSON: " + fileName);
        } catch (RuntimeException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to read staged relationship delta file: " + fileName);
        }
    }

    private RelationshipDeltaExecutionContext transitionToProcessing(Long jobId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = findAnalysisJob(jobId);
            if (job.getStatus() != ProcessingJobStatus.QUEUED) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST, "Relationship delta import job is not queued.");
            }
            if (job.getArtifactPath() == null || job.getArtifactPath().isBlank()) {
                throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Relationship delta import artifact root is missing.");
            }

            job.markProcessing("processing_files");
            job.getBook().resetAnalysisStatus();
            writeLog(job, ProcessingJobLogLevel.INFO, "processing_files", "Relationship delta import has started.", Map.of(
                    "runId", job.getRunId(),
                    "artifactRoot", job.getArtifactPath(),
                    "inputPrefix", privateKey(inputPrefix(job.getArtifactPath()))
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
                    "artifactRoot", job.getArtifactPath(),
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
                        "artifactRoot", job.getArtifactPath(),
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

    private List<String> listStagedInputKeys(String artifactRoot) {
        try {
            return amazonS3Manager.listKeys(privateKey(inputPrefix(artifactRoot))).stream()
                    .filter(key -> !key.endsWith("/"))
                    .sorted()
                    .toList();
        } catch (RuntimeException e) {
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "Failed to list staged relationship delta files.");
        }
    }

    private void cleanupStagedFiles(String artifactRoot) {
        try {
            amazonS3Manager.deleteKeys(listStagedInputKeys(artifactRoot));
        } catch (RuntimeException e) {
            log.warn("Failed to clean staged relationship delta files. artifactRoot={}", artifactRoot, e);
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

    private String resolveEventKey(RelationshipUploadDTO dto, String fileName) {
        if (dto == null || dto.getChapterIndex() == null || dto.getEventId() == null || dto.getEventId().isBlank()) {
            return fileName;
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

    private String fileNameFromKey(String key) {
        int slashIndex = key.lastIndexOf('/');
        return slashIndex >= 0 ? key.substring(slashIndex + 1) : key;
    }

    private String buildArtifactRoot(Long bookId, String runId) {
        return "books/" + bookId + "/analysis/relationship-delta-jobs/" + runId;
    }

    private String inputPrefix(String artifactRoot) {
        return artifactRoot + "/input";
    }

    private String privateKey(String relativePath) {
        return trimSlashes(artifactStorageProperties.getPrivatePrefix()) + "/" + trimLeadingSlash(relativePath);
    }

    private String trimSlashes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("^/+|/+$", "");
    }

    private String trimLeadingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("^/+", "");
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

    private record S3StagingResult(String artifactRoot, int fileCount) {
    }

    private record RelationshipDeltaExecutionContext(Long bookId, String artifactRoot) {
    }
}
