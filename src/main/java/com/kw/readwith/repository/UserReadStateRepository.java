package com.kw.readwith.repository;

import java.util.List;
import java.util.Optional;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.mapping.UserReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReadStateRepository extends JpaRepository<UserReadState, Long> {

    Optional<UserReadState> findByUserAndBook(User user, Book book);
    
    // 사용자 ID와 책 ID로 조회
    Optional<UserReadState> findByUserIdAndBookId(Long userId, Long bookId);
    
    // 사용자의 모든 읽기 상태 조회
    List<UserReadState> findByUserId(Long userId);
    
    // 사용자와 책으로 읽기 상태 존재 여부 확인
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
    
    // 사용자의 특정 책 읽기 상태 삭제
    void deleteByUserIdAndBookId(Long userId, Long bookId);
}