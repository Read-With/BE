package com.kw.readwith.service;

import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.BookImageReferenceStatus;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceCandidateJobTransactionServiceTest {

    @InjectMocks
    private ReferenceCandidateJobTransactionService service;

    @Mock
    private BookCharacterImageProfileRepository profileRepository;
    @Mock
    private CharacterImageAssetRepository assetRepository;
    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private ProcessingJobLogRepository processingJobLogRepository;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private CharacterImageProperties imageProperties;
    @Mock
    private ObjectMapper objectMapper;

    private Book book;
    private Character referenceCharacter;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .id(20L)
                .title("Dracula")
                .author("Bram Stoker")
                .language("en")
                .bookPrompt("Victorian gothic portrait")
                .build();
        referenceCharacter = Character.builder()
                .id(200L)
                .book(book)
                .characterId(1L)
                .name("Van Helsing")
                .profileText("elderly professor")
                .isMainCharacter(true)
                .build();
    }

    @Test
    void queueCreatesTwoGeneratingSlotsAndReturnsImmediately() throws Exception {
        BookCharacterImageProfile profile = BookCharacterImageProfile.builder()
                .book(book)
                .build();
        List<CharacterImageAsset> savedAssets = new ArrayList<>();
        AtomicLong assetIds = new AtomicLong(1L);

        given(profileRepository.findByBook(book)).willReturn(Optional.empty());
        when(profileRepository.save(any(BookCharacterImageProfile.class))).thenReturn(profile);
        given(imageProperties.getModel()).willReturn("gpt-image-2");
        given(imageProperties.getBaseStylePrompt()).willReturn("editorial gouache");
        given(imageProperties.getReferenceCandidateCount()).willReturn(2);
        given(imageProperties.getReferenceCandidateTimeoutMs()).willReturn(420000L);
        given(characterImageService.buildImagePrompt(referenceCharacter)).willReturn("prompt");
        given(characterImageService.buildPromptHash("prompt")).willReturn("prompt-hash");
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 900L);
            return job;
        });
        when(assetRepository.save(any(CharacterImageAsset.class))).thenAnswer(invocation -> {
            CharacterImageAsset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", assetIds.getAndIncrement());
            savedAssets.add(asset);
            return asset;
        });
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        ProcessingJobResponseDTO response = service.queue(book, referenceCharacter);

        assertThat(response.getId()).isEqualTo(900L);
        assertThat(response.getPipelineType()).isEqualTo(ProcessingPipelineType.IMAGE_REFERENCE_GENERATION);
        assertThat(response.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
        assertThat(profile.getReferenceStatus()).isEqualTo(BookImageReferenceStatus.CANDIDATE_GENERATING);
        assertThat(savedAssets).hasSize(2);
        assertThat(savedAssets).extracting(CharacterImageAsset::getSlotNo).containsExactly(1, 2);
        assertThat(savedAssets).extracting(CharacterImageAsset::getStatus)
                .containsOnly(CharacterImageAssetStatus.GENERATING);
        assertThat(savedAssets).allSatisfy(asset -> {
            assertThat(asset.getProcessingJob().getId()).isEqualTo(900L);
            assertThat(asset.getModel()).isEqualTo("gpt-image-2");
            assertThat(asset.getPromptHash()).isEqualTo("prompt-hash");
        });
        verify(assetRepository, times(2)).save(any(CharacterImageAsset.class));
        verify(processingJobLogRepository).save(any(ProcessingJobLog.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void slotFailureLogContainsElapsedTimeAndRootCause() throws Exception {
        ProcessingJob job = ProcessingJob.builder()
                .id(901L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_REFERENCE_GENERATION)
                .runId("reference-candidates-run")
                .status(ProcessingJobStatus.PROCESSING)
                .currentStep("generating_candidates")
                .build();
        CharacterImageAsset asset = CharacterImageAsset.builder()
                .id(10L)
                .book(book)
                .character(referenceCharacter)
                .assetRole(CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .processingJob(job)
                .slotNo(1)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(1)
                .build();

        given(processingJobRepository.findByIdForUpdate(901L)).willReturn(Optional.of(job));
        given(assetRepository.findById(10L)).willReturn(Optional.of(asset));
        given(assetRepository.findByProcessingJobOrderByIdAsc(job)).willReturn(List.of(asset));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        boolean completed = service.completeSlotFailure(
                901L,
                10L,
                "REFERENCE_GENERATION_FAILED",
                new IllegalStateException("OpenAI upstream failure"),
                1234L
        );

        assertThat(completed).isTrue();
        assertThat(asset.getStatus()).isEqualTo(CharacterImageAssetStatus.FAILED);
        assertThat(asset.getFailureCode()).isEqualTo("REFERENCE_GENERATION_FAILED");
        assertThat(job.getCurrentStep()).isEqualTo("completed_1_of_1");

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(objectMapper).writeValueAsString(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("slotNo", 1)
                .containsEntry("elapsedMs", 1234L)
                .containsEntry("exceptionType", IllegalStateException.class.getName())
                .containsEntry("error", "OpenAI upstream failure");

        ArgumentCaptor<ProcessingJobLog> logCaptor = ArgumentCaptor.forClass(ProcessingJobLog.class);
        verify(processingJobLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getLevel()).isEqualTo(ProcessingJobLogLevel.ERROR);
        assertThat(logCaptor.getValue().getStep()).isEqualTo("candidate_failed");
    }
}
