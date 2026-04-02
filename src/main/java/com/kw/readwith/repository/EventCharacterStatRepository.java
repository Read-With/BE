package com.kw.readwith.repository;

import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.mapping.EventCharacterStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventCharacterStatRepository extends JpaRepository<EventCharacterStat, Long> {

    @Query("SELECT CASE WHEN COUNT(stat) > 0 THEN true ELSE false END FROM EventCharacterStat stat WHERE stat.event.book = :book")
    boolean existsByBook(@Param("book") Book book);

    Optional<EventCharacterStat> findByEventAndCharacter(Event event, Character character);

    boolean existsByEvent(Event event);

    int deleteByEvent(Event event);
}
