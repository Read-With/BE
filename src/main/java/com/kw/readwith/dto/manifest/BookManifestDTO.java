package com.kw.readwith.dto.manifest;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookManifestDTO {
    
    /**
     * 책 ID
     */
    private Long id;
    
    /**
     * 제목
     */
    private String title;
    
    /**
     * 저자
     */
    private String author;
    
    /**
     * 언어 (ISO-639 코드)
     */
    private String language;
    
    /**
     * 기본 제공 책 여부
     */
    private Boolean isDefault;
    
    /**
     * 요약 완료 여부
     */
    private Boolean summary;
    
    /**
     * 커버 이미지 URL
     */
    private String coverImgUrl;
    
    /**
     * 요약 파일 URL
     */
    private String summaryUrl;
    
    /**
     * EPUB 파일 경로
     */
    private String epubPath;
}
