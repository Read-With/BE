package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.SentimentLabel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* ─────────────── EventRelationshipEdge ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(indexes = {
        @Index(columnList = "event_id, from_char_id, to_char_id", unique = true)
})
public class EventRelationshipEdge extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_char_id", nullable = false)
    private Character fromCharacter;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "to_char_id", nullable = false)
    private Character toCharacter;

    private int interactionCount;

    @Enumerated(EnumType.STRING) @Column(length = 3)
    private SentimentLabel sentimentLabel;

    private float sentimentScore;

    @JdbcTypeCode(SqlTypes.JSON)             // Hibernate 6
    @Column(columnDefinition = "json")
    private String relationTags;

    @Lob @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 7)
    private String edgeColorHex;

    private float edgeWidth;
}