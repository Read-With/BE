package com.kw.readwith.repository;

import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRelationshipEdgeRepository extends JpaRepository<EventRelationshipEdge, Long> {
    boolean existsByEvent(Event event);

    void deleteByEvent(Event event);
}