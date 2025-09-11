package com.kw.readwith.repository;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventCharacterWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventCharacterWeightRepository extends JpaRepository<EventCharacterWeight, Long> {

    /**
     * 특정 이벤트의 모든 캐릭터 중요도 조회
     */
    List<EventCharacterWeight> findByEvent(Event event);

    /**
     * 특정 이벤트와 캐릭터의 중요도 조회
     */
    Optional<EventCharacterWeight> findByEventAndCharacter(Event event, Character character);

    /**
     * 특정 이벤트에 중요도 데이터가 존재하는지 확인
     */
    boolean existsByEvent(Event event);

    /**
     * 특정 캐릭터의 모든 이벤트별 중요도 조회
     */
    List<EventCharacterWeight> findByCharacter(Character character);

    /**
     * 특정 이벤트의 중요도 데이터 삭제
     */
    void deleteByEvent(Event event);
}
