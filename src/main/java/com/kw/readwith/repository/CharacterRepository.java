package com.kw.readwith.repository;

import com.kw.readwith.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {
}