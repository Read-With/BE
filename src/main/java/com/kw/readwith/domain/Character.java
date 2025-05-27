package com.kw.readwith.domain;

import com.kw.readwith.domain.cache.ChapterCharacterStat;
import com.kw.readwith.domain.cache.EventCharacterStat;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/* ─────────────── Character ────────────────────────── */
@Entity
@Table(name = "book_character",
        indexes = @Index(columnList = "book_id, name", unique = true))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class Character extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Book book;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(nullable = false)
    private boolean isMainCharacter;

    private int firstChapterIdx;

    @Lob @Column(columnDefinition = "TEXT")
    private String personalityText;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] embeddingVector;

    /* 관계 */
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<EventCharacterStat> eventStats = new ArrayList<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private List<ChapterCharacterStat> chapterStats = new ArrayList<>();
}