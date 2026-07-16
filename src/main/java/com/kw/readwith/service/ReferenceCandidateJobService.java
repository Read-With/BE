package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.dto.admin.ProcessingJobLogResponseDTO;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.service.ReferenceCandidateJobTransactionService.CandidateJobStart;
import com.kw.readwith.service.ReferenceCandidateJobTransactionService.SlotContext;
import com.kw.readwith.service.image.GeneratedCharacterImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ReferenceCandidateJobService {

    private final ReferenceCandidateJobTransactionService transactionService;
    private final CharacterRepository characterRepository;
    private final CharacterImageService characterImageService;
    private final Executor slotExecutor;

    public ReferenceCandidateJobService(
            ReferenceCandidateJobTransactionService transactionService,
            CharacterRepository characterRepository,
            CharacterImageService characterImageService,
            @Qualifier("referenceCandidateSlotExecutor") Executor slotExecutor
    ) {
        this.transactionService = transactionService;
        this.characterRepository = characterRepository;
        this.characterImageService = characterImageService;
        this.slotExecutor = slotExecutor;
    }

    public ProcessingJobResponseDTO queue(Book book, Character referenceCharacter) {
        return transactionService.queue(book, referenceCharacter);
    }

    public void process(Long jobId) {
        try {
            CandidateJobStart start = transactionService.start(jobId);
            if (start == null) {
                return;
            }

            List<CompletableFuture<Void>> slotFutures = start.assetIds().stream()
                    .map(assetId -> generateSlotWithTimeout(jobId, assetId))
                    .toList();
            CompletableFuture.allOf(slotFutures.toArray(CompletableFuture[]::new)).join();
            transactionService.finalizeJob(jobId);
        } catch (Exception e) {
            log.error("Reference candidate generation job failed. jobId={}", jobId, e);
            try {
                transactionService.failJob(jobId, "REFERENCE_GENERATION_JOB_FAILED", e);
            } catch (Exception nested) {
                log.error("Failed to persist reference candidate job failure. jobId={}", jobId, nested);
            }
        }
    }

    public ProcessingJobResponseDTO getJob(Long jobId) {
        return transactionService.getJob(jobId);
    }

    public List<ProcessingJobLogResponseDTO> getJobLogs(Long jobId) {
        return transactionService.getJobLogs(jobId);
    }

    public List<Long> findQueuedJobIds() {
        return transactionService.findQueuedJobIds();
    }

    public List<Long> findInterruptedJobIds() {
        return transactionService.findInterruptedJobIds();
    }

    public void failInterrupted(Long jobId) {
        transactionService.failInterrupted(jobId);
    }

    public void ensureNoActiveJob(Long bookId) {
        transactionService.ensureNoActiveJob(bookId);
    }

    private CompletableFuture<Void> generateSlotWithTimeout(Long jobId, Long assetId) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long startedAtNanos = System.nanoTime();
        CompletableFuture<Void> generation = CompletableFuture.runAsync(
                () -> generateSlot(jobId, assetId, cancelled, startedAtNanos),
                slotExecutor
        );

        return generation
                .orTimeout(transactionService.slotTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    if (cancelled.compareAndSet(false, true)) {
                        long elapsedMs = elapsedMillis(startedAtNanos);
                        Throwable failure = isTimeout(exception)
                                ? new TimeoutException("Reference candidate slot exceeded "
                                + transactionService.slotTimeoutMs() + " ms.")
                                : exception;
                        transactionService.completeSlotFailure(
                                jobId,
                                assetId,
                                isTimeout(exception)
                                        ? "REFERENCE_GENERATION_TIMEOUT"
                                        : "REFERENCE_GENERATION_FAILED",
                                failure,
                                elapsedMs
                        );
                    }
                    return null;
                });
    }

    private void generateSlot(Long jobId,
                              Long assetId,
                              AtomicBoolean cancelled,
                              long startedAtNanos) {
        String uploadedUrl = null;
        try {
            SlotContext context = transactionService.loadSlotContext(jobId, assetId);
            if (context == null || cancelled.get()) {
                return;
            }
            Character character = characterRepository.findByIdWithBook(context.characterId())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.CHARACTER_NOT_FOUND));

            GeneratedCharacterImage generated = characterImageService.generateTextImage(character);
            if (cancelled.get()) {
                return;
            }

            uploadedUrl = characterImageService.uploadGeneratedImage(
                    character,
                    generated.imageData(),
                    characterImageService.buildReferenceCandidateJobS3KeyName(
                            character,
                            context.runId(),
                            context.slotNo()
                    )
            );
            if (cancelled.get()) {
                characterImageService.deleteGeneratedImage(uploadedUrl);
                return;
            }

            boolean committed = transactionService.completeSlotSuccess(
                    jobId,
                    assetId,
                    uploadedUrl,
                    generated.model(),
                    generated.promptHash(),
                    generated.requestId(),
                    elapsedMillis(startedAtNanos)
            );
            if (!committed) {
                characterImageService.deleteGeneratedImage(uploadedUrl);
            }
        } catch (Exception e) {
            if (uploadedUrl != null) {
                characterImageService.deleteGeneratedImage(uploadedUrl);
            }
            if (!cancelled.get()) {
                log.error("Reference candidate slot generation failed. jobId={}, assetId={}", jobId, assetId, e);
                transactionService.completeSlotFailure(
                        jobId,
                        assetId,
                        "REFERENCE_GENERATION_FAILED",
                        e,
                        elapsedMillis(startedAtNanos)
                );
            }
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
