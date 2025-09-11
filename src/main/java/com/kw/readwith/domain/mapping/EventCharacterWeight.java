package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 이벤트별 캐릭터 중요도 (weight) 정보
 * 각 이벤트에서 특정 캐릭터가 얼마나 중요한 역할을 하는지 저장
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
    name = "event_character_weight",
    indexes = {
        @Index(columnList = "event_id, character_id", unique = true)
    }
)
public class EventCharacterWeight extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    /**
     * 캐릭터의 이벤트 내 중요도 (노드 크기)
     */
    @Column(nullable = false)
    private Float weight;

    /**
     * 참고용 카운트 (AI에서 제공하는 추가 정보)
     */
    @Column(nullable = false)
    private Integer count;
}
