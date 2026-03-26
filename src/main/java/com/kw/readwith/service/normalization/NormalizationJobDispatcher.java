package com.kw.readwith.service.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalizationJobDispatcher {

    private final NormalizationJobService normalizationJobService;

    @Async("normalizationJobExecutor")
    public void dispatch(Long jobId) {
        normalizationJobService.execute(jobId);
    }
}
