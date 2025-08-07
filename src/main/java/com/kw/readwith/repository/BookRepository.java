package com.kw.readwith.repository;

import com.kw.readwith.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByInfoUploadedTrue();
} 