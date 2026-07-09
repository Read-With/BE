package com.kw.readwith.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RelationshipDeltaImportJobDispatcher {

    private final RelationshipDeltaImportJobService relationshipDeltaImportJobService;

    @Async("normalizationJobExecutor")
    public void dispatch(Long jobId) {
        relationshipDeltaImportJobService.execute(jobId);
    }
}
