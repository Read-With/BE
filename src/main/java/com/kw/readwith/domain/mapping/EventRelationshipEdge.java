package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.SentimentLabel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EventRelationshipEdge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_char_id")
    private Character fromCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_char_id")
    private Character toCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "JSON")
    private String relationTags;

    private Integer interactionCount;

    private Float sentimentScore;

    @Enumerated(EnumType.STRING)
    private SentimentLabel sentimentLabel;

    private String edgeColorHex;

    @Column(name = "edge_weight")
    private Float edgeWeight;
}
