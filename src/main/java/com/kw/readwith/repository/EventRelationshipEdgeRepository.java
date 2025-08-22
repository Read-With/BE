package com.kw.readwith.repository;

import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRelationshipEdgeRepository extends JpaRepository<EventRelationshipEdge, Long> {
    boolean existsByEvent(Event event);
}
