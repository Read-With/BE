package com.kw.readwith.repository;

import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventCharacterStatRepository extends JpaRepository<EventCharacterStat, Long> {

    Optional<EventCharacterStat> findByEventAndCharacter(Event event, Character character);

    boolean existsByEvent(Event event);

    int deleteByEvent(Event event);
}
