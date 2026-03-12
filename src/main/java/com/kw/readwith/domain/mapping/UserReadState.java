package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.User;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
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

    @Column(name = "last_locator_json", columnDefinition = "TEXT", nullable = false)
    private String lastLocatorJson;
}
