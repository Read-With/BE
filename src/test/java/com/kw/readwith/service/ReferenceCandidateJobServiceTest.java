package com.kw.readwith.service;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.service.ReferenceCandidateJobTransactionService.CandidateJobStart;
import com.kw.readwith.service.ReferenceCandidateJobTransactionService.SlotContext;
import com.kw.readwith.service.image.GeneratedCharacterImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReferenceCandidateJobServiceTest {

    @Mock
    private ReferenceCandidateJobTransactionService transactionService;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private CharacterImageService characterImageService;

    private ExecutorService slotExecutor;

    @AfterEach
    void tearDown() {
        if (slotExecutor != null) {
            slotExecutor.shutdownNow();
        }
    }

    @Test
    void processGeneratesTwoSlotsConcurrentlyAndFinalizesJob() throws Exception {
        Book book = Book.builder().id(20L).title("Dracula").build();
        Character character = Character.builder()
                .id(200L)
                .book(book)
                .characterId(1L)
                .name("Van Helsing")
                .build();
        slotExecutor = Executors.newFixedThreadPool(2);
        ReferenceCandidateJobService service = new ReferenceCandidateJobService(
                transactionService,
                characterRepository,
                characterImageService,
                slotExecutor
        );
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();

        given(transactionService.start(900L)).willReturn(new CandidateJobStart(900L, List.of(10L, 11L)));
        given(transactionService.slotTimeoutMs()).willReturn(5000L);
        given(transactionService.loadSlotContext(eq(900L), anyLong())).willAnswer(invocation -> {
            Long assetId = invocation.getArgument(1);
            int slotNo = assetId.equals(10L) ? 1 : 2;
            return new SlotContext(assetId, 200L, "reference-candidates-run", slotNo);
        });
        given(characterRepository.findByIdWithBook(200L)).willReturn(Optional.of(character));
        given(characterImageService.generateTextImage(character)).willAnswer(invocation -> {
            int active = activeCalls.incrementAndGet();
            maxActiveCalls.accumulateAndGet(active, Math::max);
            bothStarted.countDown();
            assertThat(bothStarted.await(2, TimeUnit.SECONDS)).isTrue();
            activeCalls.decrementAndGet();
            return new GeneratedCharacterImage(new byte[]{1}, "gpt-image-2", "prompt", "hash", "req");
        });
        given(characterImageService.buildReferenceCandidateJobS3KeyName(
                eq(character),
                eq("reference-candidates-run"),
                anyInt()
        )).willAnswer(invocation -> "character-images/20/reference-jobs/reference-candidates-run/slot-"
                + invocation.getArgument(2) + ".png");
        given(characterImageService.uploadGeneratedImage(
                eq(character),
                any(byte[].class),
                anyString()
        )).willAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(2));
        given(transactionService.completeSlotSuccess(
                eq(900L),
                anyLong(),
                anyString(),
                eq("gpt-image-2"),
                eq("hash"),
                eq("req"),
                anyLong()
        )).willReturn(true);

        service.process(900L);

        assertThat(maxActiveCalls).hasValue(2);
        verify(characterImageService, times(2)).generateTextImage(character);
        verify(transactionService, times(2)).completeSlotSuccess(
                eq(900L),
                anyLong(),
                anyString(),
                eq("gpt-image-2"),
                eq("hash"),
                eq("req"),
                anyLong()
        );
        verify(transactionService).finalizeJob(900L);
    }
}
