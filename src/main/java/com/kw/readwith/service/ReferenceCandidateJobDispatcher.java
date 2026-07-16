package com.kw.readwith.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ReferenceCandidateJobDispatcher {

    private final ReferenceCandidateJobService jobService;
    private final Executor jobExecutor;
    private final Set<Long> dispatchedJobIds = ConcurrentHashMap.newKeySet();

    public ReferenceCandidateJobDispatcher(
            ReferenceCandidateJobService jobService,
            @Qualifier("referenceCandidateJobExecutor") Executor jobExecutor
    ) {
        this.jobService = jobService;
        this.jobExecutor = jobExecutor;
    }

    public void dispatch(Long jobId) {
        if (!dispatchedJobIds.add(jobId)) {
            return;
        }
        try {
            jobExecutor.execute(() -> {
                try {
                    jobService.process(jobId);
                } finally {
                    dispatchedJobIds.remove(jobId);
                }
            });
        } catch (RuntimeException e) {
            dispatchedJobIds.remove(jobId);
            log.warn("Reference candidate job dispatch was deferred. jobId={}", jobId, e);
        }
    }
}
