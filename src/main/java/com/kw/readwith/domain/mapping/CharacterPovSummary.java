package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/* ─────────────── CharacterPovSummary ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id","character_id"}))
public class CharacterPovSummary extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Character character;

    @Lob @Column(columnDefinition = "TEXT")
    private String summaryText;
}
