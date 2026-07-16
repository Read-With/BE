package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferenceCandidateJobTransactionService {

    private static final String SOURCE_VERSION = "reference-candidate-v2";
    private static final String TRIGGERED_BY = "ADMIN_REFERENCE_GENERATION";
    private static final int MAX_CANDIDATE_COUNT = 4;
    private static final EnumSet<ProcessingJobStatus> ACTIVE_JOB_STATUSES =
            EnumSet.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.PROCESSING);

    private final BookCharacterImageProfileRepository profileRepository;
    private final CharacterImageAssetRepository assetRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingJobLogRepository processingJobLogRepository;
    private final CharacterImageService characterImageService;
    private final CharacterImageProperties imageProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessingJobResponseDTO queue(Book book, Character referenceCharacter) {
        ensureNoActiveReferenceJob(book.getId());
        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        book.getId(),
                        ProcessingPipelineType.IMAGE_GENERATION,
                        ACTIVE_JOB_STATUSES
                )
                .ifPresent(job -> {
                    throw new GeneralException(ErrorStatus.IMAGE_FANOUT_JOB_ACTIVE);
                });

        BookCharacterImageProfile profile = profileRepository.findByBook(book)
                .orElseGet(() -> profileRepository.save(BookCharacterImageProfile.builder()
                        .book(book)
                        .build()));
        String model = resolveTextImageModel();
        String prompt = characterImageService.buildImagePrompt(referenceCharacter);
        profile.markReferenceCandidatesGenerating(
                referenceCharacter,
                model,
                sha256(normalize(imageProperties.getBaseStylePrompt())),
                sha256(normalize(book.getBookPrompt()))
        );

        String runId = "reference-candidates-" + UUID.randomUUID();
        ProcessingJob job = processingJobRepository.save(ProcessingJob.builder()
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_REFERENCE_GENERATION)
                .runId(runId)
                .sourceVersion(SOURCE_VERSION)
                .artifactPath(buildArtifactPath(book.getId(), runId))
                .status(ProcessingJobStatus.QUEUED)
                .currentStep("queued")
                .triggeredBy(TRIGGERED_BY)
                .build());

        int candidateCount = candidateCount();
        String promptHash = characterImageService.buildPromptHash(prompt);
        for (int slotNo = 1; slotNo <= candidateCount; slotNo++) {
            CharacterImageAsset asset = getOrCreateReferenceCandidate(book, referenceCharacter, slotNo);
            asset.assignProcessingJob(job, model, promptHash);
        }

        writeLog(job, ProcessingJobLogLevel.INFO, "queued", "Reference candidate generation job has been queued.", Map.of(
                "candidateCount", candidateCount,
                "concurrency", 2,
                "model", model,
                "referenceCharacterId", referenceCharacter.getId(),
                "slotTimeoutMs", slotTimeoutMs()
        ));
        return ProcessingJobResponseDTO.from(job);
    }

    @Transactional
    public CandidateJobStart start(Long jobId) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() != ProcessingJobStatus.QUEUED) {
            return null;
        }

        List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
        if (assets.isEmpty()) {
            throw new IllegalStateException("Reference candidate job has no target assets.");
        }

        job.markProcessing("generating_candidates");
        writeLog(job, ProcessingJobLogLevel.INFO, "generating_candidates", "Reference candidate generation has started.", Map.of(
                "candidateCount", assets.size(),
                "concurrency", 2,
                "slotTimeoutMs", slotTimeoutMs()
        ));
        return new CandidateJobStart(job.getId(), assets.stream().map(CharacterImageAsset::getId).toList());
    }

    @Transactional(readOnly = true)
    public SlotContext loadSlotContext(Long jobId, Long assetId) {
        ProcessingJob job = findReferenceJob(jobId);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            return null;
        }

        CharacterImageAsset asset = findJobAsset(job, assetId);
        if (asset.getStatus() != CharacterImageAssetStatus.GENERATING) {
            return null;
        }
        return new SlotContext(
                asset.getId(),
                asset.getCharacter().getId(),
                job.getRunId(),
                asset.getSlotNo()
        );
    }

    @Transactional
    public boolean completeSlotSuccess(Long jobId,
                                       Long assetId,
                                       String s3Url,
                                       String model,
                                       String promptHash,
                                       String requestId,
                                       long elapsedMs) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            return false;
        }

        CharacterImageAsset asset = findJobAsset(job, assetId);
        if (asset.getStatus() != CharacterImageAssetStatus.GENERATING) {
            return false;
        }

        String previousS3Url = asset.getS3Url();
        asset.generated(s3Url, model, promptHash, requestId);
        asset.markQaPassed("{\"passed\":true,\"mode\":\"ADMIN_REFERENCE_CANDIDATE\"}");
        updateProgress(job);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetId", asset.getId());
        payload.put("slotNo", asset.getSlotNo());
        payload.put("elapsedMs", elapsedMs);
        payload.put("model", model);
        if (requestId != null && !requestId.isBlank()) {
            payload.put("openAiRequestId", requestId);
        }
        writeLog(job, ProcessingJobLogLevel.INFO, "candidate_ready", "Reference candidate slot is ready.", payload);
        characterImageService.deleteReplacedGeneratedImage(previousS3Url, s3Url);
        return true;
    }

    @Transactional
    public boolean completeSlotFailure(Long jobId,
                                       Long assetId,
                                       String failureCode,
                                       Throwable throwable,
                                       long elapsedMs) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            return false;
        }

        CharacterImageAsset asset = findJobAsset(job, assetId);
        if (asset.getStatus() != CharacterImageAssetStatus.GENERATING
                && asset.getStatus() != CharacterImageAssetStatus.QA_PENDING) {
            return false;
        }

        asset.fail(failureCode);
        updateProgress(job);
        Throwable rootCause = rootCause(throwable);
        writeLog(job, ProcessingJobLogLevel.ERROR, "candidate_failed", "Reference candidate slot generation failed.", Map.of(
                "assetId", asset.getId(),
                "slotNo", asset.getSlotNo(),
                "elapsedMs", elapsedMs,
                "failureCode", failureCode,
                "exceptionType", rootCause.getClass().getName(),
                "error", messageOf(rootCause)
        ));
        return true;
    }

    @Transactional
    public void finalizeJob(Long jobId) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            return;
        }
        finalizeLocked(job, "REFERENCE_GENERATION_INCOMPLETE");
    }

    @Transactional
    public void failJob(Long jobId, String failureCode, Throwable throwable) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() == ProcessingJobStatus.READY || job.getStatus() == ProcessingJobStatus.FAILED) {
            return;
        }

        List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
        assets.stream()
                .filter(this::isInProgress)
                .forEach(asset -> asset.fail(failureCode));

        Throwable rootCause = rootCause(throwable);
        String step = job.getCurrentStep() == null ? "failed" : job.getCurrentStep();
        writeLog(job, ProcessingJobLogLevel.ERROR, step, "Reference candidate generation job failed.", Map.of(
                "failureCode", failureCode,
                "exceptionType", rootCause.getClass().getName(),
                "error", messageOf(rootCause)
        ));
        finalizeLocked(job, failureCode);
    }

    @Transactional
    public void failInterrupted(Long jobId) {
        ProcessingJob job = findReferenceJobForUpdate(jobId);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            return;
        }
        finalizeLocked(job, "REFERENCE_GENERATION_INTERRUPTED");
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponseDTO getJob(Long jobId) {
        return ProcessingJobResponseDTO.from(findReferenceJob(jobId));
    }

    @Transactional(readOnly = true)
    public List<ProcessingJobLogResponseDTO> getJobLogs(Long jobId) {
        ProcessingJob job = findReferenceJob(jobId);
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
                        ProcessingPipelineType.IMAGE_REFERENCE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.QUEUED)
                ).stream()
                .map(ProcessingJob::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findInterruptedJobIds() {
        LocalDateTime cutoff = LocalDateTime.now().minus(
                Duration.ofMillis(slotTimeoutMs() + 120000)
        );
        return processingJobRepository.findAllByPipelineTypeAndStatusInOrderByCreatedAtAsc(
                        ProcessingPipelineType.IMAGE_REFERENCE_GENERATION,
                        EnumSet.of(ProcessingJobStatus.PROCESSING)
                ).stream()
                .filter(job -> job.getUpdatedAt() != null && job.getUpdatedAt().isBefore(cutoff))
                .map(ProcessingJob::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureNoActiveJob(Long bookId) {
        ensureNoActiveReferenceJob(bookId);
    }

    public long slotTimeoutMs() {
        return Math.max(imageProperties.getReferenceCandidateTimeoutMs(), 1000);
    }

    private void finalizeLocked(ProcessingJob job, String incompleteFailureCode) {
        List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
        assets.stream()
                .filter(this::isInProgress)
                .forEach(asset -> asset.fail(incompleteFailureCode));

        long successCount = assets.stream().filter(this::isReadyForSelection).count();
        long failedCount = assets.size() - successCount;
        Character referenceCharacter = assets.stream()
                .findFirst()
                .map(CharacterImageAsset::getCharacter)
                .orElse(null);
        Optional<BookCharacterImageProfile> profile = profileRepository.findByBook(job.getBook());

        Map<String, Object> payload = Map.of(
                "candidateCount", assets.size(),
                "successCount", successCount,
                "failedCount", failedCount
        );
        if (successCount > 0) {
            profile.ifPresent(value -> value.markReferenceCandidatesReady(referenceCharacter));
            String step = failedCount > 0 ? "completed_with_partial_failures" : "completed";
            job.markReady(job.getArtifactPath(), step);
            ProcessingJobLogLevel level = failedCount > 0
                    ? ProcessingJobLogLevel.WARN
                    : ProcessingJobLogLevel.INFO;
            writeLog(job, level, step, "Reference candidate generation job completed.", payload);
            return;
        }

        profile.ifPresent(value -> value.markQaFailed(referenceCharacter));
        job.markFailed(
                "completed_with_failures",
                "REFERENCE_GENERATION_FAILED",
                "All reference candidate slots failed."
        );
        writeLog(job, ProcessingJobLogLevel.ERROR, "completed_with_failures",
                "Reference candidate generation job completed without a usable candidate.", payload);
    }

    private void updateProgress(ProcessingJob job) {
        List<CharacterImageAsset> assets = assetRepository.findByProcessingJobOrderByIdAsc(job);
        long completedCount = assets.stream().filter(asset -> !isInProgress(asset)).count();
        job.advanceProcessing("completed_" + completedCount + "_of_" + assets.size());
    }

    private CharacterImageAsset getOrCreateReferenceCandidate(Book book,
                                                              Character referenceCharacter,
                                                              int slotNo) {
        Optional<CharacterImageAsset> existing = assetRepository.findByBookAndAssetRoleAndSlotNo(
                book,
                CharacterImageAssetRole.REFERENCE_CANDIDATE,
                slotNo
        );
        if (existing.isPresent()) {
            CharacterImageAsset asset = existing.get();
            asset.beginReferenceCandidate(referenceCharacter, slotNo);
            return asset;
        }

        return assetRepository.save(CharacterImageAsset.builder()
                .book(book)
                .character(referenceCharacter)
                .assetRole(CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .slotNo(slotNo)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(1)
                .build());
    }

    private CharacterImageAsset findJobAsset(ProcessingJob job, Long assetId) {
        CharacterImageAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalStateException("Reference candidate asset not found: " + assetId));
        if (asset.getProcessingJob() == null || !job.getId().equals(asset.getProcessingJob().getId())) {
            throw new IllegalStateException("Reference candidate asset does not belong to job: " + assetId);
        }
        return asset;
    }

    private ProcessingJob findReferenceJob(Long jobId) {
        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_JOB_NOT_FOUND));
        ensureReferenceJob(job);
        return job;
    }

    private ProcessingJob findReferenceJobForUpdate(Long jobId) {
        ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.IMAGE_REFERENCE_JOB_NOT_FOUND));
        ensureReferenceJob(job);
        return job;
    }

    private void ensureReferenceJob(ProcessingJob job) {
        if (job.getPipelineType() != ProcessingPipelineType.IMAGE_REFERENCE_GENERATION) {
            throw new GeneralException(ErrorStatus.IMAGE_REFERENCE_JOB_NOT_FOUND);
        }
    }

    private void ensureNoActiveReferenceJob(Long bookId) {
        processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                        bookId,
                        ProcessingPipelineType.IMAGE_REFERENCE_GENERATION,
                        ACTIVE_JOB_STATUSES
                )
                .ifPresent(job -> {
                    throw new GeneralException(ErrorStatus.IMAGE_REFERENCE_JOB_ACTIVE);
                });
    }

    private boolean isReadyForSelection(CharacterImageAsset asset) {
        return asset.getStatus() == CharacterImageAssetStatus.QA_PASSED
                || asset.getStatus() == CharacterImageAssetStatus.APPROVED
                || asset.getStatus() == CharacterImageAssetStatus.PUBLISHED;
    }

    private boolean isInProgress(CharacterImageAsset asset) {
        return asset.getStatus() == CharacterImageAssetStatus.GENERATING
                || asset.getStatus() == CharacterImageAssetStatus.QA_PENDING;
    }

    private int candidateCount() {
        return Math.min(Math.max(imageProperties.getReferenceCandidateCount(), 1), MAX_CANDIDATE_COUNT);
    }

    private String resolveTextImageModel() {
        String configured = normalize(imageProperties.getModel());
        return configured != null ? configured : "gpt-image-2";
    }

    private String buildArtifactPath(Long bookId, String runId) {
        return "character-images/" + bookId + "/reference-jobs/" + runId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        String value = message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
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

    public record CandidateJobStart(Long jobId, List<Long> assetIds) {
    }

    public record SlotContext(Long assetId,
                              Long characterId,
                              String runId,
                              Integer slotNo) {
    }
}
