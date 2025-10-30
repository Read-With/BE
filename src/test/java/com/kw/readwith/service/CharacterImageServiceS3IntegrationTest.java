package com.kw.readwith.service;

import com.kw.readwith.aws.s3.AmazonS3Manager;
import com.kw.readwith.config.CharacterImageProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

/**
 * S3 실제 업로드 통합 테스트
 * 
 * 주의: 이 테스트는 실제 AWS S3에 파일을 업로드합니다.
 * @Disabled 어노테이션을 제거하고 실행하세요.
 * 
 * 실행 전 확인 사항:
 * 1. AWS 자격증명 설정 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. S3 버킷 존재 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("실제 S3 업로드가 발생하므로 수동으로 활성화해야 합니다")
@DisplayName("S3 실제 업로드 통합 테스트")
class CharacterImageServiceS3IntegrationTest {

    @Autowired
    private AmazonS3Manager s3Manager;

    @Autowired
    private CharacterImageProperties imageProperties;

    @Test
    @DisplayName("테스트 이미지를 실제로 S3에 업로드")
    void testRealS3Upload() {
        // Given: 1x1 픽셀 PNG 이미지 (Base64)
        // 이것은 매우 작은 테스트 이미지입니다
        String base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        
        String testKeyName = imageProperties.getS3Path() + "/test/integration-test.png";
        
        // When: S3에 업로드
        String s3Url = s3Manager.uploadFileFromBase64(testKeyName, base64Image, "image/png");
        
        // Then: S3 URL이 반환되어야 함
        System.out.println("✅ S3 업로드 성공!");
        System.out.println("업로드된 URL: " + s3Url);
        System.out.println("S3 콘솔에서 확인하세요: https://s3.console.aws.amazon.com/s3/buckets/readwith-s3-bucket");
        
        assert s3Url != null;
        assert s3Url.contains("readwith-s3-bucket");
        assert s3Url.contains("character-images/test/integration-test.png");
    }

    @Test
    @DisplayName("실제 크기의 이미지 업로드 테스트")
    void testRealSizeImageUpload() {
        // Given: 더 큰 테스트 이미지 (1KB 정도)
        byte[] testImageData = new byte[1024]; // 1KB 테스트 데이터
        for (int i = 0; i < testImageData.length; i++) {
            testImageData[i] = (byte) (i % 256);
        }
        
        String base64Image = Base64.getEncoder().encodeToString(testImageData);
        String testKeyName = imageProperties.getS3Path() + "/test/large-integration-test.png";
        
        // When: S3에 업로드
        String s3Url = s3Manager.uploadFileFromBase64(testKeyName, base64Image, "image/png");
        
        // Then
        System.out.println("✅ 큰 이미지 S3 업로드 성공!");
        System.out.println("업로드된 URL: " + s3Url);
        System.out.println("파일 크기: " + testImageData.length + " bytes");
        
        assert s3Url != null;
    }

    @Test
    @DisplayName("캐릭터 이미지 경로 형식 확인")
    void testCharacterImagePath() {
        // Given: 책 ID 1, 캐릭터 ID 1
        Long bookId = 1L;
        Long characterId = 1L;
        
        // When: 경로 생성
        String expectedPath = String.format("%s/%d/%d.png", 
            imageProperties.getS3Path(),
            bookId,
            characterId
        );
        
        // Then
        System.out.println("예상 S3 경로: " + expectedPath);
        assert expectedPath.equals("character-images/1/1.png");
    }
}

