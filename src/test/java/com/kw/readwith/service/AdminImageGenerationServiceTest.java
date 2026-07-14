package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.BookCharacterImageProfile;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.CharacterImageAsset;
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
import com.kw.readwith.service.image.GeneratedCharacterImage;
import com.kw.readwith.service.image.OpenAiImageEditClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
    @DisplayName("representative candidate generation creates four book-scoped slots")
    void generateReferenceCandidates_createsFourSlots() throws Exception {
        BookCharacterImageProfile profile = BookCharacterImageProfile.builder()
                .book(book)
                .build();
        List<CharacterImageAsset> savedAssets = new ArrayList<>();
        AtomicLong idSequence = new AtomicLong(100L);

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(imageProperties.getModel()).willReturn("gpt-image-2");
        given(imageProperties.getBaseStylePrompt()).willReturn("editorial gouache portrait");
        given(characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(book))
                .willReturn(List.of(mainCharacter, sideCharacter));
        given(profileRepository.findByBook(book)).willReturn(Optional.empty(), Optional.of(profile));
        when(profileRepository.save(any(BookCharacterImageProfile.class))).thenReturn(profile);
        given(assetRepository.findByBookAndAssetRoleAndSlotNo(
                eq(book),
                eq(CharacterImageAssetRole.REFERENCE_CANDIDATE),
                any()
        )).willReturn(Optional.empty());
        when(assetRepository.save(any(CharacterImageAsset.class))).thenAnswer(invocation -> {
            CharacterImageAsset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", idSequence.getAndIncrement());
            savedAssets.add(asset);
            return asset;
        });
        given(assetRepository.findByBookAndAssetRoleOrderBySlotNoAscCreatedAtAsc(
                book,
                CharacterImageAssetRole.REFERENCE_CANDIDATE
        )).willAnswer(invocation -> savedAssets);
        given(assetRepository.findByBookAndAssetRoleOrderByCreatedAtDesc(
                book,
                CharacterImageAssetRole.CHARACTER_IMAGE
        )).willReturn(List.of());
        given(characterImageService.generateTextImage(mainCharacter))
                .willReturn(new GeneratedCharacterImage(new byte[]{1}, "gpt-image-2", "prompt", "hash", "req-1"));
        when(characterImageService.buildReferenceCandidateSlotS3KeyName(eq(mainCharacter), anyInt()))
                .thenAnswer(invocation -> "character-images/1/reference/slot-" + invocation.getArgument(1) + ".png");
        when(characterImageService.uploadGeneratedImage(eq(mainCharacter), any(byte[].class), anyString()))
                .thenAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(2));

        AdminImageGenerationStatusResponseDTO response = adminImageGenerationService.generateReferenceCandidates(1L);

        assertThat(response.getStatus()).isEqualTo("REFERENCE_READY");
        assertThat(response.getNextAction()).isEqualTo("SELECT_REFERENCE_CANDIDATE");
        assertThat(response.getReferenceCharacter().getId()).isEqualTo(10L);
        assertThat(response.getReferenceCandidates()).hasSize(4);
        assertThat(response.getReferenceCandidates())
                .extracting(AdminImageGenerationStatusResponseDTO.ReferenceCandidate::getSlotNo)
                .containsExactly(1, 2, 3, 4);
        assertThat(response.getReferenceCandidates())
                .extracting(AdminImageGenerationStatusResponseDTO.ReferenceCandidate::getStatus)
                .containsOnly("READY");
        verify(characterImageService).uploadGeneratedImage(
                eq(mainCharacter),
                any(byte[].class),
                eq("character-images/1/reference/slot-1.png")
        );
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
}
