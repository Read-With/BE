package com.kw.readwith.domain.cache;

import com.kw.readwith.domain.Chapter;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/* ─────────────── ChapterCharacterStat ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id","character_id"}))
public class ChapterCharacterStat extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Character character;

    private int mentionCount;
}