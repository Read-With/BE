package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/* ─────────────── EventCharacterStat ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"event_id","character_id"}))
public class EventCharacterStat extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Character character;

    @Column(name = "node_weight")
    private double nodeWeight;

    public void updateNodeWeight(double nodeWeight) {
        this.nodeWeight = nodeWeight;
    }
}