package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.BookImageReferenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "book_character_image_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BookCharacterImageProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_reference_asset_id")
    private CharacterImageAsset activeReferenceAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_character_id")
    private Character referenceCharacter;

    @Column(name = "reference_version", nullable = false)
    @Builder.Default
    private int referenceVersion = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_status", length = 30, nullable = false)
    @Builder.Default
    private BookImageReferenceStatus referenceStatus = BookImageReferenceStatus.NONE;

    @Column(length = 80)
    private String model;

    @Column(name = "base_style_prompt_hash", length = 64)
    private String baseStylePromptHash;

    @Column(name = "book_prompt_hash", length = 64)
    private String bookPromptHash;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    public void markCandidateGenerating(Character referenceCharacter, String model,
                                        String baseStylePromptHash, String bookPromptHash) {
        this.referenceCharacter = referenceCharacter;
        this.referenceStatus = BookImageReferenceStatus.CANDIDATE_GENERATING;
        this.model = model;
        this.baseStylePromptHash = baseStylePromptHash;
        this.bookPromptHash = bookPromptHash;
    }

    public void markQaPassed(CharacterImageAsset candidate) {
        this.activeReferenceAsset = candidate;
        this.referenceCharacter = candidate.getCharacter();
        this.referenceStatus = BookImageReferenceStatus.QA_PASSED;
    }

    public void markQaFailed(Character referenceCharacter) {
        this.referenceCharacter = referenceCharacter;
        this.referenceStatus = BookImageReferenceStatus.QA_FAILED;
    }

    public void approve(CharacterImageAsset candidate, String approvedBy) {
        this.activeReferenceAsset = candidate;
        this.referenceCharacter = candidate.getCharacter();
        this.referenceVersion += 1;
        this.referenceStatus = BookImageReferenceStatus.APPROVED;
        this.model = candidate.getModel();
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
    }

    public void reject() {
        this.referenceStatus = BookImageReferenceStatus.REJECTED;
    }
}
