package com.kw.readwith.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReferenceCandidateJobDispatcherTest {

    @Mock
    private ReferenceCandidateJobService jobService;
    @Mock
    private Executor jobExecutor;

    @Test
    void dispatchSuppressesDuplicateQueueEntriesUntilCurrentTaskFinishes() {
        ReferenceCandidateJobDispatcher dispatcher = new ReferenceCandidateJobDispatcher(
                jobService,
                jobExecutor
        );
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        dispatcher.dispatch(900L);
        dispatcher.dispatch(900L);

        verify(jobExecutor).execute(taskCaptor.capture());
        taskCaptor.getValue().run();
        verify(jobService).process(900L);

        dispatcher.dispatch(900L);
        verify(jobExecutor, times(2)).execute(taskCaptor.capture());
    }
}
