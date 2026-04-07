package com.kw.readwith.service;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CharacterImageService")
class CharacterImageServiceTest {

    @Mock
    private OpenAiImageModel imageModel;

    @Mock
    private AmazonS3Manager s3Manager;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterImageProperties imageProperties;

    @Mock
    private CharacterImageTransactionService transactionService;

    @InjectMocks
    private CharacterImageService characterImageService;

    private Book testBook;
    private Character testCharacter;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .id(1L)
                .title("Jekyll and Hyde")
                .author("Stevenson")
                .language("en")
                .bookPrompt("Victorian urban gothic, muted sepia and deep green palette, somber gaslit atmosphere, restrained realism, subtle psychological unease")
                .isDefault(false)
                .build();

        testCharacter = Character.builder()
                .id(10L)
                .book(testBook)
                .characterId(1L)
                .name("DR. JEKYLL")
                .profileText("middle-aged man, pale complexion, neatly combed dark hair touched with gray, refined narrow features, formal black suit, reserved and slightly weary expression")
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        when(imageProperties.getFallbackUrl()).thenReturn("https://cdn.readwith.store/character/default.png");
        when(imageProperties.getBaseStylePrompt()).thenReturn("Configured editorial gouache base style");
        when(imageProperties.getS3Path()).thenReturn("character-images");
        when(imageProperties.getBatchSize()).thenReturn(10);
        when(imageProperties.getDelayBetweenRequestsMs()).thenReturn(0L);
    }

    @Test
    @DisplayName("buildDallePrompt uses configured base style prompt and composes the remaining sections")
    void buildDallePrompt_composesStructuredPrompt() {
        String prompt = (String) ReflectionTestUtils.invokeMethod(characterImageService, "buildDallePrompt", testCharacter);

        assertThat(prompt).contains("Configured editorial gouache base style");
        assertThat(prompt).contains("exactly one character, single centered chest-up bust portrait");
        assertThat(prompt).contains("Victorian urban gothic, muted sepia and deep green palette");
        assertThat(prompt).contains("middle-aged man, pale complexion, neatly combed dark hair touched with gray");
        assertThat(prompt).contains("no split composition");
        assertThat(prompt.indexOf("Configured editorial gouache base style")).isLessThan(prompt.indexOf("Victorian urban gothic"));
        assertThat(prompt.indexOf("Victorian urban gothic")).isLessThan(prompt.indexOf("middle-aged man"));
    }

    @Test
    @DisplayName("buildDallePrompt falls back to default base style prompt when configuration is missing")
    void buildDallePrompt_fallsBackToDefaultBaseStylePrompt() {
        when(imageProperties.getBaseStylePrompt()).thenReturn(null);

        String prompt = (String) ReflectionTestUtils.invokeMethod(characterImageService, "buildDallePrompt", testCharacter);

        assertThat(prompt).contains("A single centered chest-up character portrait in a mature editorial gouache illustration style");
        assertThat(prompt).contains("flat matte gouache rendering");
    }

    @Test
    @DisplayName("buildDallePrompt removes multi-subject and scene-driving prompt segments")
    void buildDallePrompt_sanitizesRiskyPromptSegments() {
        Book riskyBook = Book.builder()
                .id(2L)
                .title("Risky Book")
                .author("Author")
                .language("en")
                .bookPrompt("Victorian urban gothic, scene with another character, muted sepia palette")
                .isDefault(false)
                .build();

        Character riskyCharacter = Character.builder()
                .id(11L)
                .book(riskyBook)
                .characterId(2L)
                .name("MR. HYDE")
                .profileText("middle-aged man, duo portrait, formal black suit, crowd behind him")
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        String prompt = (String) ReflectionTestUtils.invokeMethod(characterImageService, "buildDallePrompt", riskyCharacter);

        assertThat(prompt).contains("Victorian urban gothic");
        assertThat(prompt).contains("muted sepia palette");
        assertThat(prompt).contains("middle-aged man");
        assertThat(prompt).contains("formal black suit");
        assertThat(prompt).doesNotContain("scene with another character");
        assertThat(prompt).doesNotContain("duo portrait");
        assertThat(prompt).doesNotContain("crowd behind him");
    }

    @Test
    @DisplayName("generateAndSaveImage uploads generated image using current bookPrompt")
    void generateAndSaveImage_succeeds() throws Exception {
        Path tempImage = Files.createTempFile("character-image-test-", ".png");
        Files.write(tempImage, new byte[]{1, 2, 3, 4});

        when(characterRepository.findByIdWithBook(testCharacter.getId())).thenReturn(Optional.of(testCharacter));
        when(s3Manager.uploadFileFromBase64(anyString(), anyString(), eq("image/png")))
                .thenReturn("https://cdn.readwith.store/character-images/1/10.png");

        ImageResponse imageResponse = mock(ImageResponse.class);
        ImageGeneration generation = mock(ImageGeneration.class);
        Image image = mock(Image.class);
        when(imageModel.call(any())).thenReturn(imageResponse);
        when(imageResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(image);
        when(image.getUrl()).thenReturn(tempImage.toUri().toURL().toString());

        characterImageService.generateAndSaveImage(testCharacter.getId());

        verify(transactionService).updateStatus(testCharacter.getId(), ImageGenerationStatus.GENERATING);
        verify(s3Manager).uploadFileFromBase64(eq("character-images/1/10.png"), anyString(), eq("image/png"));
        verify(transactionService).updateImageAndStatus(
                testCharacter.getId(),
                "https://cdn.readwith.store/character-images/1/10.png",
                ImageGenerationStatus.COMPLETED
        );
    }

    @Test
    @DisplayName("generateImagesAsync stores fallback image when generation fails")
    void generateImagesAsync_storesFallbackImageOnFailure() {
        when(characterRepository.findByIdWithBook(testCharacter.getId())).thenReturn(Optional.of(testCharacter));
        when(imageModel.call(any())).thenThrow(new RuntimeException("OpenAI failure"));

        characterImageService.generateImagesAsync(List.of(testCharacter.getId()));

        verify(transactionService).updateStatus(testCharacter.getId(), ImageGenerationStatus.PENDING);
        verify(transactionService).updateStatus(testCharacter.getId(), ImageGenerationStatus.GENERATING);
        verify(transactionService).updateStatus(testCharacter.getId(), ImageGenerationStatus.FAILED);
        verify(transactionService).updateImageAndStatus(
                testCharacter.getId(),
                "https://cdn.readwith.store/character/default.png",
                ImageGenerationStatus.FAILED
        );
    }

    @Test
    @DisplayName("generateAndSaveImage throws when character does not exist")
    void generateAndSaveImage_throwsWhenCharacterMissing() {
        when(characterRepository.findByIdWithBook(anyLong())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> characterImageService.generateAndSaveImage(99L));

        assertThat(exception.getMessage()).contains("Failed to generate character image");
    }
}
