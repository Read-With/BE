package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
import com.kw.readwith.domain.enums.BookImageReferenceStatus;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.dto.admin.ProcessingJobResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.ProcessingJobLogRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import com.kw.readwith.service.image.OpenAiImageBatchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterImageFanoutJobService")
class CharacterImageFanoutJobServiceTest {

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private CharacterImageAssetRepository assetRepository;
    @Mock
    private BookCharacterImageProfileRepository profileRepository;
    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private ProcessingJobLogRepository processingJobLogRepository;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private CharacterImageProperties imageProperties;
    @Mock
    private CdnUrlService cdnUrlService;
    @Mock
    private OpenAiImageBatchClient batchClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private CharacterImageFanoutJobService service;

    private Book book;
    private Character referenceCharacter;
    private Character targetCharacter;
    private CharacterImageAsset referenceAsset;
    private BookCharacterImageProfile profile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        book = Book.builder().id(20L).title("The Adventures of Sherlock Holmes").build();
        referenceCharacter = Character.builder()
                .id(100L)
                .book(book)
                .characterId(1L)
                .name("Sherlock Holmes")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.COMPLETED)
                .build();
        targetCharacter = Character.builder()
                .id(101L)
                .book(book)
                .characterId(2L)
                .name("Dr. Watson")
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();
        referenceAsset = CharacterImageAsset.builder()
                .id(500L)
                .book(book)
                .character(referenceCharacter)
                .assetRole(CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .status(CharacterImageAssetStatus.PUBLISHED)
                .s3Url("https://cdn.readwith.store/reference.png")
                .model("gpt-image-2")
                .build();
        profile = BookCharacterImageProfile.builder()
                .book(book)
                .activeReferenceAsset(referenceAsset)
                .referenceCharacter(referenceCharacter)
                .referenceVersion(1)
                .referenceStatus(BookImageReferenceStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("queues one GPT Image 2 Batch target for every non-reference character")
    void queueFanout_preparesBatchTargets() {
        AtomicLong idSequence = new AtomicLong(700L);
        given(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                eq(20L), eq(ProcessingPipelineType.IMAGE_GENERATION), any()
        )).willReturn(Optional.empty());
        given(characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book))
                .willReturn(List.of(referenceCharacter, targetCharacter));
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 600L);
            return job;
        });
        given(assetRepository.findFirstByCharacterAndAssetRoleOrderByCreatedAtDesc(
                targetCharacter, CharacterImageAssetRole.CHARACTER_IMAGE
        )).willReturn(Optional.empty());
        when(assetRepository.save(any(CharacterImageAsset.class))).thenAnswer(invocation -> {
            CharacterImageAsset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", idSequence.getAndIncrement());
            return asset;
        });
        given(imageProperties.getEditModel()).willReturn("gpt-image-2");
        given(characterImageService.buildReferenceEditPrompt(targetCharacter)).willReturn("series lock: Watson");
        given(characterImageService.buildPromptHash("series lock: Watson")).willReturn("prompt-hash");
        given(processingJobLogRepository.countByJobId(600L)).willReturn(0L);

        ProcessingJobResponseDTO response = service.queueFanout(book, profile, referenceAsset);

        assertThat(response.getPipelineType()).isEqualTo(ProcessingPipelineType.IMAGE_GENERATION);
        assertThat(response.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
        ArgumentCaptor<CharacterImageAsset> assetCaptor = ArgumentCaptor.forClass(CharacterImageAsset.class);
        verify(assetRepository).save(assetCaptor.capture());
        CharacterImageAsset targetAsset = assetCaptor.getValue();
        assertThat(targetAsset.getSourceReferenceAsset()).isSameAs(referenceAsset);
        assertThat(targetAsset.getReferenceVersion()).isEqualTo(1);
        assertThat(targetAsset.getProcessingJob().getId()).isEqualTo(600L);
        assertThat(targetAsset.getModel()).isEqualTo("gpt-image-2");
        assertThat(targetAsset.getPromptHash()).isEqualTo("prompt-hash");
        verify(characterRepository).updateImageGenerationStatus(101L, ImageGenerationStatus.GENERATING);
    }

    @Test
    @DisplayName("rejects a second active fan-out job for the same book")
    void queueFanout_rejectsActiveJob() {
        ProcessingJob activeJob = ProcessingJob.builder()
                .id(601L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .status(ProcessingJobStatus.PROCESSING)
                .build();
        given(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                eq(20L), eq(ProcessingPipelineType.IMAGE_GENERATION), any()
        )).willReturn(Optional.of(activeJob));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.queueFanout(book, profile, referenceAsset)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_FANOUT_JOB_ACTIVE);
    }

    @Test
    @DisplayName("submits a queued fan-out job and stores the OpenAI Batch identifiers")
    void submit_registersOpenAiBatch() {
        ProcessingJob job = ProcessingJob.builder()
                .id(610L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .runId("character-image-fanout-run-1")
                .artifactPath("character-images/20/fanout-jobs/run-1")
                .status(ProcessingJobStatus.QUEUED)
                .currentStep("queued")
                .build();
        CharacterImageAsset targetAsset = buildTargetAsset(710L, job);
        given(transactionManager.getTransaction(any())).willAnswer(invocation -> new SimpleTransactionStatus());
        given(processingJobRepository.findByIdForUpdate(610L)).willReturn(Optional.of(job));
        given(assetRepository.findByProcessingJobOrderByIdAsc(job)).willReturn(List.of(targetAsset));
        given(profileRepository.findByBook(book)).willReturn(Optional.of(profile));
        given(cdnUrlService.toPublicUrl(referenceAsset.getS3Url()))
                .willReturn("https://cdn.readwith.store/character-images/20/reference/slot-1.png");
        given(cdnUrlService.isCdnUrl(any())).willReturn(true);
        given(characterImageService.buildReferenceEditPrompt(targetCharacter)).willReturn("series lock: Watson");
        given(imageProperties.getEditModel()).willReturn("gpt-image-2");
        given(imageProperties.getWidth()).willReturn(1024);
        given(imageProperties.getHeight()).willReturn(1024);
        given(imageProperties.getQuality()).willReturn("medium");
        given(batchClient.buildImageEditInput(any())).willReturn(new byte[]{1});
        given(batchClient.submit(any(), any(), any(), any())).willReturn(
                new OpenAiImageBatchClient.OpenAiBatchSubmission("file-input", "batch-610", "validating")
        );

        service.submit(610L);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PROCESSING);
        assertThat(job.getExternalJobId()).isEqualTo("batch-610");
        assertThat(job.getInputFileId()).isEqualTo("file-input");
        assertThat(job.getCurrentStep()).isEqualTo("openai_validating");
    }

    @Test
    @DisplayName("publishes streamed Batch output and marks the fan-out job ready")
    void refresh_publishesCompletedBatchOutput() {
        ProcessingJob job = ProcessingJob.builder()
                .id(620L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .runId("character-image-fanout-run-2")
                .artifactPath("character-images/20/fanout-jobs/run-2")
                .externalJobId("batch-620")
                .inputFileId("file-input")
                .status(ProcessingJobStatus.PROCESSING)
                .currentStep("openai_in_progress")
                .build();
        CharacterImageAsset targetAsset = buildTargetAsset(720L, job);
        given(transactionManager.getTransaction(any())).willAnswer(invocation -> new SimpleTransactionStatus());
        given(processingJobRepository.findByIdForUpdate(620L)).willReturn(Optional.of(job));
        given(assetRepository.findByProcessingJobOrderByIdAsc(job)).willReturn(List.of(targetAsset));
        given(assetRepository.findById(720L)).willReturn(Optional.of(targetAsset));
        given(batchClient.retrieve("batch-620")).willReturn(new OpenAiImageBatchClient.OpenAiBatchStatus(
                "batch-620", "completed", "file-output", null, 1, 1, 0, null
        ));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<OpenAiImageBatchClient.OpenAiBatchImageResult> consumer = invocation.getArgument(1);
            consumer.accept(new OpenAiImageBatchClient.OpenAiBatchImageResult(
                    "character-image-asset-720", 200, "req-720", new byte[]{1, 2, 3}, null, null
            ));
            return null;
        }).when(batchClient).streamResults(eq("file-output"), any());
        given(characterImageService.buildPublishedS3KeyName(20L, 101L, 1, 1))
                .willReturn("character-images/20/101/reference-v1/attempt-1.png");
        given(characterImageService.uploadGeneratedImage(
                any(byte[].class),
                eq("character-images/20/101/reference-v1/attempt-1.png")
        )).willReturn("https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png");

        service.refresh(620L);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.READY);
        assertThat(job.getOutputFileId()).isEqualTo("file-output");
        assertThat(targetAsset.getStatus()).isEqualTo(CharacterImageAssetStatus.PUBLISHED);
        assertThat(targetAsset.getS3Url())
                .isEqualTo("https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png");
        verify(characterRepository).updateProfileImageAndStatus(
                101L,
                "https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png",
                ImageGenerationStatus.COMPLETED
        );
        verify(characterImageService).deleteReplacedGeneratedImage(
                "https://cdn.readwith.store/character-images/20/101/reference-v0/attempt-1.png",
                "https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png"
        );
    }

    @Test
    @DisplayName("reapplies a stored Batch output without submitting or charging for another Batch")
    void retryResultApplication_reusesStoredBatchOutput() {
        ProcessingJob job = ProcessingJob.builder()
                .id(630L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .runId("character-image-fanout-run-3")
                .artifactPath("character-images/20/fanout-jobs/run-3")
                .externalJobId("batch-630")
                .inputFileId("file-input")
                .outputFileId("file-output")
                .status(ProcessingJobStatus.FAILED)
                .currentStep("completed_with_failures")
                .failureCode("IMAGE_BATCH_PARTIAL_FAILURE")
                .build();
        CharacterImageAsset targetAsset = buildTargetAsset(730L, job);
        targetAsset.fail("BATCH_IMAGE_PUBLISH_FAILED");
        given(transactionManager.getTransaction(any())).willAnswer(invocation -> new SimpleTransactionStatus());
        given(processingJobRepository.findByIdForUpdate(630L)).willReturn(Optional.of(job));
        given(assetRepository.findByProcessingJobOrderByIdAsc(job)).willReturn(List.of(targetAsset));
        given(assetRepository.findById(730L)).willReturn(Optional.of(targetAsset));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<OpenAiImageBatchClient.OpenAiBatchImageResult> consumer = invocation.getArgument(1);
            consumer.accept(new OpenAiImageBatchClient.OpenAiBatchImageResult(
                    "character-image-asset-730", 200, "req-730", new byte[]{4, 5, 6}, null, null
            ));
            return null;
        }).when(batchClient).streamResults(eq("file-output"), any());
        given(characterImageService.buildPublishedS3KeyName(20L, 101L, 1, 1))
                .willReturn("character-images/20/101/reference-v1/attempt-1.png");
        given(characterImageService.uploadGeneratedImage(
                any(byte[].class),
                eq("character-images/20/101/reference-v1/attempt-1.png")
        )).willReturn("https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png");

        service.retryResultApplication(630L);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.READY);
        assertThat(targetAsset.getStatus()).isEqualTo(CharacterImageAssetStatus.PUBLISHED);
        verify(batchClient, never()).submit(any(), any(), any(), any());
        verify(batchClient).streamResults(eq("file-output"), any());
        verify(characterRepository).updateProfileImageAndStatus(
                101L,
                "https://cdn.readwith.store/character-images/20/101/reference-v1/attempt-1.png",
                ImageGenerationStatus.COMPLETED
        );
    }

    private CharacterImageAsset buildTargetAsset(Long assetId, ProcessingJob job) {
        return CharacterImageAsset.builder()
                .id(assetId)
                .book(book)
                .character(targetCharacter)
                .assetRole(CharacterImageAssetRole.CHARACTER_IMAGE)
                .generationMode(CharacterImageGenerationMode.REFERENCE_EDIT)
                .sourceReferenceAsset(referenceAsset)
                .processingJob(job)
                .referenceVersion(1)
                .status(CharacterImageAssetStatus.GENERATING)
                .s3Url("https://cdn.readwith.store/character-images/20/101/reference-v0/attempt-1.png")
                .model("gpt-image-2")
                .promptHash("prompt-hash")
                .build();
    }
}
