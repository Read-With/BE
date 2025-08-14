package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
// import org.hibernate.annotations.Where; // 삭제

import jakarta.persistence.Column;
// import jakarta.persistence.Entity; // 삭제
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;

// @Entity // 삭제
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// @Where(clause = "deleted_at IS NULL") // 삭제
public class BookCharacter extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long characterId;

    private boolean isMainCharacter;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String personalityText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Builder
    public BookCharacter(Long characterId, boolean isMainCharacter, String name, String personalityText, Book book) {
        this.characterId = characterId;
        this.isMainCharacter = isMainCharacter;
        this.name = name;
        this.personalityText = personalityText;
        this.book = book;
    }
}