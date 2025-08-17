package com.kw.readwith.repository;

import com.kw.readwith.domain.mapping.CharacterPovSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // 추가

@Repository
public interface CharacterPovSummaryRepository extends JpaRepository<CharacterPovSummary, Long> {

    List<CharacterPovSummary> findByChapterId(Long chapterId);
}