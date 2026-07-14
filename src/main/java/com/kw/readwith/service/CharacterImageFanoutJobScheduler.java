package com.kw.readwith.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterImageFanoutJobScheduler {

    private final CharacterImageFanoutJobService fanoutJobService;
    private final CharacterImageFanoutJobDispatcher dispatcher;

    @Scheduled(
            fixedDelayString = "${character-image.batch-poll-interval-ms:30000}",
            initialDelayString = "${character-image.batch-poll-interval-ms:30000}"
    )
    public void resumeAndPoll() {
        try {
            fanoutJobService.findQueuedJobIds().forEach(dispatcher::dispatch);
            fanoutJobService.findInterruptedSubmissionJobIds()
                    .forEach(fanoutJobService::failInterruptedSubmission);
            fanoutJobService.findSubmittedJobIds().forEach(fanoutJobService::refresh);
        } catch (Exception e) {
            log.warn("Character image fan-out scheduler cycle failed.", e);
        }
    }
}
