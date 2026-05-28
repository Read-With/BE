package com.kw.readwith.repository;

import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRelationshipEdgeRepository extends JpaRepository<EventRelationshipEdge, Long> {
    @Query("SELECT CASE WHEN COUNT(edge) > 0 THEN true ELSE false END FROM EventRelationshipEdge edge WHERE edge.event.book = :book")
    boolean existsByBook(@Param("book") Book book);

    boolean existsByEvent(Event event);

    /**
     * 특정 이벤트의 모든 관계 엣지 조회 (세밀 그래프용)
     */
    List<EventRelationshipEdge> findByEvent(Event event);

    int deleteByEvent(Event event);

    boolean existsByEventAndFromCharacterAndToCharacter(Event event, Character fromCharacter, Character toCharacter);

    /**
     * 여러 이벤트의 관계 엣지 조회 (거시 그래프용)
     */
    List<EventRelationshipEdge> findByEventIn(List<Event> events);

    @Query("SELECT edge FROM EventRelationshipEdge edge " +
           "JOIN edge.event event JOIN event.chapter chapter " +
           "WHERE event IN :events " +
           "ORDER BY chapter.idx ASC, event.idx ASC, edge.id ASC")
    List<EventRelationshipEdge> findByEventsOrdered(@Param("events") List<Event> events);

}
