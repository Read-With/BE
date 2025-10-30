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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CharacterImageService 단위 테스트
 * OpenAI API와 S3 업로드는 모킹하여 실제 비용 발생 없이 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("캐릭터 이미지 생성 서비스 테스트")
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

    @Spy
    private RestTemplate restTemplate;

    @InjectMocks
    private CharacterImageService characterImageService;

    private Book testBook;
    private Character testCharacter1;
    private Character testCharacter2;

    @BeforeEach
    void setUp() {
        // 테스트용 Book 생성
        testBook = Book.builder()
                .id(1L)
                .title("테스트 책")
                .author("테스트 작가")
                .language("ko")
                .isDefault(true)
                .build();

        // 테스트용 Character 생성
        testCharacter1 = Character.builder()
                .id(1L)
                .book(testBook)
                .characterId(1L)
                .name("테스트 캐릭터 1")
                .profileText("검은 머리에 번개 모양 흉터가 있는 소년")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        testCharacter2 = Character.builder()
                .id(2L)
                .book(testBook)
                .characterId(2L)
                .name("테스트 캐릭터 2")
                .profileText("곱슬한 갈색 머리의 똑똑한 소녀")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        // Properties 설정
        when(imageProperties.getCommonStyle()).thenReturn(
                "Bust portrait, square composition, face centered. Illustrative realism style."
        );
        when(imageProperties.getS3Path()).thenReturn("character-images");
        when(imageProperties.getFallbackUrl()).thenReturn(
                "https://readwith-s3-bucket.s3.ap-northeast-2.amazonaws.com/character-images/default-character.jpg"
        );
        when(imageProperties.getBatchSize()).thenReturn(10);
        when(imageProperties.getDelayBetweenRequestsMs()).thenReturn(100L); // 테스트에서는 짧게

        // TransactionService 모킹 (모든 업데이트 메서드)
        doNothing().when(transactionService).updateStatus(anyLong(), any());
        doNothing().when(transactionService).updateImageAndStatus(anyLong(), anyString(), any());

        // RestTemplate을 Service에 주입 (리플렉션 사용)
        ReflectionTestUtils.setField(characterImageService, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("이미지 생성 성공 - DALL-E 호출 및 S3 업로드")
    void testGenerateAndSaveImage_Success() throws Exception {
        // Given: DALL-E API 응답 모킹
        String mockDalleImageUrl = "https://oaidalleapiprodscus.blob.core.windows.net/test-image.png";
        
        // Spring AI ImageResponse 구조에 맞게 모킹
        ImageResponse mockImageResponse = mock(ImageResponse.class);
        org.springframework.ai.image.ImageGeneration mockGeneration = mock(org.springframework.ai.image.ImageGeneration.class);
        org.springframework.ai.image.Image mockImage = mock(org.springframework.ai.image.Image.class);

        when(imageModel.call(any())).thenReturn(mockImageResponse);
        when(mockImageResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        when(mockImage.getUrl()).thenReturn(mockDalleImageUrl);

        // RestTemplate 이미지 다운로드 모킹
        byte[] mockImageData = new byte[]{1, 2, 3, 4, 5}; // 가짜 이미지 데이터
        doReturn(mockImageData).when(restTemplate).getForObject(eq(mockDalleImageUrl), eq(byte[].class));

        // S3 업로드 모킹
        String mockS3Url = "https://readwith-s3-bucket.s3.ap-northeast-2.amazonaws.com/character-images/1/1.png";
        when(s3Manager.uploadFileFromBase64(anyString(), anyString(), eq("image/png")))
                .thenReturn(mockS3Url);

        // When: 이미지 생성 실행
        characterImageService.generateAndSaveImage(testCharacter1);

        // Then: 검증
        // 1. DALL-E API 호출 확인
        verify(imageModel, times(1)).call(any());

        // 2. 이미지 다운로드 확인
        verify(restTemplate, times(1)).getForObject(eq(mockDalleImageUrl), eq(byte[].class));

        // 3. S3 업로드 확인
        verify(s3Manager, times(1)).uploadFileFromBase64(
                eq("character-images/1/1.png"),
                anyString(),
                eq("image/png")
        );

        // 4. 상태 업데이트 확인 (GENERATING)
        verify(characterRepository, times(1))
                .updateImageGenerationStatus(eq(1L), eq(ImageGenerationStatus.GENERATING));

        // 5. 이미지 URL 및 상태 업데이트 확인 (COMPLETED)
        verify(characterRepository, times(1))
                .updateProfileImageAndStatus(eq(1L), eq(mockS3Url), eq(ImageGenerationStatus.COMPLETED));

        System.out.println("✅ 이미지 생성 성공 테스트 통과");
    }

    @Test
    @DisplayName("이미지 생성 실패 - 폴백 이미지 설정")
    void testGenerateAndSaveImage_Failure_UsesFallback() {
        // Given: DALL-E API 호출 시 예외 발생
        when(imageModel.call(any())).thenThrow(new RuntimeException("API 호출 실패"));

        // Repository 모킹
        doNothing().when(characterRepository).updateImageGenerationStatus(anyLong(), any());
        doNothing().when(characterRepository).updateProfileImageAndStatus(anyLong(), anyString(), any());

        // When & Then: 예외가 발생하고 폴백 처리됨
        try {
            characterImageService.generateAndSaveImage(testCharacter1);
        } catch (Exception e) {
            // 예외는 발생하지만 폴백은 처리되어야 함
        }

        // Then: GENERATING 상태로 변경 확인
        verify(characterRepository, times(1))
                .updateImageGenerationStatus(eq(1L), eq(ImageGenerationStatus.GENERATING));

        // FAILED 상태로 변경 확인
        verify(characterRepository, times(1))
                .updateImageGenerationStatus(eq(1L), eq(ImageGenerationStatus.FAILED));

        System.out.println("✅ 이미지 생성 실패 및 폴백 처리 테스트 통과");
    }

    @Test
    @DisplayName("여러 캐릭터 비동기 이미지 생성 - 이미 완료된 캐릭터는 스킵")
    void testGenerateImagesAsync_SkipsCompletedCharacters() {
        // Given: 캐릭터 1은 이미 완료됨
        Character completedCharacter = Character.builder()
                .id(1L)
                .book(testBook)
                .characterId(1L)
                .name("완료된 캐릭터")
                .profileImage("https://s3.amazonaws.com/existing-image.png")
                .imageGenerationStatus(ImageGenerationStatus.COMPLETED)
                .build();

        // 캐릭터 2는 아직 생성 안됨
        List<Character> characters = Arrays.asList(completedCharacter, testCharacter2);

        // DALL-E API 모킹
        ImageResponse mockImageResponse = mock(ImageResponse.class);
        org.springframework.ai.image.ImageGeneration mockGeneration = mock(org.springframework.ai.image.ImageGeneration.class);
        org.springframework.ai.image.Image mockImage = mock(org.springframework.ai.image.Image.class);

        when(imageModel.call(any())).thenReturn(mockImageResponse);
        when(mockImageResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockImage);
        when(mockImage.getUrl()).thenReturn("https://dalle-test-url.png");

        // RestTemplate 모킹
        byte[] mockImageData = new byte[]{1, 2, 3};
        doReturn(mockImageData).when(restTemplate).getForObject(anyString(), eq(byte[].class));

        // S3 모킹
        when(s3Manager.uploadFileFromBase64(anyString(), anyString(), anyString()))
                .thenReturn("https://s3-test-url.png");

        // Repository 모킹
        doNothing().when(characterRepository).updateImageGenerationStatus(anyLong(), any());
        doNothing().when(characterRepository).updateProfileImageAndStatus(anyLong(), anyString(), any());

        // When: 비동기 이미지 생성 실행
        characterImageService.generateImagesAsync(characters);

        // Then: 완료된 캐릭터는 스킵되고, 캐릭터 2만 처리됨
        // DALL-E는 캐릭터 2에 대해서만 호출됨
        verify(imageModel, times(1)).call(any());

        System.out.println("✅ 완료된 캐릭터 스킵 테스트 통과");
    }

    @Test
    @DisplayName("프롬프트 생성 - profileText와 공통 스타일 결합")
    void testBuildDallePrompt() {
        // Given: 프롬프트 빌드를 위한 캐릭터
        Character character = Character.builder()
                .id(1L)
                .book(testBook)
                .name("테스트 캐릭터")
                .profileText("검은 머리와 번개 모양 흉터가 있는 소년")
                .build();

        // 리플렉션을 통해 private 메서드 테스트
        String prompt = (String) ReflectionTestUtils.invokeMethod(
                characterImageService,
                "buildDallePrompt",
                character
        );

        // Then: 프롬프트에 profileText와 공통 스타일이 포함되어야 함
        assert prompt != null;
        assert prompt.contains("검은 머리와 번개 모양 흉터가 있는 소년");
        assert prompt.contains("Bust portrait");

        System.out.println("✅ 프롬프트 생성 테스트 통과");
        System.out.println("생성된 프롬프트: " + prompt);
    }

    @Test
    @DisplayName("S3 키 이름 생성 - 올바른 경로 포맷")
    void testBuildS3KeyName() {
        // Given: S3 키 이름 생성
        String keyName = (String) ReflectionTestUtils.invokeMethod(
                characterImageService,
                "buildS3KeyName",
                testCharacter1
        );

        // Then: 올바른 포맷인지 확인
        String expectedKeyName = "character-images/1/1.png";
        assert keyName != null;
        assert keyName.equals(expectedKeyName);

        System.out.println("✅ S3 키 이름 생성 테스트 통과");
        System.out.println("생성된 키 이름: " + keyName);
    }

    @Test
    @DisplayName("폴백 이미지 저장 - FAILED 상태로 변경")
    void testSaveFallbackImage() {
        // Given: Repository 모킹
        doNothing().when(characterRepository).updateProfileImageAndStatus(anyLong(), anyString(), any());

        // When: 폴백 이미지 저장
        ReflectionTestUtils.invokeMethod(
                characterImageService,
                "saveFallbackImage",
                testCharacter1
        );

        // Then: 폴백 URL과 FAILED 상태로 업데이트되어야 함
        verify(characterRepository, times(1))
                .updateProfileImageAndStatus(
                        eq(1L),
                        eq("https://readwith-s3-bucket.s3.ap-northeast-2.amazonaws.com/character-images/default-character.jpg"),
                        eq(ImageGenerationStatus.FAILED)
                );

        System.out.println("✅ 폴백 이미지 저장 테스트 통과");
    }
}

