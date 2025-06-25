package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* ─────────────── UserReadState ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","book_id"}))
public class UserReadState extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Book book;

    @Column(nullable = false)
    private Integer lastReadChapterIdx;

    @Column(nullable = false)
    private Integer lastReadEventIdx;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String cfi;                   // EPUB CFI

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String bookmarks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String highlights;
}