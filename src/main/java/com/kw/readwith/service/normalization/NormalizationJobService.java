package com.kw.readwith.service.normalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.EpubNormalizationProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.enums.NormalizationFailureCode;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.ChapterRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NormalizationJobService {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobLogRepository processingJobLogRepository;
    private final NormalizationPipelineService normalizationPipelineService;
    private final NormalizedArtifactStorageService normalizedArtifactStorageService;
    private final LocatorResolutionService locatorResolutionService;
    private final EpubNormalizationProperties epubNormalizationProperties;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJob createQueuedJob(Book book, String sourceVersion, String triggeredBy) {
        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        book.getId(),
                        ProcessingPipelineType.NORMALIZATION,
                        EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING)
                )
                .ifPresent(existing -> {
                    throw new GeneralException(ErrorStatus._BAD_REQUEST, "A normalization job is already active for this book.");
                });

        ProcessingJob job = ProcessingJob.builder()
                .book(book)
                .pipelineType(ProcessingPipelineType.NORMALIZATION)
                .runId(normalizedArtifactStorageService.newNormalizationRunId())
                .sourceVersion(sourceVersion)
                .ruleVersion(epubNormalizationProperties.getRuleVersion())
                .locatorVersion(epubNormalizationProperties.getLocatorVersion())
                .status(ProcessingJobStatus.QUEUED)
                .currentStep("queued")
                .triggeredBy(triggeredBy)
                .build();

        ProcessingJob savedJob = processingJobRepository.save(job);
        writeLog(savedJob, ProcessingJobLogLevel.INFO, "queued", "Normalization job has been queued.", Map.of(
                "runId", savedJob.getRunId(),
                "ruleVersion", epubNormalizationProperties.getRuleVersion(),
                "locatorVersion", epubNormalizationProperties.getLocatorVersion()
        ));
        return savedJob;
    }

    public void execute(Long jobId) {
        Path tempFile = null;
        try {
            NormalizationExecutionContext context = transitionToProcessing(jobId);

            try {
                tempFile = Files.createTempFile("readwith-normalization-", ".epub");
                Files.write(tempFile, normalizedArtifactStorageService.loadPrivateObject(context.sourcePath()));
            } catch (NormalizationProcessingException e) {
                throw e;
            } catch (IOException e) {
                throw new NormalizationProcessingException(
                        NormalizationFailureCode.TEMP_FILE_PREPARE_FAILED,
                        "prepare_temp_file",
                        "Failed to prepare local normalization workspace.",
                        e
                );
            }

            advanceStep(jobId, "normalizing_epub", "EPUB normalization has started.");
            NormalizationPipelineResult pipelineResult = normalizationPipelineService.normalize(
                    tempFile,
                    context.bookId(),
                    context.runId(),
                    context.ruleVersion(),
                    context.locatorVersion()
            );

            advanceStep(jobId, "storing_artifacts", "Normalization artifacts are being stored.");
            String artifactRoot = normalizedArtifactStorageService.storeNormalizationArtifacts(
                    context.bookId(),
                    context.runId(),
                    pipelineResult
            );

            completeSuccess(jobId, artifactRoot, pipelineResult.getChapters());
        } catch (Exception e) {
            log.error("Normalization job failed. jobId={}", jobId, e);
            completeFailure(jobId, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.warn("Temporary normalization file cleanup failed. path={}", tempFile);
                }
            }
        }
    }

    private NormalizationExecutionContext transitionToProcessing(Long jobId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = processingJobRepository.findById(jobId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Normalization job not found."));
            Book book = job.getBook();

            job.markProcessing("downloading_source");
            book.markNormalizationProcessing();
            writeLog(job, ProcessingJobLogLevel.INFO, "downloading_source", "Downloading source EPUB.", Map.of(
                    "runId", job.getRunId(),
                    "sourceVersion", job.getSourceVersion()
            ));

            return new NormalizationExecutionContext(
                    book.getId(),
                    book.getEpubPath(),
                    job.getRunId(),
                    job.getRuleVersion(),
                    job.getLocatorVersion()
            );
        });
    }

    private void completeSuccess(Long jobId, String artifactRoot, List<NormalizedChapterArtifact> chapterArtifacts) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = processingJobRepository.findById(jobId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Normalization job not found."));
            Book book = job.getBook();

            writeLog(job, ProcessingJobLogLevel.INFO, "writing_projection", "Writing chapter projection to database.", null);
            projectChapters(book, chapterArtifacts);
            locatorResolutionService.evictAll();

            book.resetAnalysisStatus();
            book.markNormalizationReady(
                    job.getRuleVersion(),
                    job.getLocatorVersion(),
                    job.getRunId(),
                    artifactRoot
            );
            job.markReady(artifactRoot, "completed");
            writeLog(job, ProcessingJobLogLevel.INFO, "completed", "Normalization job completed.", Map.of(
                    "runId", job.getRunId(),
                    "artifactRoot", artifactRoot,
                    "chapterCount", chapterArtifacts.size(),
                    "ruleVersion", job.getRuleVersion(),
                    "locatorVersion", job.getLocatorVersion()
            ));
        });
    }

    private void advanceStep(Long jobId, String step, String message) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = processingJobRepository.findById(jobId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Normalization job not found."));
            job.markProcessing(step);
            writeLog(job, ProcessingJobLogLevel.INFO, step, message, null);
        });
    }

    private void completeFailure(Long jobId, Exception e) {
        try {
            writableTransaction().executeWithoutResult(status -> {
                ProcessingJob job = processingJobRepository.findById(jobId)
                        .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST, "Normalization job not found."));
                Book book = job.getBook();

                NormalizationFailureCode failureCode = resolveFailureCode(e);
                String currentStep = resolveFailureStep(job, e);
                String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();

                book.markNormalizationFailed();
                job.markFailed(currentStep, failureCode.name(), failureMessage);
                writeLog(job, ProcessingJobLogLevel.ERROR, currentStep, "Normalization job failed.", Map.of(
                        "runId", job.getRunId(),
                        "failureCode", failureCode.name(),
                        "error", failureMessage
                ));
            });
        } catch (Exception nested) {
            log.error("Normalization job failure state persistence failed. jobId={}", jobId, nested);
        }
    }

    private void projectChapters(Book book, List<NormalizedChapterArtifact> chapterArtifacts) {
        Map<Integer, Chapter> existingChapters = new HashMap<>();
        for (Chapter chapter : chapterRepository.findByBookId(book.getId())) {
            existingChapters.put(chapter.getIdx(), chapter);
        }

        List<Chapter> chaptersToSave = chapterArtifacts.stream()
                .map(artifact -> {
                    Chapter chapter = existingChapters.getOrDefault(
                            artifact.getChapterIndex(),
                            Chapter.builder()
                                    .book(book)
                                    .idx(artifact.getChapterIndex())
                                    .pageStart(0)
                                    .pageEnd(0)
                                    .startPos(artifact.getStartPos())
                                    .endPos(artifact.getEndPos())
                                    .povSummariesCached(false)
                                    .build()
                    );
                    chapter.applyNormalizationProjection(
                            artifact.getTitle(),
                            artifact.getSpineHref(),
                            artifact.getParagraphStarts().size(),
                            locatorResolutionService.writeIntegerList(artifact.getParagraphStarts()),
                            locatorResolutionService.writeIntegerList(artifact.getParagraphLengths()),
                            artifact.getTotalCodePoints(),
                            artifact.getStartPos(),
                            artifact.getEndPos(),
                            artifact.getRawText()
                    );
                    return chapter;
                })
                .toList();

        try {
            chapterRepository.saveAll(chaptersToSave);
        } catch (Exception e) {
            throw new NormalizationProcessingException(
                    NormalizationFailureCode.PROJECTION_WRITE_FAILED,
                    "writing_projection",
                    "Failed to persist chapter projection.",
                    e
            );
        }
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
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":true}";
        }
    }

    private TransactionTemplate writableTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private NormalizationFailureCode resolveFailureCode(Exception exception) {
        if (exception instanceof NormalizationProcessingException normalizationProcessingException) {
            return normalizationProcessingException.getFailureCode();
        }
        return NormalizationFailureCode.UNEXPECTED_FAILURE;
    }

    private String resolveFailureStep(ProcessingJob job, Exception exception) {
        if (exception instanceof NormalizationProcessingException normalizationProcessingException
                && normalizationProcessingException.getStep() != null
                && !normalizationProcessingException.getStep().isBlank()) {
            return normalizationProcessingException.getStep();
        }
        return job.getCurrentStep() == null ? "failed" : job.getCurrentStep();
    }

    private record NormalizationExecutionContext(
            Long bookId,
            String sourcePath,
            String runId,
            String ruleVersion,
            String locatorVersion
    ) {
    }
}
