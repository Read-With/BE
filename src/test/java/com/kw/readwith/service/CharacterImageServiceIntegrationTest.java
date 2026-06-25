package com.kw.readwith.service;

import com.kw.readwith.config.CharacterImageProperties;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.enums.ImageGenerationStatus;
import com.kw.readwith.repository.BookRepository;
import com.kw.readwith.repository.CharacterRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CharacterImageService 완전 통합 테스트
 * 
 * ⚠️ 주의: 이 테스트는 실제로 다음을 수행합니다:
 * 1. OpenAI DALL-E 3 API 호출 (비용 발생: 약 $0.04/이미지)
 * 2. 실제 S3에 이미지 업로드
 * 3. 실제 DB에 데이터 저장
 * 
 * 실행 전 확인 사항:
 * - OPENAI_API_KEY 환경변수 설정
 * - AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY 환경변수 설정
 * - S3 버킷 'readwith-s3-bucket' 존재 확인
 * - 테스트 DB 사용 (application-test.yml)
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("캐릭터 이미지 생성 완전 통합 테스트 (실제 API 호출)")
@EnabledIfEnvironmentVariable(named = "RUN_REAL_IMAGE_INTEGRATION_TESTS", matches = "true")
class CharacterImageServiceIntegrationTest {

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CharacterImageProperties imageProperties;

    private static Book testBook;
    private static Character testCharacter;

    @BeforeAll
    static void setUpClass() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🚀 실제 통합 테스트 시작");
        System.out.println("⚠️  OpenAI API 호출 및 S3 업로드가 실제로 발생합니다!");
        System.out.println("💰 예상 비용: 약 $0.04 (DALL-E 3 API)");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    @BeforeEach
    void setUp() {
        // 테스트용 Book 생성 및 저장
        testBook = Book.builder()
                .title("통합테스트책")
                .author("테스트작가")
                .language("ko")
                .isDefault(false)
                .build();
        testBook = bookRepository.save(testBook);

        // 테스트용 Character 생성 (실제 프롬프트 사용)
        testCharacter = Character.builder()
                .book(testBook)
                .characterId(999L)
                .name("통합테스트캐릭터")
                .profileText("A wise elderly wizard with a long white beard, wearing star-patterned robes and a pointed hat")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();
        testCharacter = characterRepository.save(testCharacter);

        System.out.println("\n📌 테스트 준비 완료");
        System.out.println("   - Book ID: " + testBook.getId());
        System.out.println("   - Character ID: " + testCharacter.getId());
        System.out.println("   - Profile Text: " + testCharacter.getProfileText());
    }

    @Test
    @Order(1)
    @DisplayName("1. 실제 DALL-E 3 이미지 생성 및 S3 업로드 테스트")
    void testRealImageGeneration() throws InterruptedException {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🎨 TEST 1: 실제 이미지 생성 시작");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // When: 실제 이미지 생성 실행
        long startTime = System.currentTimeMillis();
        characterImageService.generateAndSaveImage(testCharacter.getId());
        long endTime = System.currentTimeMillis();

        // Then: DB에서 업데이트된 캐릭터 정보 조회
        Character updatedCharacter = characterRepository.findById(testCharacter.getId())
                .orElseThrow(() -> new AssertionError("캐릭터를 찾을 수 없습니다"));

        // 검증
        assertNotNull(updatedCharacter.getProfileImage(), "프로필 이미지 URL이 null이면 안됩니다");
        assertEquals(ImageGenerationStatus.COMPLETED, updatedCharacter.getImageGenerationStatus(),
                "상태가 COMPLETED여야 합니다");
        assertTrue(updatedCharacter.getProfileImage().contains("readwith-s3-bucket"),
                "S3 URL이어야 합니다");
        assertTrue(updatedCharacter.getProfileImage().contains("character-images"),
                "character-images 경로를 포함해야 합니다");

        // 결과 출력
        System.out.println("\n✅ 이미지 생성 성공!");
        System.out.println("⏱️  소요 시간: " + (endTime - startTime) / 1000.0 + "초");
        System.out.println("🖼️  생성된 이미지 URL:");
        System.out.println("   " + updatedCharacter.getProfileImage());
        System.out.println("📊 최종 상태: " + updatedCharacter.getImageGenerationStatus());
        System.out.println("\n🌐 S3 콘솔에서 확인:");
        System.out.println("   https://s3.console.aws.amazon.com/s3/buckets/readwith-s3-bucket?prefix=character-images/");
    }

    @Test
    @Order(2)
    @DisplayName("2. 여러 캐릭터 동시 생성 테스트 (비동기)")
    void testMultipleCharactersGeneration() throws InterruptedException {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🎨 TEST 2: 여러 캐릭터 동시 생성 (2명)");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Given: 두 명의 추가 캐릭터 생성
        Character character1 = Character.builder()
                .book(testBook)
                .characterId(1001L)
                .name("해리포터")
                .profileText("Young boy with messy black hair, round glasses, and a lightning bolt scar on forehead")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        Character character2 = Character.builder()
                .book(testBook)
                .characterId(1002L)
                .name("헤르미온느")
                .profileText("Clever girl with bushy brown hair and bright brown eyes")
                .isMainCharacter(true)
                .imageGenerationStatus(ImageGenerationStatus.PENDING)
                .build();

        character1 = characterRepository.save(character1);
        character2 = characterRepository.save(character2);

        List<Long> characters = Arrays.asList(character1.getId(), character2.getId());

        System.out.println("📌 생성할 캐릭터:");
        System.out.println("   1. " + character1.getName() + " (ID: " + character1.getId() + ")");
        System.out.println("   2. " + character2.getName() + " (ID: " + character2.getId() + ")");

        // When: 비동기로 이미지 생성
        long startTime = System.currentTimeMillis();
        characterImageService.generateImagesAsync(characters);

        // 비동기 작업 완료 대기 (최대 2분)
        System.out.println("\n⏳ 비동기 작업 완료 대기 중... (최대 120초)");
        int maxWaitSeconds = 120;
        int waitedSeconds = 0;
        boolean allCompleted = false;

        while (waitedSeconds < maxWaitSeconds && !allCompleted) {
            Thread.sleep(5000); // 5초마다 체크
            waitedSeconds += 5;

            Character char1Updated = characterRepository.findById(character1.getId()).orElseThrow();
            Character char2Updated = characterRepository.findById(character2.getId()).orElseThrow();

            allCompleted = (char1Updated.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED ||
                           char1Updated.getImageGenerationStatus() == ImageGenerationStatus.FAILED) &&
                          (char2Updated.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED ||
                           char2Updated.getImageGenerationStatus() == ImageGenerationStatus.FAILED);

            System.out.println("   [" + waitedSeconds + "초] " +
                    character1.getName() + ": " + char1Updated.getImageGenerationStatus() + ", " +
                    character2.getName() + ": " + char2Updated.getImageGenerationStatus());
        }

        long endTime = System.currentTimeMillis();

        // Then: 검증
        Character char1Final = characterRepository.findById(character1.getId()).orElseThrow();
        Character char2Final = characterRepository.findById(character2.getId()).orElseThrow();

        System.out.println("\n✅ 비동기 작업 완료!");
        System.out.println("⏱️  총 소요 시간: " + (endTime - startTime) / 1000.0 + "초");
        System.out.println("\n📊 최종 결과:");
        System.out.println("   1. " + char1Final.getName());
        System.out.println("      상태: " + char1Final.getImageGenerationStatus());
        System.out.println("      URL: " + (char1Final.getProfileImage() != null ? 
                char1Final.getProfileImage().substring(0, Math.min(80, char1Final.getProfileImage().length())) + "..." : "null"));
        
        System.out.println("   2. " + char2Final.getName());
        System.out.println("      상태: " + char2Final.getImageGenerationStatus());
        System.out.println("      URL: " + (char2Final.getProfileImage() != null ? 
                char2Final.getProfileImage().substring(0, Math.min(80, char2Final.getProfileImage().length())) + "..." : "null"));

        // 적어도 하나는 성공해야 함
        assertTrue(
            char1Final.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED ||
            char2Final.getImageGenerationStatus() == ImageGenerationStatus.COMPLETED,
            "적어도 하나의 캐릭터는 이미지 생성에 성공해야 합니다"
        );
    }

    @Test
    @Order(3)
    @DisplayName("3. 이미 생성된 캐릭터는 스킵 확인")
    void testSkipAlreadyGeneratedCharacter() {
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🎨 TEST 3: 이미 생성된 캐릭터 스킵 테스트");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Given: 이미 완료된 캐릭터 생성
        Character completedCharacter = Character.builder()
                .book(testBook)
                .characterId(2000L)
                .name("이미생성된캐릭터")
                .profileImage("https://readwith-s3-bucket.s3.ap-northeast-2.amazonaws.com/existing.png")
                .imageGenerationStatus(ImageGenerationStatus.COMPLETED)
                .build();
        completedCharacter = characterRepository.save(completedCharacter);

        System.out.println("📌 테스트 캐릭터:");
        System.out.println("   - 이름: " + completedCharacter.getName());
        System.out.println("   - 상태: " + completedCharacter.getImageGenerationStatus());
        System.out.println("   - 기존 URL: " + completedCharacter.getProfileImage());

        String originalUrl = completedCharacter.getProfileImage();

        // When: 이미지 생성 시도
        characterImageService.generateImagesAsync(Arrays.asList(completedCharacter.getId()));

        // Then: URL이 변경되지 않아야 함
        Character afterCharacter = characterRepository.findById(completedCharacter.getId()).orElseThrow();
        
        assertEquals(originalUrl, afterCharacter.getProfileImage(), "이미 완료된 캐릭터의 URL은 변경되지 않아야 합니다");
        assertEquals(ImageGenerationStatus.COMPLETED, afterCharacter.getImageGenerationStatus());

        System.out.println("\n✅ 스킵 확인 완료!");
        System.out.println("   기존 URL과 동일함: " + originalUrl.equals(afterCharacter.getProfileImage()));
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // 테스트 데이터 정리
        if (testCharacter != null && testCharacter.getId() != null) {
            characterRepository.deleteById(testCharacter.getId());
        }
        if (testBook != null && testBook.getId() != null) {
            // 연관된 모든 캐릭터 삭제
            characterRepository.deleteAll(characterRepository.findByBookOrderByIsMainCharacterDescNameAsc(testBook));
            bookRepository.deleteById(testBook.getId());
        }
    }

    @AfterAll
    static void tearDownClass() {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("✅ 모든 통합 테스트 완료!");
        System.out.println("📊 S3 버킷 확인: https://s3.console.aws.amazon.com/s3/buckets/readwith-s3-bucket");
        System.out.println("═══════════════════════════════════════════════════════════\n");
    }
}

