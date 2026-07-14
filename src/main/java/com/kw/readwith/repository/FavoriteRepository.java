package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.mapping.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    // 필요시 커스텀 쿼리 메소드 추가
    List<Favorite> findByUserId(Long userId);
    Favorite findByUserIdAndBookId(Long userId, Long bookId);

    @Modifying
    int deleteByBook(Book book);
}
