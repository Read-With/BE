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
import com.kw.readwith.dto.admin.AdminImageGenerationStatusResponseDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.repository.ProcessingJobRepository;
import com.kw.readwith.service.image.OpenAiImageEditClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminImageGenerationService")
class AdminImageGenerationServiceTest {

    @InjectMocks
    private AdminImageGenerationService adminImageGenerationService;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private CharacterImageAssetRepository assetRepository;
    @Mock
    private BookCharacterImageProfileRepository profileRepository;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private OpenAiImageEditClient imageEditClient;
    @Mock
    private CharacterImageProperties imageProperties;
    @Mock
    private CharacterImageFanoutJobService fanoutJobService;
    @Mock
    private ReferenceCandidateJobService referenceCandidateJobService;
    @Mock
    private ProcessingJobRepository processingJobRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private CdnUrlService cdnUrlService;

    private Book book;
    private Character mainCharacter;
    private Character sideCharacter;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .id(1L)
                .title("Pride and Prejudice")
                .author("Jane Austen")
                .language("en")
                .bookPrompt("Regency era portrait style")
                .build();
        mainCharacter = Character.builder()
                .id(10L)
                .book(book)
                .characterId(1L)
                .name("Elizabeth Bennet")
                .profileText("bright eyes, witty expression")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();
        sideCharacter = Character.builder()
                .id(11L)
                .book(book)
                .characterId(2L)
                .name("Mr. Darcy")
                .profileText("reserved expression, formal coat")
                .isMainCharacter(false)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();
        lenient().when(cdnUrlService.toPublicUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("representative candidate generation queues two book-scoped slots without blocking")
    void generateReferenceCandidates_queuesTwoSlots() {
        ProcessingJob referenceJob = ProcessingJob.builder()
                .id(500L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_REFERENCE_GENERATION)
                .runId("reference-candidates-run-1")
                .status(ProcessingJobStatus.QUEUED)
                .currentStep("queued")
                .build();
        BookCharacterImageProfile profile = BookCharacterImageProfile.builder()
                .book(book)
                .referenceCharacter(mainCharacter)
                .referenceStatus(BookImageReferenceStatus.CANDIDATE_GENERATING)
                .build();
        List<CharacterImageAsset> candidates = List.of(
                referenceCandidate(100L, 1, referenceJob),
                referenceCandidate(101L, 2, referenceJob)
        );

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(imageProperties.getReferenceCandidateCount()).willReturn(2);
        given(characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book))
                .willReturn(List.of(mainCharacter, sideCharacter));
        given(profileRepository.findByBook(book)).willReturn(Optional.of(profile));
        given(assetRepository.findByBookAndAssetRoleOrderBySlotNoAscCreatedAtAsc(
                book,
                CharacterImageAssetRole.REFERENCE_CANDIDATE
        )).willReturn(candidates);
        given(assetRepository.findByBookAndAssetRoleOrderByCreatedAtDesc(
                book,
                CharacterImageAssetRole.CHARACTER_IMAGE
        )).willReturn(List.of());
        given(processingJobRepository.findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
                1L,
                ProcessingPipelineType.IMAGE_REFERENCE_GENERATION
        )).willReturn(Optional.of(referenceJob));
        lenient().when(processingJobRepository.findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
                1L,
                ProcessingPipelineType.IMAGE_GENERATION
        )).thenReturn(Optional.empty());

        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.generateReferenceCandidates(1L);

        assertThat(response.getStatus()).isEqualTo("REFERENCE_GENERATING");
        assertThat(response.getNextAction()).isEqualTo("WAIT_REFERENCE_CANDIDATES");
        assertThat(response.getReferenceCandidateJob().getId()).isEqualTo(500L);
        assertThat(response.getReferenceCandidateJob().getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
        assertThat(response.getReferenceCharacter().getId()).isEqualTo(10L);
        assertThat(response.getReferenceCandidates()).hasSize(2);
        assertThat(response.getReferenceCandidates())
                .extracting(AdminImageGenerationStatusResponseDTO.ReferenceCandidate::getSlotNo)
                .containsExactly(1, 2);
        assertThat(response.getReferenceCandidates())
                .extracting(AdminImageGenerationStatusResponseDTO.ReferenceCandidate::getStatus)
                .containsOnly("GENERATING");
        verify(referenceCandidateJobService).queue(book, mainCharacter);
        verifyNoInteractions(characterImageService);
    }

    @Test
    @DisplayName("representative candidate selection rejects failed candidates")
    void selectReferenceCandidate_rejectsFailedCandidate() {
        CharacterImageAsset failedCandidate = CharacterImageAsset.builder()
                .id(100L)
                .book(book)
                .character(mainCharacter)
                .assetRole(CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .slotNo(1)
                .s3Url("https://cdn.test/failed.png")
                .status(CharacterImageAssetStatus.FAILED)
                .attemptNo(1)
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(assetRepository.findByIdAndBook(100L, book)).willReturn(Optional.of(failedCandidate));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> adminImageGenerationService.selectReferenceCandidate(1L, 100L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_ASSET_INVALID_STATUS);
    }

    @Test
    @DisplayName("single character regeneration requires a selected representative candidate")
    void regenerateCharacterImage_requiresSelectedReference() {
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(characterRepository.findByIdWithBook(11L)).willReturn(Optional.of(sideCharacter));
        given(profileRepository.findByBook(book)).willReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> adminImageGenerationService.regenerateCharacterImage(1L, 11L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
    }

    @Test
    @DisplayName("individual regeneration is blocked while a fan-out Batch is active")
    void regenerateCharacterImage_rejectsActiveFanoutJob() {
        ProcessingJob activeJob = ProcessingJob.builder()
                .id(500L)
                .book(book)
                .pipelineType(ProcessingPipelineType.IMAGE_GENERATION)
                .status(ProcessingJobStatus.PROCESSING)
                .build();
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(processingJobRepository.findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq(ProcessingPipelineType.IMAGE_GENERATION), any()
        )).willReturn(Optional.of(activeJob));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> adminImageGenerationService.regenerateCharacterImage(1L, 11L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_FANOUT_JOB_ACTIVE);
    }

    private CharacterImageAsset referenceCandidate(Long id, int slotNo, ProcessingJob job) {
        return CharacterImageAsset.builder()
                .id(id)
                .book(book)
                .character(mainCharacter)
                .assetRole(CharacterImageAssetRole.REFERENCE_CANDIDATE)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .processingJob(job)
                .slotNo(slotNo)
                .status(CharacterImageAssetStatus.GENERATING)
                .attemptNo(1)
                .build();
    }
}
