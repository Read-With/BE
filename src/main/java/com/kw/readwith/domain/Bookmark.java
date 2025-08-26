package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(indexes = {
    @Index(columnList = "user_id, book_id"),
    @Index(columnList = "book_id, created_at")
})
public class Bookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "start_cfi", nullable = false, length = 500)
    private String startCfi; // EPUB CFI 시작 위치

    @Column(name = "end_cfi", length = 500)
    private String endCfi; // EPUB CFI 끝 위치 (단일 위치 북마크의 경우 null 가능)

    @Column(name = "color", length = 7)
    private String color; // HEX 색상 코드 (예: #ffd700)

    @Column(name = "memo", length = 1000)
    private String memo; // 사용자 메모

    /**
     * 비즈니스 로직
     */
    public void updateBookmark(String color, String memo) {
        if (color != null) {
            this.color = color;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    /**
     * 범위 선택 북마크인지 확인
     */
    public boolean isRangeBookmark() {
        return endCfi != null && !endCfi.trim().isEmpty();
    }

    /**
     * 단일 위치 북마크인지 확인
     */
    public boolean isPointBookmark() {
        return !isRangeBookmark();
    }
}
