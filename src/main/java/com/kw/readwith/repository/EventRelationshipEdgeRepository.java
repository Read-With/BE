package com.kw.readwith.repository;

import com.kw.readwith.domain.Event;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

public interface EventRelationshipEdgeRepository extends JpaRepository<EventRelationshipEdge, Long> {
    boolean existsByEvent(Event event);
    
    /**
     * 특정 이벤트의 모든 관계 엣지 조회 (세밀 그래프용)
     */
    List<EventRelationshipEdge> findByEvent(Event event);


    void deleteByEvent(Event event);
    

}

