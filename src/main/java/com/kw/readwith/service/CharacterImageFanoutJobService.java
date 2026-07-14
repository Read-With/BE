package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.BookImageReferenceStatus;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import com.kw.readwith.service.image.OpenAiImageBatchClient;
import com.kw.readwith.service.image.OpenAiImageBatchClient.OpenAiBatchImageResult;
import com.kw.readwith.service.image.OpenAiImageBatchClient.OpenAiBatchImageEditRequest;
import com.kw.readwith.service.image.OpenAiImageBatchClient.OpenAiBatchStatus;
import com.kw.readwith.service.image.OpenAiImageBatchClient.OpenAiBatchSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterImageFanoutJobService {

    private static final String SOURCE_VERSION = "character-image-batch-v1";
    private static final String TRIGGERED_BY = "ADMIN_REFERENCE_SELECTION";
    private static final String CUSTOM_ID_PREFIX = "character-image-asset-";
    private static final EnumSet<ProcessingJobStatus> ACTIVE_JOB_STATUSES =
            EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING);

    private final CharacterRepository characterRepository;
    private final CharacterImageAssetRepository assetRepository;
    private final BookCharacterImageProfileRepository profileRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobLogRepository processingJobLogRepository;
    private final CharacterImageService characterImageService;
    private final CharacterImageProperties imageProperties;
    private final CdnUrlService cdnUrlService;
    private final OpenAiImageBatchClient batchClient;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public ProcessingJobResponseDTO queueFanout(Book book,
                                                BookCharacterImageProfile profile,
                                                CharacterImageAsset reference) {
        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        book.getId(),
                        ProcessingPipelineType.IMAGE_GENERATION,
                        ACTIVE_JOB_STATUSES
                )
                .ifPresent(existing -> {
                    throw new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_ACTIVE);
                });

        List<Character> targets = characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book).stream()
                .filter(character -> profile.getReferenceCharacter() == null
                        || !profile.getReferenceCharacter().getId().equals(character.getId()))
                .toList();
        if (targets.isEmpty()) {
            return null;
        }

        String runId = "character-image-fanout-" + UUID.randomUUID();
        ProcessingJob job = processingJobRepository.save(ProcessingJob.builder()
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .runId(runId)
                .sourceVersion(SOURCE_VERSION)
                .artifactPath(buildArtifactPath(book.getId(), runId))
                .status(ProcessingJobStatus.QUEUED)
                .currentStep("queued")
                .triggeredBy(TRIGGERED_BY)
                .build());

        String model = resolveEditModel();
        for (Character character : targets) {
            CharacterImageAsset asset = getOrCreateCharacterImageAsset(
                    character,
                    reference,
                    profile.getReferenceVersion()
            );
            String prompt = characterImageService.buildReferenceEditPrompt(character);
            asset.assignProcessingJob(job, model, characterImageService.buildPromptHash(prompt));
            characterRepository.updateImageGenerationStatus(character.getId(), ImageGenerationStatus.GENERATING);
        }

        writeLog(job, ProcessingJobLogLevel.INFO, "queued", "Character image fan-out Batch job has been queued.", Map.of(
                "targetCount", targets.size(),
                "referenceAssetId", reference.getId(),
                "referenceVersion", profile.getReferenceVersion(),
                "model", model
        ));
        return ProcessingJobResponseDTO.from(job);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponseDTO getFanoutJob(Long jobId) {
        return ProcessingJobResponseDTO.from(findImageJob(jobId));
    }

    @Transactional(readOnly = true)
    public List<ProcessingJobLogResponseDTO> getFanoutJobLogs(Long jobId) {
        ProcessingJob job = findImageJob(jobId);
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

    @Transactional(readOnly = true)
    public List<Long> findQueuedJobIds() {
        return processingJobRepository.findAllByPipelineTypeAndStatusInOrderByCreatedAtAsc(
                        ProcessingPipelineType.IMAGE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.QUEUED)
                ).stream()
                .map(ProcessingJob::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findSubmittedJobIds() {
        return processingJobRepository.findAllByPipelineTypeAndStatusInOrderByCreatedAtAsc(
                        ProcessingPipelineType.IMAGE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.PROCESSING)
                ).stream()
                .filter(job -> job.getExternalJobId() != null && !job.getExternalJobId().isBlank())
                .map(ProcessingJob::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findInterruptedSubmissionJobIds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        return processingJobRepository.findAllByPipelineTypeAndStatusInOrderByCreatedAtAsc(
                        ProcessingPipelineType.IMAGE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.PROCESSING)
                ).stream()
                .filter(job -> (job.getExternalJobId() == null || job.getExternalJobId().isBlank())
                        && "submitting_openai_batch".equals(job.getCurrentStep())
                        && job.getUpdatedAt() != null
                        && job.getUpdatedAt().isBefore(cutoff))
                .map(ProcessingJob::getId)
                .toList();
    }

    public void failInterruptedSubmission(Long jobId) {
        completeFailure(
                jobId,
                "IMAGE_BATCH_SUBMISSION_INTERRUPTED",
                "Batch submission was interrupted before an external job ID was stored."
        );
    }

    public void submit(Long jobId) {
        try {
            SubmissionContext context = transitionToSubmitting(jobId);
            if (context == null) {
                return;
            }

            byte[] inputJsonl = batchClient.buildImageEditInput(context.targets().stream()
                    .map(target -> new OpenAiBatchImageEditRequest(
                            CUSTOM_ID_PREFIX + target.assetId(),
                            context.model(),
                            context.referenceUrl(),
                            target.prompt(),
                            context.size(),
                            context.quality()
                    ))
                    .toList());
            OpenAiBatchSubmission submission = batchClient.submit(
                    inputJsonl,
                    context.runId() + ".jsonl",
                    context.runId(),
                    Map.of(
                            "book_id", context.bookId().toString(),
                            "run_id", context.runId(),
                            "pipeline", SOURCE_VERSION
                    )
            );
            markSubmitted(jobId, submission);
        } catch (Exception e) {
            log.error("Character image fan-out Batch submission failed. jobId={}", jobId, e);
            completeFailure(jobId, "IMAGE_BATCH_SUBMISSION_FAILED", messageOf(e));
        }
    }

    public void refresh(Long jobId) {
        try {
            PollContext context = loadPollContext(jobId);
            if (context == null) {
                return;
            }

            OpenAiBatchStatus batchStatus = batchClient.retrieve(context.externalJobId());
            updateExternalStatus(jobId, batchStatus);
            if (batchStatus.isTerminal()) {
                applyTerminalResults(jobId, batchStatus);
            }
        } catch (Exception e) {
            log.error("Character image fan-out Batch refresh failed. jobId={}", jobId, e);
            writeRefreshWarning(jobId, e);
        }
    }

    private SubmissionContext transitionToSubmitting(Long jobId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.QUEUED) {
                return null;
            }

            List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
            if (assets.isEmpty()) {
                throw new IllegalStateException("Character image fan-out job has no target assets.");
            }

            CharacterImageAsset reference = assets.get(0).getSourceReferenceAsset();
            int referenceVersion = assets.get(0).getReferenceVersion();
            validateReference(job.getBook(), reference, referenceVersion, assets);

            String referenceUrl = cdnUrlService.toPublicUrl(reference.getS3Url());
            if (referenceUrl == null || referenceUrl.isBlank()) {
                throw new IllegalStateException("Character image fan-out reference URL is missing.");
            }
            if (!cdnUrlService.isCdnUrl(referenceUrl)) {
                throw new IllegalStateException(
                        "Character image fan-out requires a public CloudFront reference URL."
                );
            }

            job.markProcessing("submitting_openai_batch");
            writeLog(job, ProcessingJobLogLevel.INFO, "submitting_openai_batch", "Submitting character images to OpenAI Batch.", Map.of(
                    "targetCount", assets.size(),
                    "referenceAssetId", reference.getId(),
                    "referenceVersion", referenceVersion,
                    "referenceUrl", referenceUrl
            ));

            List<BatchTarget> targets = assets.stream()
                    .map(asset -> new BatchTarget(
                            asset.getId(),
                            characterImageService.buildReferenceEditPrompt(asset.getCharacter())
                    ))
                    .toList();
            return new SubmissionContext(
                    job.getBook().getId(),
                    job.getRunId(),
                    referenceUrl,
                    resolveEditModel(),
                    imageProperties.getWidth() + "x" + imageProperties.getHeight(),
                    normalize(imageProperties.getQuality()),
                    targets
            );
        });
    }

    private void validateReference(Book book,
                                   CharacterImageAsset reference,
                                   int referenceVersion,
                                   List<CharacterImageAsset> assets) {
        if (reference == null || assets.stream().anyMatch(asset ->
                asset.getSourceReferenceAsset() == null
                        || !reference.getId().equals(asset.getSourceReferenceAsset().getId())
                        || asset.getReferenceVersion() != referenceVersion)) {
            throw new IllegalStateException("Character image fan-out targets do not share one reference.");
        }

        BookCharacterImageProfile profile = profileRepository.findByBook(book)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED));
        if (profile.getReferenceStatus() != BookImageReferenceStatus.APPROVED
                || profile.getActiveReferenceAsset() == null
                || !reference.getId().equals(profile.getActiveReferenceAsset().getId())
                || profile.getReferenceVersion() != referenceVersion) {
            throw new GeneralException(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
        }
    }

    private void markSubmitted(Long jobId, OpenAiBatchSubmission submission) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING || job.getExternalJobId() != null) {
                return;
            }
            job.markExternalJobSubmitted(submission.batchId(), submission.inputFileId(), submission.status());
            writeLog(job, ProcessingJobLogLevel.INFO, job.getCurrentStep(), "OpenAI Batch has been registered.", Map.of(
                    "externalJobId", submission.batchId(),
                    "inputFileId", submission.inputFileId(),
                    "status", submission.status()
            ));
        });
    }

    private PollContext loadPollContext(Long jobId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING
                    || job.getExternalJobId() == null
                    || job.getExternalJobId().isBlank()) {
                return null;
            }
            return new PollContext(job.getExternalJobId());
        });
    }

    private void updateExternalStatus(Long jobId, OpenAiBatchStatus batchStatus) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return;
            }

            String previousStep = job.getCurrentStep();
            job.updateExternalJobStatus(
                    batchStatus.status(),
                    batchStatus.outputFileId(),
                    batchStatus.errorFileId()
            );
            if (!job.getCurrentStep().equals(previousStep) || batchStatus.isTerminal()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("externalJobId", batchStatus.batchId());
                payload.put("status", batchStatus.status());
                payload.put("totalCount", batchStatus.totalCount());
                payload.put("completedCount", batchStatus.completedCount());
                payload.put("failedCount", batchStatus.failedCount());
                if (batchStatus.errorsJson() != null) {
                    payload.put("errors", batchStatus.errorsJson());
                }
                writeLog(job, ProcessingJobLogLevel.INFO, job.getCurrentStep(), "OpenAI Batch status has changed.", payload);
            }
        });
    }

    private void applyTerminalResults(Long jobId, OpenAiBatchStatus batchStatus) {
        if (!beginApplyingResults(jobId)) {
            return;
        }
        try {
            batchClient.streamResults(batchStatus.outputFileId(), result -> processResultSafely(jobId, result));
            batchClient.streamResults(batchStatus.errorFileId(), result -> processResultSafely(jobId, result));
            finalizeResults(jobId, batchStatus.status());
        } catch (Exception e) {
            completeFailure(jobId, "IMAGE_BATCH_RESULT_APPLY_FAILED", messageOf(e));
        }
    }

    private boolean beginApplyingResults(Long jobId) {
        Boolean started = writableTransaction().execute(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return false;
            }
            job.advanceProcessing("applying_batch_results");
            writeLog(job, ProcessingJobLogLevel.INFO, "applying_batch_results", "Applying OpenAI Batch image results.", Map.of(
                    "outputFileId", Optional.ofNullable(job.getOutputFileId()).orElse(""),
                    "errorFileId", Optional.ofNullable(job.getErrorFileId()).orElse("")
            ));
            return true;
        });
        return Boolean.TRUE.equals(started);
    }

    private void processResultSafely(Long jobId, OpenAiBatchImageResult result) {
        try {
            Long assetId = parseAssetId(result.customId());
            if (!result.isSuccess()) {
                markTargetFailure(jobId, assetId, normalizeFailureCode(result.errorCode()), result.errorMessage());
                return;
            }

            TargetUploadContext target = loadTargetForUpload(jobId, assetId);
            if (target == null) {
                return;
            }
            String s3Url = characterImageService.uploadGeneratedImage(
                    target.character(),
                    result.imageData(),
                    characterImageService.buildPublishedS3KeyName(target.character())
            );
            completeTargetSuccess(jobId, assetId, s3Url, result.requestId());
        } catch (Exception e) {
            log.error("Failed to apply OpenAI Batch image result. jobId={}, customId={}", jobId, result.customId(), e);
            try {
                markTargetFailure(
                        jobId,
                        parseAssetId(result.customId()),
                        "BATCH_IMAGE_PUBLISH_FAILED",
                        messageOf(e)
                );
            } catch (Exception nested) {
                writeRefreshWarning(jobId, nested);
            }
        }
    }

    private TargetUploadContext loadTargetForUpload(Long jobId, Long assetId) {
        return writableTransaction().execute(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return null;
            }
            CharacterImageAsset asset = findJobAsset(job, assetId);
            if (asset.getStatus() == CharacterImageAssetStatus.PUBLISHED) {
                return null;
            }
            return new TargetUploadContext(asset.getCharacter());
        });
    }

    private void completeTargetSuccess(Long jobId, Long assetId, String s3Url, String requestId) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return;
            }
            CharacterImageAsset asset = findJobAsset(job, assetId);
            if (asset.getStatus() == CharacterImageAssetStatus.PUBLISHED) {
                return;
            }

            asset.generated(s3Url, asset.getModel(), asset.getPromptHash(), requestId);
            asset.markQaPassed(serializePayload(Map.of(
                    "passed", true,
                    "mode", "OPENAI_BATCH_REFERENCE_EDIT",
                    "batchId", job.getExternalJobId(),
                    "referenceAssetId", asset.getSourceReferenceAsset().getId(),
                    "referenceVersion", asset.getReferenceVersion()
            )));
            asset.publish();
            characterRepository.updateProfileImageAndStatus(
                    asset.getCharacter().getId(),
                    s3Url,
                    ImageGenerationStatus.COMPLETED
            );
            writeLog(job, ProcessingJobLogLevel.INFO, "character_published", "Character image has been published from Batch output.", Map.of(
                    "assetId", asset.getId(),
                    "characterId", asset.getCharacter().getId(),
                    "requestId", Optional.ofNullable(requestId).orElse("")
            ));
        });
    }

    private void markTargetFailure(Long jobId, Long assetId, String failureCode, String failureMessage) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return;
            }
            CharacterImageAsset asset = findJobAsset(job, assetId);
            if (asset.getStatus() == CharacterImageAssetStatus.PUBLISHED) {
                return;
            }
            asset.fail(failureCode);
            characterRepository.updateImageGenerationStatus(asset.getCharacter().getId(), ImageGenerationStatus.FAILED);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assetId", asset.getId());
            payload.put("characterId", asset.getCharacter().getId());
            payload.put("failureCode", failureCode);
            if (failureMessage != null) {
                payload.put("error", failureMessage);
            }
            writeLog(job, ProcessingJobLogLevel.WARN, "character_failed", "Character image Batch request failed.", payload);
        });
    }

    private void finalizeResults(Long jobId, String externalStatus) {
        writableTransaction().executeWithoutResult(status -> {
            ProcessingJob job = findImageJobForUpdate(jobId);
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                return;
            }

            List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
            if (assets.isEmpty()) {
                job.markFailed("completed_with_failures", "IMAGE_BATCH_TARGETS_MISSING",
                        "Character image fan-out job has no target assets.");
                writeLog(job, ProcessingJobLogLevel.ERROR, "completed_with_failures",
                        "Character image fan-out Batch targets are missing.", Map.of(
                                "externalStatus", externalStatus
                        ));
                return;
            }
            for (CharacterImageAsset asset : assets) {
                if (asset.getStatus() == CharacterImageAssetStatus.GENERATING
                        || asset.getStatus() == CharacterImageAssetStatus.QA_PENDING) {
                    asset.fail("BATCH_RESULT_MISSING");
                    characterRepository.updateImageGenerationStatus(asset.getCharacter().getId(), ImageGenerationStatus.FAILED);
                }
            }

            long successCount = assets.stream()
                    .filter(asset -> asset.getStatus() == CharacterImageAssetStatus.PUBLISHED)
                    .count();
            long failedCount = assets.size() - successCount;
            Map<String, Object> payload = Map.of(
                    "externalStatus", externalStatus,
                    "targetCount", assets.size(),
                    "successCount", successCount,
                    "failedCount", failedCount
            );

            if (failedCount == 0) {
                job.markReady(job.getArtifactPath(), "completed");
                writeLog(job, ProcessingJobLogLevel.INFO, "completed", "Character image fan-out Batch job completed.", payload);
            } else {
                job.markFailed("completed_with_failures", "IMAGE_BATCH_PARTIAL_FAILURE",
                        "One or more character images were not published.");
                writeLog(job, ProcessingJobLogLevel.ERROR, "completed_with_failures",
                        "Character image fan-out Batch job completed with failures.", payload);
            }
        });
    }

    private void completeFailure(Long jobId, String failureCode, String failureMessage) {
        try {
            writableTransaction().executeWithoutResult(status -> {
                ProcessingJob job = findImageJobForUpdate(jobId);
                if (job.getStatus() == ProcessingJobStatus.READY || job.getStatus() == ProcessingJobStatus.FAILED) {
                    return;
                }
                for (CharacterImageAsset asset : assetRepository.findByProcessingJobOrderByIdAsc(job)) {
                    if (asset.getStatus() != CharacterImageAssetStatus.PUBLISHED) {
                        asset.fail(failureCode);
                        characterRepository.updateImageGenerationStatus(
                                asset.getCharacter().getId(),
                                ImageGenerationStatus.FAILED
                        );
                    }
                }
                String step = job.getCurrentStep() == null ? "failed" : job.getCurrentStep();
                job.markFailed(step, failureCode, failureMessage);
                writeLog(job, ProcessingJobLogLevel.ERROR, step, "Character image fan-out Batch job failed.", Map.of(
                        "failureCode", failureCode,
                        "error", failureMessage
                ));
            });
        } catch (Exception nested) {
            log.error("Failed to persist character image fan-out failure. jobId={}", jobId, nested);
        }
    }

    private void writeRefreshWarning(Long jobId, Exception exception) {
        try {
            writableTransaction().executeWithoutResult(status -> {
                ProcessingJob job = findImageJobForUpdate(jobId);
                if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
                    return;
                }
                if (processingJobLogRepository.findTopByJobIdOrderBySeqDesc(jobId)
                        .map(ProcessingJobLog::getStep)
                        .filter("batch_poll_failed"::equals)
                        .isPresent()) {
                    return;
                }
                writeLog(job, ProcessingJobLogLevel.WARN, "batch_poll_failed", "OpenAI Batch status check failed and will be retried.", Map.of(
                        "error", messageOf(exception)
                ));
            });
        } catch (Exception nested) {
            log.warn("Failed to persist OpenAI Batch poll warning. jobId={}", jobId, nested);
        }
    }

    private CharacterImageAsset getOrCreateCharacterImageAsset(Character character,
                                                               CharacterImageAsset reference,
                                                               int referenceVersion) {
        Optional<CharacterImageAsset> existing = assetRepository
                .findFirstByCharacterAndAssetRoleOrderByCreatedAtDesc(
                        character,
                        CharacterImageAssetRole.CHARACTER_IMAGE
                );
        if (existing.isPresent()) {
            CharacterImageAsset asset = existing.get();
            asset.beginCharacterImage(character, reference, referenceVersion);
            return asset;
        }

        return assetRepository.save(CharacterImageAsset.builder()
                .book(character.getBook())
                .character(character)
                .assetRole(CharacterImageAssetRole.CHARACTER_IMAGE)
                .generationMode(CharacterImageGenerationMode.REFERENCE_EDIT)
                .sourceReferenceAsset(reference)
                .referenceVersion(referenceVersion)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(1)
                .build());
    }

    private CharacterImageAsset findJobAsset(ProcessingJob job, Long assetId) {
        CharacterImageAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalStateException("Character image Batch asset not found: " + assetId));
        if (asset.getProcessingJob() == null || !job.getId().equals(asset.getProcessingJob().getId())) {
            throw new IllegalStateException("Character image asset does not belong to Batch job: " + assetId);
        }
        return asset;
    }

    private ProcessingJob findImageJob(Long jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_NOT_FOUND));
        ensureImageJob(job);
        return job;
    }

    private ProcessingJob findImageJobForUpdate(Long jobId) {
        ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_NOT_FOUND));
        ensureImageJob(job);
        return job;
    }

    private void ensureImageJob(ProcessingJob job) {
        if (job.getPipelineType() != ProcessingPipelineType.IMAGE_GENERATION) {
            throw new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_NOT_FOUND);
        }
    }

    private Long parseAssetId(String customId) {
        if (customId == null || !customId.startsWith(CUSTOM_ID_PREFIX)) {
            throw new IllegalArgumentException("Invalid character image Batch custom_id: " + customId);
        }
        try {
            return Long.parseLong(customId.substring(CUSTOM_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid character image Batch custom_id: " + customId, e);
        }
    }

    private String resolveEditModel() {
        String editModel = normalize(imageProperties.getEditModel());
        if (editModel != null) {
            return editModel;
        }
        String model = normalize(imageProperties.getModel());
        return model != null ? model : "gpt-image-2";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFailureCode(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "OPENAI_BATCH_REQUEST_FAILED";
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String buildArtifactPath(Long bookId, String runId) {
        return "character-images/" + bookId + "/fanout-jobs/" + runId;
    }

    private String messageOf(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void writeLog(ProcessingJob job,
                          ProcessingJobLogLevel level,
                          String step,
                          String message,
                          Map<String, Object> payload) {
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

    private TransactionTemplate writableTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private record BatchTarget(Long assetId, String prompt) {
    }

    private record SubmissionContext(
            Long bookId,
            String runId,
            String referenceUrl,
            String model,
            String size,
            String quality,
            List<BatchTarget> targets
    ) {
    }

    private record PollContext(String externalJobId) {
    }

    private record TargetUploadContext(Character character) {
    }
}
