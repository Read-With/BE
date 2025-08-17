package com.kw.readwith.domain;

import com.kw.readwith.domain.cache.ChapterCharacterStat;
import com.kw.readwith.domain.cache.EventCharacterStat;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "book_character")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Character extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 전체 테이블에서의 고유 ID (PK)

    // 책 내에서의 인물 ID를 위한 필드 추가
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(length = 120, nullable = false)
    private String name;

    // 인물이 불리는 다른 이름들
    @Column
    private String names;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(nullable = false)
    private boolean isMainCharacter;

    private int firstChapterIdx;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String personalityText;

    // 프로필 묘사 필드 추가
    @Lob
    @Column(columnDefinition = "TEXT")
    private String profileText;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] embeddingVector;

    /* 관계 */
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<EventCharacterStat> eventStats = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<ChapterCharacterStat> chapterStats = new ArrayList<>();
}