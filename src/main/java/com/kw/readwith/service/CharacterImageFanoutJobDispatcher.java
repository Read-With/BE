package com.kw.readwith.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CharacterImageFanoutJobDispatcher {

    private final CharacterImageFanoutJobService fanoutJobService;

    @Async("imageGenerationExecutor")
    public void dispatch(Long jobId) {
        fanoutJobService.submit(jobId);
    }
}
