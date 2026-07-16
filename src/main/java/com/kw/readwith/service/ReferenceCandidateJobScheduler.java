package com.kw.readwith.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCandidateJobScheduler {

    private final ReferenceCandidateJobService jobService;
    private final ReferenceCandidateJobDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${character-image.reference-candidate-scheduler-interval-ms:30000}")
    public void dispatchQueuedJobs() {
        try {
            jobService.findQueuedJobIds().forEach(this::dispatchSafely);
            jobService.findInterruptedJobIds().forEach(this::failInterruptedSafely);
        } catch (Exception e) {
            log.warn("Reference candidate job scheduler failed.", e);
        }
    }

    private void dispatchSafely(Long jobId) {
        try {
            dispatcher.dispatch(jobId);
        } catch (RuntimeException e) {
            log.warn("Reference candidate job dispatch was deferred. jobId={}", jobId, e);
        }
    }

    private void failInterruptedSafely(Long jobId) {
        try {
            jobService.failInterrupted(jobId);
        } catch (RuntimeException e) {
            log.warn("Failed to close interrupted reference candidate job. jobId={}", jobId, e);
        }
    }
}
