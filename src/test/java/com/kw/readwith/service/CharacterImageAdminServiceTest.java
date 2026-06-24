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
import com.kw.readwith.dto.admin.CharacterImageFanoutRequestDTO;
import com.kw.readwith.repository.BookCharacterImageProfileRepository;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterImageAssetRepository;
import com.kw.readwith.repository.CharacterRepository;
import com.kw.readwith.service.image.CharacterImageQaService;
import com.kw.readwith.service.image.OpenAiImageEditClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CharacterImageAdminServiceTest {

    @InjectMocks
    private CharacterImageAdminService characterImageAdminService;

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
    private CharacterImageQaService qaService;
    @Mock
    private CharacterImageProperties imageProperties;
    @Mock
    private RestTemplate restTemplate;

    @Test
    @DisplayName("fanout rejects invalid scope enum value")
    void fanout_rejectsInvalidScope() {
        Book book = Book.builder().id(1L).build();
        CharacterImageFanoutRequestDTO request = CharacterImageFanoutRequestDTO.builder()
                .scope("WRONG_SCOPE")
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> characterImageAdminService.fanout(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.INVALID_IMAGE_FANOUT_SCOPE);
    }

    @Test
    @DisplayName("fanout rejects invalid publishPolicy enum value")
    void fanout_rejectsInvalidPublishPolicy() {
        Book book = Book.builder().id(1L).build();
        CharacterImageFanoutRequestDTO request = CharacterImageFanoutRequestDTO.builder()
                .scope("MAIN_ONLY")
                .publishPolicy("WRONG_POLICY")
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> characterImageAdminService.fanout(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.INVALID_IMAGE_PUBLISH_POLICY);
    }

    @Test
    @DisplayName("fanout requires an approved reference image")
    void fanout_requiresApprovedReferenceImage() {
        Book book = Book.builder().id(1L).build();
        CharacterImageFanoutRequestDTO request = CharacterImageFanoutRequestDTO.builder()
                .scope("MAIN_ONLY")
                .publishPolicy("MANUAL")
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(profileRepository.findByBook(book)).willReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> characterImageAdminService.fanout(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
    }

    @Test
    @DisplayName("approveReferenceCandidate rejects assets that did not pass QA")
    void approveReferenceCandidate_rejectsNonPublishableStatus() {
        Book book = Book.builder().id(1L).build();
        Character character = Character.builder().id(10L).book(book).name("Alice").build();
        CharacterImageAsset asset = CharacterImageAsset.builder()
                .id(100L)
                .book(book)
                .character(character)
                .assetRole(CharacterImageAssetRole.REFERENCE_SEED)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .status(CharacterImageAssetStatus.QA_FAILED)
                .attemptNo(1)
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(assetRepository.findByIdAndBook(100L, book)).willReturn(Optional.of(asset));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> characterImageAdminService.approveReferenceCandidate(1L, 100L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_ASSET_INVALID_STATUS);
    }

    @Test
    @DisplayName("fanout rejects a profile that has not reached APPROVED")
    void fanout_rejectsUnapprovedProfile() {
        Book book = Book.builder().id(1L).build();
        Character character = Character.builder().id(10L).book(book).name("Alice").build();
        CharacterImageAsset reference = CharacterImageAsset.builder()
                .id(100L)
                .book(book)
                .character(character)
                .assetRole(CharacterImageAssetRole.REFERENCE_SEED)
                .generationMode(CharacterImageGenerationMode.TEXT_TO_IMAGE)
                .status(CharacterImageAssetStatus.QA_PASSED)
                .attemptNo(1)
                .build();
        BookCharacterImageProfile profile = BookCharacterImageProfile.builder()
                .book(book)
                .activeReferenceAsset(reference)
                .referenceCharacter(character)
                .referenceStatus(BookImageReferenceStatus.QA_PASSED)
                .build();
        CharacterImageFanoutRequestDTO request = CharacterImageFanoutRequestDTO.builder()
                .scope("MAIN_ONLY")
                .publishPolicy("MANUAL")
                .build();

        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(profileRepository.findByBook(book)).willReturn(Optional.of(profile));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> characterImageAdminService.fanout(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE_REFERENCE_NOT_APPROVED);
    }
}
